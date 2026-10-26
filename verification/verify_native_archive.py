#!/usr/bin/env python3
"""Verify Linux wrapper object identity before Native cinterop/linking."""
import argparse
import hashlib
import json
import struct
from pathlib import Path

MACHINES = {"linux_x64": 62, "linux_arm64": 183}


def elf_identity(data, label, expected):
    if len(data) < 20 or data[:7] != b"\x7fELF\x02\x01\x01":
        raise ValueError(f"{label}: expected ELF64 little-endian object")
    kind, machine = struct.unpack_from("<HH", data, 16)
    if kind != 1 or machine != expected:
        raise ValueError(f"{label}: expected relocatable ELF machine {expected}, got type {kind} machine {machine}")
    return {"name": label, "machine": machine, "type": kind, "sha256": hashlib.sha256(data).hexdigest()}


def archive_members(data):
    if not data.startswith(b"!<arch>\n"):
        raise ValueError("Expected regular ar archive, not a thin archive or renamed object")
    offset = 8
    names = b""
    while offset < len(data):
        header = data[offset:offset + 60]
        if len(header) != 60 or header[58:] != b"`\n":
            raise ValueError("Invalid ar member header")
        size = int(header[48:58].strip())
        if size < 0 or offset + 60 + size > len(data):
            raise ValueError("Truncated ar member")
        name = header[:16].decode("ascii").strip()
        payload = data[offset + 60:offset + 60 + size]
        offset += 60 + size + size % 2
        if offset > len(data):
            raise ValueError("Missing ar alignment byte")
        if name == "//":
            names = payload
            continue
        if name in ("/", "/SYM64/"):
            continue
        if name.startswith("#1/"):
            length = int(name[3:])
            if length <= 0 or length > len(payload):
                raise ValueError("Invalid BSD extended member name")
            name = payload[:length].rstrip(b"\0").decode("utf-8")
            payload = payload[length:]
        elif name.startswith("/") and name[1:].isdigit():
            index = int(name[1:])
            if index >= len(names):
                raise ValueError("Invalid GNU extended member name")
            name = names[index:].split(b"\n", 1)[0].rstrip(b"/").decode("utf-8")
        else:
            name = name.rstrip("/")
        if name in ("__.SYMDEF", "__.SYMDEF SORTED", "__.SYMDEF_64", "__.SYMDEF_64 SORTED"):
            continue
        yield name, payload


def verify(archive, target, object_file=None):
    expected = MACHINES[target]
    data = Path(archive).read_bytes()
    members = [elf_identity(payload, name, expected) for name, payload in archive_members(data)]
    if not members:
        raise ValueError("Archive contains no ELF object members")
    report = {"target": target, "expected_machine": expected, "archive_sha256": hashlib.sha256(data).hexdigest(), "members": members}
    if object_file is not None:
        obj = elf_identity(Path(object_file).read_bytes(), str(object_file), expected)
        if not any(member["sha256"] == obj["sha256"] for member in members):
            raise ValueError("Compiled object bytes are absent from the archive")
        report["compiled_object"] = obj
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--target", required=True, choices=MACHINES)
    parser.add_argument("--object", dest="object_file", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    try:
        report = verify(args.archive, args.target, args.object_file)
    except (ValueError, OSError, UnicodeError, struct.error) as error:
        parser.exit(1, f"Native archive verification failed: {error}\n")
    text = json.dumps(report, indent=2) + "\n"
    if args.report:
        args.report.write_text(text)
    print(text, end="")


if __name__ == "__main__":
    main()
