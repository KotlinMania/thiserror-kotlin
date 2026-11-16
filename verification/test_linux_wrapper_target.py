"""Template source regressions; no compiler/link/runtime acceptance."""
import re
import unittest
from pathlib import Path

SOURCE = (Path(__file__).resolve().parent.parent / 'build.gradle.kts').read_text()


def linux_commands(source):
    branch = source.split('konanTargetName.startsWith("linux") -> {', 1)[1].split('konanTargetName.startsWith("mingw") -> {', 1)[0]
    return re.findall(r'commandLine\("clang",([^\n]+)', branch)


def target_commands_are_explicit(source):
    commands = linux_commands(source)
    return len(commands) == 2 and all(
        all(flag in command for flag in ['"--target=$targetTriple"', '"--sysroot=${sysroot.absolutePath}"', '"-fPIC"', '"-Werror"'])
        for command in commands
    )


class LinuxWrapperTargetTest(unittest.TestCase):
    def test_both_host_paths_use_requested_target_sysroot_and_pic(self):
        self.assertTrue(target_commands_are_explicit(SOURCE))

    def test_old_host_only_command_is_rejected(self):
        changed = SOURCE.replace('"--target=$targetTriple", "--sysroot=${sysroot.absolutePath}", ', '', 1)
        self.assertFalse(target_commands_are_explicit(changed))

    def test_linux_guard_precedes_object_cleanup(self):
        guard = SOURCE.index('file("verification/verify_native_archive.py").absolutePath')
        cleanup = SOURCE.index('tempObj.delete()', guard)
        self.assertLess(guard, cleanup)
        block = SOURCE[guard:cleanup]
        for argument in ['outFile.absolutePath', '"--target"', 'konanTargetName', '"--object"', 'tempObj.absolutePath', 'assertNormalExitValue()']:
            self.assertIn(argument, block)
        self.assertIn('inputs.file("verification/verify_native_archive.py")', SOURCE)
        self.assertIn('outputs.file(File(outDir, "native-archive-verification.json"))', SOURCE)

    def test_existing_cinterop_dependency_is_preserved(self):
        self.assertIn('dependsOn(wrapperTask)', SOURCE)
        self.assertIn('inputs.files(wrapperTask)', SOURCE)


if __name__ == '__main__':
    unittest.main(verbosity=2)
