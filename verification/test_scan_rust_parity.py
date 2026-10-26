"""Parser regressions with data fixtures; never library runtime/port acceptance."""
import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from scan_rust_parity import audit, matches


class CargoAuditTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'gradle').mkdir()
        (self.root / 'gradle/libs.versions.toml').write_text('[versions]\nkotlin="2.4.20"\n')

    def index(self, manifests, source_paths=()):
        rows, tree = [], []
        for n, text in manifests.items():
            p = self.root / n.replace('/', '__')
            raw = text.encode()
            p.write_bytes(raw)
            blob = hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest()
            rows.append({'upstream_path': n, 'local_path': str(p), 'sha256': hashlib.sha256(raw).hexdigest(), 'git_blob_sha1': blob})
            tree.append({'path': n, 'type': 'blob', 'sha': blob})
        tree += [{'path': p, 'type': 'blob', 'sha': 'b' * 40} for p in source_paths]
        tp = self.root / 'tree.json'
        tp.write_text(json.dumps({'truncated': False, 'tree': tree}))
        p = self.root / 'index.json'
        p.write_text(json.dumps({'revision': 'a' * 40, 'manifests': rows,
                                'tree': {'local_path': str(tp), 'sha256': hashlib.sha256(tp.read_bytes()).hexdigest(), 'revision': 'a' * 40}}))
        return p

    def test_inherited_features_defaults_and_package_version(self):
        p = self.index({'Cargo.toml': '[workspace]\nmembers=["member"]\n[workspace.package]\nversion="1.2.3"\n[workspace.dependencies.serde]\nversion="1"\ndefault-features=false\nfeatures=["alloc"]\n',
                        'member/Cargo.toml': '[package]\nname="member"\nversion.workspace=true\n[dependencies.serde]\nworkspace=true\nfeatures=["derive"]\n'})
        out = audit(p, self.root)
        edge = out['dependencies'][0]
        self.assertEqual(edge['features'], ['alloc', 'derive'])
        self.assertFalse(edge['default_features'])
        self.assertEqual(out['members'][0]['package']['version'], '1.2.3')

    def test_uncertain_inherited_default_override_is_not_disabled(self):
        p = self.index({'Cargo.toml': '[workspace]\nmembers=["m"]\n[workspace.dependencies]\nserde="1"\n',
                        'm/Cargo.toml': '[package]\nname="m"\n[dependencies.serde]\nworkspace=true\ndefault-features=false\n'})
        out = audit(p, self.root)
        self.assertIsNone(out['dependencies'][0]['default_features'])
        self.assertIn('DEFAULT_FEATURE_OVERRIDE', {x['code'] for x in out['findings']})

    def test_glob_and_explicit_exclusion(self):
        p = self.index({'Cargo.toml': '[workspace]\nmembers=["crates/*"]\nexclude=["crates/b"]\n',
                        'crates/a/Cargo.toml': '[package]\nname="a"\n',
                        'crates/b/Cargo.toml': '[package]\nname="b"\n'})
        out = audit(p, self.root)
        self.assertEqual([x['manifest'] for x in out['members']], ['crates/a/Cargo.toml'])
        self.assertEqual(out['excluded_manifests'][0]['reason'], 'workspace.exclude')
        self.assertFalse(matches('crates/*', 'crates/a/nested'))

    def test_missing_member_is_reported_not_ignored(self):
        p = self.index({'Cargo.toml': '[workspace]\nmembers=["absent"]\n'})
        self.assertIn('MISSING_MEMBER', {x['code'] for x in audit(p, self.root)['findings']})

    def test_tree_member_requires_actual_manifest_bytes(self):
        p = self.index({'Cargo.toml': '[workspace]\nmembers=["m"]\n'}, ['m/Cargo.toml'])
        self.assertIn('MISSING_MANIFEST_BYTES', {x['code'] for x in audit(p, self.root)['findings']})

    def test_path_edge_member_and_renamed_package(self):
        p = self.index({'Cargo.toml': '[package]\nname="root"\n[workspace]\n[dependencies.alias]\npackage="member"\npath="m"\n',
                        'm/Cargo.toml': '[package]\nname="member"\n'})
        out = audit(p, self.root)
        self.assertEqual(out['dependencies'][0]['package'], 'member')
        self.assertEqual(out['dependencies'][0]['path_manifest'], 'm/Cargo.toml')
        self.assertEqual(len(out['members']), 2)

    def test_path_dependency_without_workspace_is_not_assumed_member(self):
        p = self.index({'Cargo.toml': '[package]\nname="root"\n[dependencies.member]\npath="m"\n',
                        'm/Cargo.toml': '[package]\nname="member"\n'})
        out = audit(p, self.root)
        self.assertEqual([x['manifest'] for x in out['members']], ['Cargo.toml'])
        self.assertIn('PATH_CRATE_GRAPH_UNVERIFIED', {x['code'] for x in out['findings']})

    def test_target_specific_build_and_dev_edges_preserved(self):
        p = self.index({'Cargo.toml': '[package]\nname="root"\n[target.\'cfg(unix)\'.build-dependencies]\ncc="1"\n[dev-dependencies]\nsyn={version="2",features=["full","visit"]}\n'})
        out = audit(p, self.root)
        self.assertEqual({x['kind'] for x in out['dependencies']}, {'build-dependencies', 'dev-dependencies'})
        self.assertIn('cfg(unix)', {x['target_condition'] for x in out['dependencies']})
        self.assertEqual(next(x for x in out['dependencies'] if x['package'] == 'syn')['features'], ['full', 'visit'])

    def test_automatic_tests_and_explicit_harness_override(self):
        p = self.index({'Cargo.toml': '[package]\nname="root"\n[[test]]\nname="abi"\nharness=false\n'}, ['tests/abi.rs', 'tests/additional.rs'])
        tests = [x for x in audit(p, self.root)['targets'] if x['kind'] == 'test']
        self.assertEqual(len(tests), 2)
        self.assertFalse(next(x for x in tests if x['name'] == 'abi')['harness'])
        self.assertEqual(next(x for x in tests if x['name'] == 'additional')['discovered'], 'automatic')

    def test_disabled_build_and_explicit_lib_are_respected(self):
        p = self.index({'Cargo.toml': '[package]\nname="root"\nbuild=false\nautolib=false\n[lib]\npath="custom.rs"\n'}, ['build.rs', 'custom.rs'])
        kinds = {x['kind'] for x in audit(p, self.root)['targets']}
        self.assertIn('lib', kinds)
        self.assertNotIn('build', kinds)

    def test_implicit_optional_feature_and_dep_suppression(self):
        for explicit, expected in [('', True), ('[features]\ncustom=["dep:foo"]\n', False)]:
            p = self.index({'Cargo.toml': '[package]\nname="root"\n[dependencies.foo]\nversion="1"\noptional=true\n' + explicit})
            keys = {x['id'] for x in audit(p, self.root)['features']}
            self.assertEqual('Cargo.toml::foo' in keys, expected)

    def test_stale_manifest_hash_refused(self):
        p = self.index({'Cargo.toml': '[package]\nname="root"\n'})
        (self.root / 'Cargo.toml').write_text('changed')
        with self.assertRaisesRegex(ValueError, 'Stale manifest'):
            audit(p, self.root)

    def test_catalog_and_classification_never_establish_runtime_acceptance(self):
        p = self.index({'Cargo.toml': '[package]\nname="root"\n[patch.crates-io]\nfoo={path="m"}\n'})
        out = audit(p, self.root)
        self.assertIn('CARGO_OVERRIDE_GRAPH_UNVERIFIED', {x['code'] for x in out['findings']})
        self.assertIn('unverified', out['port_acceptance'])
        self.assertIn('not resolved', out['actual_runtime_test_tooling_graph'])


if __name__ == '__main__':
    unittest.main(verbosity=2)
