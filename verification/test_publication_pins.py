"""Exercise stale-metadata rejection against a real staged publication."""
import argparse
import json
from pathlib import Path
import shutil
import tempfile
import unittest
from verify_publication_pins import verify


class PublicationPinsTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(dir=options.scratch)
        self.addCleanup(self.temporary.cleanup)
        self.repository = Path(self.temporary.name)
        prefix = Path(options.group.replace(".", "/"))
        self.root = self.repository / prefix / options.module / options.version / (options.module + "-" + options.version)
        self.android = self.repository / prefix / (options.module + "-android") / options.version / (options.module + "-android-" + options.version)
        for base, suffixes in [(self.root, [".module", ".pom"]), (self.android, [".module", ".pom", ".aar"])]:
            for suffix in suffixes:
                destination = Path(str(base) + suffix)
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(options.repository / destination.relative_to(self.repository), destination)

    def check(self):
        return verify(self.repository, options.group, options.module, options.version, expected, mappings)

    def stale_module(self, base, usage):
        path = Path(str(base) + ".module")
        metadata = json.loads(path.read_text())
        variant = next(v for v in metadata["variants"] if v.get("attributes", {}).get("org.gradle.usage") == usage and v.get("dependencies"))
        edge = next(d for d in variant["dependencies"] if (d["group"], d["module"]) == reviewed_edge)
        edge["version"]["requires"] = "0.0.0-stale"
        path.write_text(json.dumps(metadata))

    def test_actual_publication_accepts_implementation_scope(self):
        self.assertEqual(self.check()["status"], "passed")

    def test_dynamic_expected_versions_are_rejected(self):
        for selector in ["latest.release", "latest.integration", "1.+", "[1,2)"]:
            with self.subTest(selector=selector):
                dynamic = dict(expected)
                dynamic[reviewed_edge] = selector
                with self.assertRaisesRegex(ValueError, "Expected exact declared dependency pins"):
                    verify(self.repository, options.group, options.module, options.version, dynamic, mappings)

    def test_runtime_dependency_cannot_disappear(self):
        path = Path(str(self.android) + ".module")
        metadata = json.loads(path.read_text())
        variant = next(v for v in metadata["variants"] if v["attributes"].get("org.gradle.usage") == "java-runtime")
        variant["dependencies"] = [d for d in variant["dependencies"] if (d["group"], d["module"]) != reviewed_edge]
        path.write_text(json.dumps(metadata))
        with self.assertRaisesRegex(ValueError, "Missing required dependency"):
            self.check()

    def test_present_api_dependency_must_have_correct_version(self):
        path = Path(str(self.android) + ".module")
        metadata = json.loads(path.read_text())
        api = next(v for v in metadata["variants"] if v["attributes"].get("org.gradle.usage") == "java-api")
        api["dependencies"] = [d for d in api.get("dependencies", []) if (d["group"], d["module"]) != reviewed_edge]
        api["dependencies"].append({"group": reviewed_edge[0], "module": reviewed_edge[1], "version": {"requires": "0.0.0-stale"}})
        path.write_text(json.dumps(metadata))
        with self.assertRaisesRegex(ValueError, "Wrong dependency pin"):
            self.check()

    def test_duplicate_runtime_edge_is_rejected(self):
        path = Path(str(self.android) + ".module")
        metadata = json.loads(path.read_text())
        runtime = next(v for v in metadata["variants"] if v["attributes"].get("org.gradle.usage") == "java-runtime")
        runtime["dependencies"].append(next(d for d in runtime["dependencies"] if (d["group"], d["module"]) == reviewed_edge))
        path.write_text(json.dumps(metadata))
        with self.assertRaisesRegex(ValueError, "Duplicate dependency"):
            self.check()

    def test_jvm_pom_target_requires_explicit_mapping(self):
        path = Path(str(self.android) + ".pom")
        text = path.read_text()
        old = mappings.get(reviewed_edge, reviewed_edge[1])
        replacement = reviewed_edge[1] + "-jvm"
        self.assertIn("<artifactId>" + old + "</artifactId>", text)
        path.write_text(text.replace("<artifactId>" + old + "</artifactId>", "<artifactId>" + replacement + "</artifactId>"))
        with self.assertRaisesRegex(ValueError, "Missing POM dependencies"):
            self.check()
        explicit = dict(mappings, **{})
        explicit[reviewed_edge] = replacement
        self.assertEqual(verify(self.repository, options.group, options.module, options.version, expected, explicit)["status"], "passed")

    def test_stale_root_module_is_rejected(self):
        self.stale_module(self.root, "kotlin-metadata")
        with self.assertRaisesRegex(ValueError, "Wrong dependency pin"):
            self.check()

    def test_stale_android_runtime_module_is_rejected(self):
        self.stale_module(self.android, "java-runtime")
        with self.assertRaisesRegex(ValueError, "Wrong dependency pin"):
            self.check()

    def test_stale_root_pom_is_rejected(self):
        path = Path(str(self.root) + ".pom")
        text = path.read_text()
        current = expected[reviewed_edge]
        self.assertIn("<version>" + current + "</version>", text)
        path.write_text(text.replace("<version>" + current + "</version>", "<version>0.0.0-stale</version>"))
        with self.assertRaisesRegex(ValueError, "Wrong POM dependency pin"):
            self.check()

    def test_stale_android_pom_is_rejected(self):
        path = Path(str(self.android) + ".pom")
        current = expected[reviewed_edge]
        path.write_text(path.read_text().replace("<version>" + current + "</version>", "<version>0.0.0-stale</version>"))
        with self.assertRaisesRegex(ValueError, "Wrong POM dependency pin"):
            self.check()

    def test_stale_target_redirect_is_rejected(self):
        path = Path(str(self.root) + ".module")
        metadata = json.loads(path.read_text())
        next(v for v in metadata["variants"] if "available-at" in v)["available-at"]["version"] = "0.0.0-stale"
        path.write_text(json.dumps(metadata))
        with self.assertRaisesRegex(ValueError, "Stale target redirect"):
            self.check()

    def test_changed_aar_is_rejected(self):
        path = Path(str(self.android) + ".aar")
        path.write_bytes(path.read_bytes() + b"changed")
        with self.assertRaisesRegex(ValueError, "fingerprint mismatch"):
            self.check()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repository", type=Path)
    parser.add_argument("--group", required=True)
    parser.add_argument("--module", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--scratch", required=True, type=Path)
    parser.add_argument("--reviewed-dependency", required=True, help="group:root-module mutation target")
    parser.add_argument("--pom-dependency", action="append", default=[])
    parser.add_argument("--dependency", action="append", required=True)
    options = parser.parse_args()
    expected = {(parts[0], parts[1]): parts[2] for parts in [value.split(":") for value in options.dependency]}
    options.scratch.mkdir(parents=True, exist_ok=True)
    mappings = {(g, root): target for g, root, target in [value.split(":") for value in options.pom_dependency]}
    reviewed_edge = tuple(options.reviewed_dependency.split(":"))
    if reviewed_edge not in expected:
        raise ValueError("Supply a declared dependency as the mutation target")
    unittest.main(argv=[__file__], verbosity=2)
