"""Policy/evidence-reader unit tests; these do not compile or execute Swift."""
import json
import tempfile
import unittest
from pathlib import Path

from scan_swift import scan, sha


class SwiftScanTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / 'project'
        self.root.mkdir()
        self.generated = Path(self.temp.name) / 'spm'
        self.bridge = Path(self.temp.name) / 'bridge'
        self.write(self.root / 'build.gradle.kts', 'allWarningsAsErrors.set(true)')
        self.write(self.root / 'swift-test-harness/Package.swift', self.manifest())
        self.write(self.root / 'swift-test-harness/Tests/Consumer/Behavior.swift', 'func actualBehavior() { XCTAssertEqual(add(1, 2), 3) }')
        self.write(self.generated / 'Package.swift', self.manifest())
        self.write(self.generated / 'Sources/Runtime/Runtime.swift', 'public struct Runtime {}')
        self.write(self.bridge / 'files/Main/Main.kt', 'fun bridge() = 3')

    def write(self, path, text):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        return path

    def manifest(self):
        return '// swift-tools-version: 6.0\nimport PackageDescription\nlet package = Package(name: "Demo", targets: [.target(name: "Runtime")], swiftLanguageModes: [.v6])\n'

    def result(self, evidence=None):
        return scan(self.root, self.generated, self.bridge, evidence, ['ConsumerTests.actualBehavior'])

    def evidence(self):
        # Synthetic records exercise the verifier, never count as Swift runtime proof.
        current = self.result()
        log = self.write(Path(self.temp.name) / 'frontend.log', '\n'.join(
            '/tool/swift-frontend -module-name ' + name + ' -swift-version 6 -warnings-as-errors source.swift'
            for name in ['Runtime', 'Consumer']))
        xml = self.write(Path(self.temp.name) / 'consumer.xml', '<testsuite><testcase classname="ConsumerTests" name="actualBehavior"/></testsuite>')
        tool = self.write(Path(self.temp.name) / 'compiler.bin', 'fixture-tool-bytes')
        generation = self.write(Path(self.temp.name) / 'generation.log', 'synthetic unit fixture, not execution')
        record = lambda p: dict(path=str(p), sha256=sha(p), run_id='unit-fixture', exit_code=0)
        data = dict(schema_version=1, source_sha256=current['source_sha256'], generated_sha256=current['generated_sha256'],
                    toolchain_files=[dict(path=str(tool), sha256=sha(tool))], consumer_module='Consumer',
                    frontend_logs=[record(log)], test_results_xml=[record(xml)],
                    generation_receipt=dict(run_id='unit-fixture', exit_code=0, argv=['./gradlew', 'embedSwiftExportForXcode'],
                        source_sha256=current['source_sha256'], generated_sha256=current['generated_sha256'],
                        log_path=str(generation), log_sha256=sha(generation)))
        p = self.write(Path(self.temp.name) / 'evidence.json', json.dumps(data))
        return p, data

    def test_comment_warning_override_ignored_real_override_rejected(self):
        p = self.root / 'build.gradle.kts'
        p.write_text('// allWarningsAsErrors.set(false)\nallWarningsAsErrors.set(true)')
        self.assertNotIn('SWIFT001', [v['rule'] for v in self.result()['findings']])
        p.write_text('allWarningsAsErrors = false')
        self.assertIn('SWIFT001', [v['rule'] for v in self.result()['findings']])

    def test_hidden_yaml_yml_and_manifest_language_overrides(self):
        for name in ['swift.yml', 'swift.yaml']:
            self.write(self.root / '.github/workflows' / name, 'env:\n  SWIFT_VERSION: 5.0\n')
        self.write(self.generated / 'Package.swift', self.manifest().replace('.v6', '.v5'))
        found = [v for v in self.result()['findings'] if v['rule'] == 'SWIFT002']
        self.assertEqual(len(found), 3)

    def test_known_runtime_shapes_are_findings_not_runtime_acceptance(self):
        self.write(self.generated / 'Sources/Runtime/Runtime.swift', '@_implementationOnly import Bridge\npublic struct KotlinError: Error { public var wrapped: KotlinRuntime.KotlinBase }')
        r = self.result()
        self.assertTrue({'SWIFT004', 'SWIFT005'}.issubset({v['rule'] for v in r['findings']}))
        self.assertEqual(r['acceptance']['status'], 'unverified')

    def test_complete_scoped_record_and_stale_generated_refusal(self):
        evidence, data = self.evidence()
        self.assertEqual(self.result(evidence)['acceptance']['status'], 'evidence-consistent-scoped-consumer')
        self.write(self.bridge / 'files/Main/Main.kt', 'fun bridge() = 4')
        self.assertEqual(self.result(evidence)['acceptance']['status'], 'unverified')

    def test_missing_dependency_frontend_or_failed_run_refused(self):
        evidence, data = self.evidence()
        p = Path(data['frontend_logs'][0]['path'])
        p.write_text('/tool/swift-frontend -module-name Consumer -swift-version 6 -warnings-as-errors source.swift')
        data['frontend_logs'][0]['sha256'] = sha(p)
        evidence.write_text(json.dumps(data))
        self.assertEqual(self.result(evidence)['acceptance']['status'], 'unverified')
        data['generation_receipt']['exit_code'] = 1
        evidence.write_text(json.dumps(data))
        self.assertIn('Fresh exact generation receipt missing or failed', self.result(evidence)['acceptance']['errors'])

    def test_placeholder_assertion_and_skipped_runtime_refused(self):
        self.write(self.root / 'swift-test-harness/Tests/Consumer/Behavior.swift', '\n\n#expect(Bool(true))\nXCTAssertTrue(true)')
        self.assertEqual([v['line'] for v in self.result()['findings'] if v['rule'] == 'SWIFT008'], [3, 4])
        evidence, data = self.evidence()
        p = Path(data['test_results_xml'][0]['path'])
        p.write_text('<testsuite><testcase classname="ConsumerTests" name="actualBehavior"><skipped/></testcase></testsuite>')
        data['test_results_xml'][0]['sha256'] = sha(p)
        evidence.write_text(json.dumps(data))
        self.assertEqual(self.result(evidence)['acceptance']['status'], 'unverified')


if __name__ == '__main__':
    unittest.main()
