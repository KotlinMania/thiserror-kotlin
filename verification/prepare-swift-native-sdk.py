"""Apply the published header repair to the pinned project Native 2.4.21 SDK."""
import argparse
import hashlib
import json
from pathlib import Path
import runpy
import zipfile


def digest(path):
    checksum = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            checksum.update(block)
    return checksum.hexdigest()


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--source", required=True, type=Path)
parser.add_argument("--destination", required=True, type=Path)
parser.add_argument("--helper", required=True, type=Path)
parser.add_argument("--helper-sha256", required=True)
parser.add_argument("--receipt", required=True, type=Path)
args = parser.parse_args()
if digest(args.helper) != args.helper_sha256:
    raise ValueError("Published Native SDK helper checksum mismatch")
helper_root = args.destination.parent / "published-helper"
helper_root.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(args.helper) as archive:
    for entry in archive.infolist():
        path = Path(entry.filename)
        if path.is_absolute() or ".." in path.parts or (entry.external_attr >> 16) & 0o170000 == 0o120000:
            raise ValueError("Unsafe helper archive member: " + entry.filename)
        destination = helper_root / path
        if entry.is_dir():
            destination.mkdir(parents=True, exist_ok=True)
        else:
            contents = archive.read(entry)
            if destination.exists() and destination.read_bytes() != contents:
                raise ValueError("Existing helper input changed: " + entry.filename)
            destination.parent.mkdir(parents=True, exist_ok=True)
            if not destination.exists():
                destination.write_bytes(contents)
lock = json.loads((helper_root / "inputs.lock.json").read_text())
# The published helper's header repair is identical in the installed 2.4.21 SDK.
# Pin the 2.4.21 inputs explicitly; never replace its compiler with 2.4.20.
lock["native_identity"] = {
    "bin/kotlinc-native": "5baa2f832ede18edd5933d8fe1f5327049e9a1b1ddb4a7d81998808b9cb460d8",
    "konan/konan.properties": "78435163bda2a45c45d065b4893271a46572e0a337e292a3bcf2f0162679f8ae",
    "konan/lib/kotlin-native-compiler-embeddable.jar": "bad46f043b98d14c4150d654ea98996cabf71e7ad87ea3a57538956164443105",
}
helper = runpy.run_path(str(helper_root / "scripts/prepare_native_sdk.py"))
header = lock["native_header"]["relative_path"]
if args.destination.exists():
    receipt = json.loads(args.receipt.read_text())
    expected = dict(receipt["source_files"])
    expected[header] = lock["native_header"]["candidate_sha256"]
    actual = helper["inventory"](args.destination)
    changed = [name for name, checksum in expected.items() if actual.get(name) != checksum]
    generated = {name: checksum for name, checksum in actual.items() if name not in expected}
    cache_prefix = "klib/cache/macos_arm64-gSTATIC-user-pl-swift_export/"
    unexpected = [name for name in generated if not name.startswith(cache_prefix)]
    if changed or unexpected:
        raise ValueError("Prepared Native SDK inputs changed: " + repr((changed + unexpected)[:20]))
    args.receipt.with_name("generated-cache-audit.json").write_text(json.dumps({
        "unchanged_sdk_input_files": len(expected),
        "generated_dependency_cache_files": generated,
    }, indent=2, sort_keys=True) + "\n")
    for name, checksum in lock["native_identity"].items():
        if expected.get(name) != checksum:
            raise ValueError("Prepared Native compiler identity mismatch: " + name)
else:
    patch = helper_root / next(row["patch"] for row in lock["sources"] if row["kind"] == "header")
    receipt = helper["prepare"](args.source, args.destination, lock, patch)
    receipt["kotlin_version"] = "2.4.21"
    receipt["compatibility_scope"] = "Project-pinned macOS ARM64 2.4.21 SDK; published header-only repair"
    receipt["published_helper_sha256"] = args.helper_sha256
    args.receipt.write_text(json.dumps(receipt, indent=2, sort_keys=True) + "\n")
print(json.dumps({"sdk": str(args.destination), "kotlin_version": "2.4.21", "header_sha256": lock["native_header"]["candidate_sha256"], "compiler_binaries_changed": False}))
