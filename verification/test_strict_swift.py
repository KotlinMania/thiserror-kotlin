"""Source-policy regressions; these tests do not compile or execute Swift/Kotlin."""
import argparse
import unittest
from pathlib import Path

from enforce_swift6 import enforce
from sync_android_publication import transform, validate_warning_policy, SOURCE
from test_sync_android_publication import BUILD, FRAGMENT


class StrictSwiftTests(unittest.TestCase):
    def test_sync_refuses_generated_bridge_warning_relaxation(self):
        bad = BUILD + '''
tasks.withType<KotlinCompilationTask<*>>().configureEach {
    if (name.startsWith("compileSwiftExport")) { compilerOptions.allWarningsAsErrors.set(false) }
}
'''
        with self.assertRaisesRegex(ValueError, 'Warnings-as-errors relaxation'):
            transform(bad, [], FRAGMENT)
        with self.assertRaisesRegex(ValueError, 'Warnings-as-errors relaxation'):
            transform(BUILD, [], FRAGMENT + '\ncompilerOptions.allWarningsAsErrors = false\n')
        validate_warning_policy(BUILD + '\n// allWarningsAsErrors.set(false) is forbidden\n')

    def test_generated_dependency_package_is_swift6_and_idempotent(self):
        text = """// swift-tools-version: 5.9
import PackageDescription
let package = Package(
    name: "Sample",
    targets: [
        .target(name: "KotlinRuntimeSupport"),
        .target(name: "KotlinCoroutineSupport", dependencies: ["KotlinRuntimeSupport"]),
        .target(name: "ExportedKotlinPackages", dependencies: ["KotlinRuntimeSupport"])
    ]
)
"""
        strict = enforce(text)
        self.assertTrue(strict.startswith('// swift-tools-version: 6.0\n'))
        self.assertIn('swiftLanguageModes: [.v6]', strict)
        self.assertIn('platforms: [.macOS("15.0")]', strict)
        self.assertEqual(strict.count('.target('), text.count('.target('))
        for dependency_module in ['KotlinRuntimeSupport', 'KotlinCoroutineSupport', 'ExportedKotlinPackages']:
            self.assertIn(f'name: "{dependency_module}"', strict)
        self.assertEqual(enforce(strict), strict)

    def test_manifest_preserves_platform_c_standard_and_target_settings(self):
        source = '''// swift-tools-version: 5.9
import PackageDescription
let package = Package(
    name: "Demo",
    platforms: [.iOS(.v17), .macOS(.v15)],
    targets: [.target(name: "Dependency"), .testTarget(name: "Tests", dependencies: ["Dependency"])],
    cLanguageStandard: .c11
)
'''
        strict = enforce(source)
        self.assertIn('platforms: [.iOS(.v17), .macOS(.v15)]', strict)
        self.assertIn('cLanguageStandard: .c11', strict)
        self.assertLess(strict.index('swiftLanguageModes:'), strict.index('cLanguageStandard:'))
        self.assertEqual(enforce(strict), strict)

    def test_weaker_target_and_legacy_package_modes_refused(self):
        for setting in ['swiftLanguageVersions: [.v5]', 'swiftLanguageModes: [.v5]',
                        'targets: [.target(name: "Dependency", swiftSettings: [.swiftLanguageMode(.v5)])]']:
            source = f'// swift-tools-version: 6.0\nimport PackageDescription\nlet package = Package(name: "Demo", {setting})\n'
            with self.assertRaises(ValueError):
                enforce(source)

    def test_newer_swift6_tools_requirement_is_preserved(self):
        source = '// swift-tools-version: 6.2\nimport PackageDescription\nlet package = Package(name: "Demo", targets: [])\n'
        self.assertTrue(enforce(source).startswith('// swift-tools-version: 6.2\n'))

    def test_template_harness_and_dependency_build_have_explicit_strict_mode(self):
        source = (SOURCE / 'build.gradle.kts').read_text()
        validate_warning_policy(source)
        self.assertIn('it.name.endsWith("generateSPMPackage", ignoreCase = true)', source)
        self.assertIn('it.name.endsWith("buildSPMPackage", ignoreCase = true)', source)
        self.assertIn('"XCODE_XCCONFIG_FILE" to strictSwiftConfiguration.asFile.absolutePath', source)
        self.assertIn('"-Xswiftc", "-swift-version", "-Xswiftc", "6", "-Xswiftc", "-warnings-as-errors"', source)
        self.assertNotIn('text.replace("swift-tools-version: 6.0", "swift-tools-version: 5.9")', source)
        self.assertNotIn('spmDir.walkTopDown()', source)
        self.assertIn('swiftLanguageModes: [.v6]', (SOURCE / 'swift-test-harness/Package.swift').read_text())
        config = (SOURCE / 'verification/strict-swift.xcconfig').read_text()
        for setting in ['SWIFT_VERSION = 6.0', 'SWIFT_TREAT_WARNINGS_AS_ERRORS = YES', 'GCC_TREAT_WARNINGS_AS_ERRORS = YES']:
            self.assertIn(setting, config)


if __name__ == '__main__':
    unittest.main(verbosity=2)
