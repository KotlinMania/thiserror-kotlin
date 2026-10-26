"""Source policy regressions, distinct from native compiler/runtime acceptance."""
import unittest
from pathlib import Path

SOURCE = (Path(__file__).resolve().parent.parent / 'build.gradle.kts').read_text()


def helper(source):
    return source.split('        fun ensureKonanDependency(', 1)[1].split('        targets.withType<KotlinNativeTarget>', 1)[0]


def safe_offline_download(source):
    body = helper(source)
    download = body.index('commandLine("curl"')
    return all(
        text in body[:download]
        for text in ['gradle.startParameter.isOffline', 'throw GradleException(', 'offline mode forbids downloading']
    )


def receipt_checked_reuse(source):
    body = helper(source).split('val targetDir', 1)[0]
    return all(
        text in body
        for text in [
            'compilerReceipt.isFile', 'name in compilerReceipt.readLines()',
            'compilerDirectory.isDirectory', 'compilerDirectory.list()?.isNotEmpty() == true',
            'return compilerDirectory',
        ]
    )


class NativeDependencyCacheTest(unittest.TestCase):
    def test_offline_rejection_precedes_network(self):
        self.assertTrue(safe_offline_download(SOURCE))
        self.assertFalse(safe_offline_download(SOURCE.replace('gradle.startParameter.isOffline', 'false', 1)))

    def test_shared_reuse_requires_real_receipt_and_nonempty_directory(self):
        self.assertTrue(receipt_checked_reuse(SOURCE))
        for condition in ['compilerReceipt.isFile', 'name in compilerReceipt.readLines()', 'compilerDirectory.list()?.isNotEmpty() == true']:
            self.assertFalse(receipt_checked_reuse(SOURCE.replace(condition, 'true', 1)))

    def test_extraction_uses_private_output_and_actual_returned_directories(self):
        self.assertIn('.dir("native-wrapper-dependencies")', SOURCE)
        self.assertNotIn('val konanDeps = File(System.getProperty("user.home"), ".konan/dependencies")', SOURCE)
        self.assertNotIn('File(konanDeps, "$gccDirName/', SOURCE)
        self.assertNotIn('File(konanDeps, "target-toolchain-2-', SOURCE)
        self.assertNotIn('File(konanDeps, "msys2-', SOURCE)
        for declaration in ['val ndkDir = ensureKonanDependency', 'val ndkDirectory = ensureKonanDependency', 'val gccDirectory = ensureKonanDependency', 'val mingwSysroot = ensureKonanDependency']:
            self.assertIn(declaration, SOURCE)
        self.assertIn('check(targetDir.isDirectory && targetDir.list()?.isNotEmpty() == true)', helper(SOURCE))
        self.assertNotIn('compilerReceipt.write', helper(SOURCE))

    def test_official_konan_data_directory_provider_is_retained(self):
        self.assertIn('.environmentVariable("KONAN_DATA_DIR")', SOURCE)
        self.assertIn('require(it.isNotBlank())', SOURCE)
        self.assertIn('.orElse(providers.systemProperty("user.home").map { File(it, ".konan") })', SOURCE)


if __name__ == '__main__':
    unittest.main(verbosity=2)
