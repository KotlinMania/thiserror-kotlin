"""Exercise publication rejection paths using a real, already verified bundle."""

import argparse
import hashlib
import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from verify_android_publication import verify as verify_generic


def verify(bundle, group, module, version):
    return verify_generic(bundle, group, module, version, set(ARGS.public_class),
                          [tuple(edge.split(":")) for edge in ARGS.api_dependency])


def packed(entries):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, content in entries.items():
            archive.writestr(name, content)
    return output.getvalue()


class AndroidPublicationTest(unittest.TestCase):
    def setUp(self):
        with zipfile.ZipFile(ARGS.bundle) as archive:
            self.entries = {name: archive.read(name) for name in archive.namelist()}
        prefix = ARGS.group.replace(".", "/")
        self.root = f"{prefix}/{ARGS.module}/{ARGS.version}/{ARGS.module}-{ARGS.version}.module"
        stem = f"{prefix}/{ARGS.module}-android/{ARGS.version}/{ARGS.module}-android-{ARGS.version}"
        self.metadata = f"{stem}.module"
        self.aar = f"{stem}.aar"
        self.pom = f"{stem}.pom"

    def reject(self, reason):
        with tempfile.TemporaryDirectory(dir=ARGS.scratch) as temporary:
            bundle = Path(temporary) / "invalid.zip"
            bundle.write_bytes(packed(self.entries))
            with self.assertRaisesRegex(ValueError, reason):
                verify(bundle, ARGS.group, ARGS.module, ARGS.version)

    def replace_aar(self, payload):
        self.entries[self.aar] = payload
        metadata = json.loads(self.entries[self.metadata])
        for variant in metadata["variants"]:
            if variant.get("attributes", {}).get("org.gradle.libraryelements") == "aar":
                for descriptor in variant["files"]:
                    descriptor["size"] = len(payload)
                    for algorithm in ["sha256", "sha512", "sha1", "md5"]:
                        descriptor[algorithm] = hashlib.new(algorithm, payload).hexdigest()
        self.entries[self.metadata] = json.dumps(metadata).encode()

    def test_real_reference_bundle(self):
        self.assertEqual(verify(ARGS.bundle, ARGS.group, ARGS.module, ARGS.version)["status"], "passed")

    def test_missing_aar(self):
        del self.entries[self.aar]
        self.reject("Missing Android publication entry")

    def test_jar_renamed_to_aar(self):
        with zipfile.ZipFile(io.BytesIO(self.entries[self.aar])) as aar:
            self.replace_aar(aar.read("classes.jar"))
        self.reject("AAR has no Android manifest")

    def test_missing_expansion_api(self):
        with zipfile.ZipFile(io.BytesIO(self.entries[self.aar])) as aar:
            entries = {name: aar.read(name) for name in aar.namelist()}
        with zipfile.ZipFile(io.BytesIO(entries["classes.jar"])) as classes:
            names = {name: classes.read(name) for name in classes.namelist()}
        del names[ARGS.public_class[0]]
        entries["classes.jar"] = packed(names)
        self.replace_aar(packed(entries))
        self.reject("missing public API class")

    def test_wrong_root_redirect(self):
        metadata = json.loads(self.entries[self.root])
        for variant in metadata["variants"]:
            if variant.get("attributes", {}).get("org.gradle.libraryelements") == "aar":
                variant["available-at"]["version"] = "0.0.0"
        self.entries[self.root] = json.dumps(metadata).encode()
        self.reject("redirects to the wrong publication")

    def test_payload_hash_mismatch(self):
        self.entries[self.aar] += b"changed"
        self.reject("size or SHA-256 disagrees")

    def test_missing_public_dependency(self):
        metadata = json.loads(self.entries[self.metadata])
        for variant in metadata["variants"]:
            if variant.get("attributes", {}).get("org.gradle.usage") == "java-api":
                variant["dependencies"] = [d for d in variant.get("dependencies", []) if d["module"] != ARGS.api_dependency[0].split(":")[1]]
        self.entries[self.metadata] = json.dumps(metadata).encode()
        self.reject("Public signature dependencies missing")

    def test_wrong_pom_packaging(self):
        self.entries[self.pom] = self.entries[self.pom].replace(
            b"<packaging>aar</packaging>", b"<packaging>jar</packaging>"
        )
        self.reject("Incorrect Android POM packaging")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path)
    parser.add_argument("--group", required=True)
    parser.add_argument("--module", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--scratch", required=True, type=Path)
    parser.add_argument("--public-class", action="append", required=True)
    parser.add_argument("--api-dependency", action="append", required=True)
    ARGS = parser.parse_args()
    ARGS.scratch.mkdir(parents=True, exist_ok=True)
    unittest.main(argv=[__file__], verbosity=2)
