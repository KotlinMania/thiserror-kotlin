"""Bounded unit tests; fixtures are data directories, never Git checkouts or builds."""
import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

import sync_android_publication as sync
from check_android_classpath import duplicates, inspect_configurations

FRAGMENT = (sync.SOURCE / 'verification/android-publication.gradle.kts.fragment').read_text()
BUILD = '''import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
plugins { alias(libs.plugins.android.kmp) }
val comment = "commonMain.dependencies { implementation(libs.syn.kotlin) }"
/* nested /* commonMain.dependencies { } */ comment */
kotlin {
    android { namespace = "keep.me" }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.syn.kotlin) // reviewed public API
            api(libs.quote.kotlin) // keep imported public edge
            implementation(libs.private.kotlin)
        }
        androidMain.dependencies { implementation(libs.android.only) }
        androidNativeMain.dependencies { implementation(libs.libc.kotlin) }
    }
}
publishing { repositories { maven { name = "centralPortalStaging" } } }
val kotlinVersion = "2.4.20"
val centralPortalBundle = tasks.register("centralPortalBundle") { }
val publishToCentralPortal = tasks.register("publishToCentralPortal") { }
'''


class SyncTests(unittest.TestCase):
    def test_preserves_api_private_android_overrides_and_strings(self):
        after = sync.transform(BUILD, ['libs.syn.kotlin', 'libs.quote.kotlin'], FRAGMENT)
        self.assertIn('api(libs.syn.kotlin) // reviewed', after)
        self.assertIn('api(libs.quote.kotlin) // keep', after)
        self.assertIn('implementation(libs.private.kotlin)', after)
        self.assertIn('androidNativeMain.dependencies { implementation(libs.libc.kotlin) }', after)
        self.assertIn('androidMain.dependencies { implementation(libs.android.only) }', after)
        self.assertIn('"commonMain.dependencies { implementation(libs.syn.kotlin) }"', after)
        self.assertIn('namespace = "keep.me"', after)

    def test_catalog_bundle_public_edge_keeps_private_bundle(self):
        text = BUILD.replace('implementation(libs.syn.kotlin)', 'implementation(commonMainDependencyBundle)')
        result = sync.transform(text, ['libs.syn.kotlin'], FRAGMENT, {'libs.syn.kotlin'})
        self.assertIn('implementation(commonMainDependencyBundle)', result)
        self.assertIn('api(libs.syn.kotlin)', result)
        self.assertEqual(result, sync.transform(result, ['libs.syn.kotlin'], FRAGMENT, {'libs.syn.kotlin'}))

    def test_idempotent(self):
        first = sync.transform(BUILD, ['libs.syn.kotlin'], FRAGMENT)
        self.assertEqual(first, sync.transform(first, ['libs.syn.kotlin'], FRAGMENT))

    def test_unsupported_dsl_and_unknown_alias_rejected(self):
        for text, alias in [(BUILD, 'libs.absent'), (BUILD.replace('commonMain.dependencies', 'getByName("commonMain").dependencies'), 'libs.syn.kotlin')]:
            with self.assertRaises(ValueError):
                sync.transform(text, [alias], FRAGMENT)

    def test_conditional_dependency_override_rejected(self):
        text = BUILD.replace('implementation(libs.syn.kotlin) // reviewed public API',
                             'if (enabled) { implementation(libs.syn.kotlin) }')
        with self.assertRaisesRegex(ValueError, 'override requires manual review'):
            sync.transform(text, ['libs.syn.kotlin'], FRAGMENT)

    def test_existing_publication_override_rejected(self):
        with self.assertRaisesRegex(ValueError, 'preserve and review'):
            sync.transform(BUILD + '\nval verifyAndroidPublication = 1\n', [], FRAGMENT)
        modified = sync.transform(BUILD, [], FRAGMENT).replace('Android KMP plugin owns', 'CUSTOM owns')
        with self.assertRaisesRegex(ValueError, 'override differs'):
            sync.transform(modified, [], FRAGMENT)

    def test_existing_pin_verifier_and_unsupported_release_layout_are_preserved(self):
        with self.assertRaisesRegex(ValueError, 'preserve and review'):
            sync.transform(BUILD + '\nval verifyPublicationPins = 1\n', [], FRAGMENT)
        with self.assertRaisesRegex(ValueError, 'Unsupported release bundle'):
            sync.transform(BUILD.replace('val centralPortalBundle', 'val customReleaseBundle'), [], FRAGMENT)

    def test_missing_android_does_not_gain_target(self):
        with self.assertRaises(ValueError):
            sync.transform(BUILD.replace('android { namespace = "keep.me" }', ''), [], FRAGMENT)

    def test_stale_head_hash_and_missing_grant_refused(self):
        saved = {'repo': str(ARGS.scratch), 'head': 'a', 'branch': 'feature', 'module': '.',
                 'api_aliases': [], 'changes': {}, 'inputs': {'catalog': 'hash'}}
        grant = {'repo': saved['repo'], 'head': 'a', 'owner': 'test-owner', 'source_write_granted': True}
        for changed in [dict(saved, head='b'), dict(saved, inputs={'catalog': 'new'})]:
            with patch.object(sync, 'plan', return_value=changed):
                with self.assertRaisesRegex(ValueError, 'Stale'):
                    sync.apply(saved, grant)
        with self.assertRaisesRegex(ValueError, 'grant required'):
            sync.apply(saved, {})

    def test_dirty_refusal_without_mutation(self):
        saved = {'repo': str(ARGS.scratch), 'head': 'a', 'branch': 'feature', 'module': '.', 'api_aliases': [], 'changes': {}}
        grant = {'repo': saved['repo'], 'head': 'a', 'owner': 'test-owner', 'source_write_granted': True}
        with patch.object(sync, 'plan', return_value=saved), patch.object(sync, 'git', return_value=' M build.gradle.kts'):
            with self.assertRaisesRegex(ValueError, 'Dirty'):
                sync.apply(saved, grant)

    def test_hidden_yaml_and_yml_inventory_no_writes(self):
        with tempfile.TemporaryDirectory(dir=ARGS.scratch) as tmp:
            root = Path(tmp)
            gh = root / '.github/workflows'
            gh.mkdir(parents=True)
            for name in ['android.yml', 'publish.yaml']:
                (gh / name).write_text('on: {workflow_call: {}}\n')
            before = {p: p.read_bytes() for p in gh.iterdir()}
            self.assertEqual(len(sync.inventory(root)), 2)
            self.assertEqual(before, {p: p.read_bytes() for p in gh.iterdir()})

    def test_configuration_versions_are_separate_but_real_duplicates_fail(self):
        with tempfile.TemporaryDirectory(dir=ARGS.scratch) as tmp:
            root = Path(tmp)
            paths = []
            for version in ['13', '23']:
                path = root / f'annotations-{version}.jar'
                with zipfile.ZipFile(path, 'w') as jar:
                    jar.writestr('org/jetbrains/annotations/NotNull.class', bytes.fromhex('cafebabe'))
                paths.append(path)
            self.assertEqual(inspect_configurations({'compile': [paths[0]], 'runtime': [paths[1]]}),
                             {'compile': {}, 'runtime': {}})
            actual = inspect_configurations({'compile': [paths[0]], 'runtime': paths})
            self.assertEqual(actual['compile'], {})
            self.assertIn('org/jetbrains/annotations/NotNull.class', actual['runtime'])

    def test_raw_aar_and_nested_jar_duplicate_bytecode(self):
        with tempfile.TemporaryDirectory(dir=ARGS.scratch) as tmp:
            root = Path(tmp)
            def packed(entries):
                buf = io.BytesIO()
                with zipfile.ZipFile(buf, 'w') as z:
                    for name, data in entries.items():
                        z.writestr(name, data)
                return buf.getvalue()
            name = 'org/jetbrains/annotations/ApiStatus.class'
            bytecode = bytes.fromhex('cafebabe')
            jar = packed({name: bytecode})
            (root / 'annotations.jar').write_bytes(jar)
            (root / 'bad.aar').write_bytes(packed({'classes.jar': jar, 'libs/embedded.jar': jar}))
            found = duplicates([root / 'annotations.jar', root / 'bad.aar'])
            self.assertEqual(len(found[name]), 3)
            (root / 'good.aar').write_bytes(packed({'classes.jar': packed({'io/github/kotlinmania/procmacro/ApiStatus.class': bytecode})}))
            self.assertEqual(duplicates([root / 'annotations.jar', root / 'good.aar']), {})


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('--scratch', required=True, type=Path)
    ARGS = parser.parse_args()
    ARGS.scratch.mkdir(parents=True, exist_ok=True)
    unittest.main(argv=[__file__], verbosity=2)
