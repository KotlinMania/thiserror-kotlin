#!/usr/bin/env python3
"""Regression tests using an existing compiled Linux x64 wrapper archive."""
import argparse
import unittest
from pathlib import Path

from verify_native_archive import archive_members, elf_identity, verify

FIXTURE = None


class NativeArchiveTest(unittest.TestCase):
    def test_actual_x64_archive_matches_requested_target(self):
        report = verify(FIXTURE, "linux_x64")
        self.assertTrue(report["members"])
        self.assertTrue(all(member["machine"] == 62 for member in report["members"]))

    def test_actual_x64_archive_is_rejected_for_arm64(self):
        with self.assertRaisesRegex(ValueError, "expected relocatable ELF machine 183, got type 1 machine 62"):
            verify(FIXTURE, "linux_arm64")

    def test_actual_object_cannot_be_renamed_as_an_archive(self):
        _, payload = next(archive_members(FIXTURE.read_bytes()))
        with self.assertRaisesRegex(ValueError, "regular ar archive"):
            list(archive_members(payload))

    def test_truncated_actual_archive_is_rejected(self):
        with self.assertRaises(ValueError):
            list(archive_members(FIXTURE.read_bytes()[:-1]))

    def test_non_relocatable_elf_is_rejected(self):
        _, payload = next(archive_members(FIXTURE.read_bytes()))
        changed = bytearray(payload)
        changed[16:18] = b"\x02\x00"
        with self.assertRaisesRegex(ValueError, "got type 2"):
            elf_identity(changed, "executable member", 62)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", required=True, type=Path)
    args = parser.parse_args()
    FIXTURE = args.archive
    unittest.main(argv=[__file__], verbosity=2)
