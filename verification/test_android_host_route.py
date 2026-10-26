"""Source regression tests only; no Gradle graph/compiler/runtime acceptance."""
import unittest
from pathlib import Path

from sync_android_publication import transform
from test_sync_android_publication import BUILD

ROOT = Path(__file__).resolve().parent.parent
FRAGMENT = (ROOT / 'verification/android-publication.gradle.kts.fragment').read_text()


def branches():
    local, external = FRAGMENT.split('    if (externalRoot == null) {', 1)[1].split('    } else {', 1)
    return local, external.split('val verifyAndroidPublication =', 1)[0]


class AndroidHostRouteTests(unittest.TestCase):
    def test_embedded_fragment_is_identical(self):
        build = (ROOT / 'build.gradle.kts').read_text()
        self.assertEqual(build.count(FRAGMENT.strip()), 1)

    def test_release_guard_does_not_expand_android_task_graph(self):
        guard = FRAGMENT.split('val verifyPublicationPins =', 1)[1]
        self.assertIn('tasks.named("centralPortalBundle") { dependsOn(verifyPublicationPins) }', guard)
        android = FRAGMENT.split('val verifyAndroidPublication =', 1)[1].split('val verifyPublicationPins =', 1)[0]
        self.assertNotIn('verifyPublicationPins', android)
        for declaration in ['commonMainApi', 'commonMainImplementation', 'versionConstraint.requiredVersion',
                            'project.publication.android.pomDependencyModules', 'inputs.property("sourceHead", sourceHead)', '!pinned.startsWith("latest.")']:
            self.assertIn(declaration, guard)

    def test_external_root_never_requests_root_publication(self):
        local, external = branches()
        preamble = FRAGMENT.split('    if (externalRoot == null) {')[0]
        root_task = 'publishKotlinMultiplatformPublicationToCentralPortalStagingRepository'
        self.assertNotIn(root_task, preamble + external)
        self.assertEqual(local.count(root_task), 1)
        self.assertIn('publishAndroidPublicationToCentralPortalStagingRepository', preamble)
        self.assertNotIn('tasks.withType<PublishToMavenRepository>().matching', FRAGMENT)

    def test_local_root_route_preserves_android_only_projects(self):
        local, _ = branches()
        guard = local.index('check(!hasAppleTargets ||')
        root_edge = local.index('dependsOn(tasks.named<PublishToMavenRepository>')
        self.assertLess(guard, root_edge)
        self.assertIn('it.konanTarget.family.isAppleFamily', local)
        self.assertIn('providers.systemProperty("os.name").get().startsWith("Mac")', local)

    def test_external_root_requires_exact_source_and_real_metadata(self):
        _, external = branches()
        for assertion in ['inputs.file(sourceReceipt)', 'inputs.property("expectedRootHead", expectedHead)',
                          'head.matches(Regex("[0-9a-f]{40}"))',
                          'sourceReceipt.isFile && sourceReceipt.readText().trim() == head',
                          '${root.artifactId}-${root.version}.module', 'rootDirectory.isDirectory']:
            self.assertIn(assertion, external)
        self.assertIn('include("$coordinatePath/${root.version}/**")', external)
        self.assertNotIn('writeText', external)

    def test_workflows_transport_same_head_root_from_mac_to_android(self):
        mac = (ROOT / '.github/workflows/macos.yml').read_text()
        android = (ROOT / '.github/workflows/android.yml').read_text()
        ci = (ROOT / '.github/workflows/ci.yml').read_text()
        self.assertIn('publishKotlinMultiplatformPublicationToCentralPortalStagingRepository', mac)
        self.assertIn('git rev-parse HEAD > build/staging-deploy.source-head', mac)
        self.assertIn('build/staging-deploy.source-head', mac)
        self.assertIn('  android:\n    needs: macos\n', ci)
        for workflow in [mac, android]:
            self.assertIn('kmp-root-publication-${{ github.sha }}', workflow)
        self.assertIn('rootStagingDirectory=build/root-publication/staging-deploy', android)
        self.assertIn('expectedRootHead=${{ github.sha }}', android)
        for broad_task in ['publishKotlinMultiplatformPublication', './gradlew build', './gradlew test', './gradlew check']:
            self.assertNotIn(broad_task, android)

    def test_focused_sync_keeps_route_and_project_specialization(self):
        synced = transform(BUILD, ['libs.syn.kotlin'], FRAGMENT)
        self.assertIn(FRAGMENT.strip(), synced)
        self.assertIn('androidNativeMain.dependencies { implementation(libs.libc.kotlin) }', synced)
        self.assertEqual(synced, transform(synced, ['libs.syn.kotlin'], FRAGMENT))
        changed = synced.replace('sourceReceipt.readText().trim() == head', 'true')
        with self.assertRaisesRegex(ValueError, 'override differs'):
            transform(changed, ['libs.syn.kotlin'], FRAGMENT)


if __name__ == '__main__':
    unittest.main(verbosity=2)
