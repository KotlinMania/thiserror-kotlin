"""Exercise only the reserved legacy tool and small data fixtures; no fleet apply."""
import contextlib
import importlib.util
import io
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

WORKSPACE = Path(__file__).resolve().parents[2]
TOOL = WORKSPACE / 'tools/sweep_template_diff.py'
spec = importlib.util.spec_from_file_location('legacy_sweep', TOOL)
sweep = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sweep)
SCRATCH = WORKSPACE / 'automation-artifacts/2026-10-21-template-dependabot-audit/regression-fixtures'
SCRATCH.mkdir(parents=True, exist_ok=True)


class LegacyPinSafetyTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(dir=SCRATCH)
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.repo = self.root / 'project'
        self.template = self.root / 'template'
        for root in [self.repo, self.template]:
            (root / 'gradle').mkdir(parents=True)
            (root / 'gradle/libs.versions.toml').write_text('[versions]\nkotlin="9.9.9"\nandroidKmp="9.4.1"\n')
            (root / 'gradle.properties').write_text('versions.kotlin=9.9.9\n')
        self.workflows = self.repo / '.github/workflows'
        self.workflows.mkdir(parents=True)

    def test_generic_callable_rejects_before_any_inspection_or_write(self):
        before = {p: p.read_bytes() for p in self.repo.rglob('*') if p.is_file()}
        with patch.object(sweep, 'get_template_files', side_effect=AssertionError('No inspection allowed')):
            with self.assertRaisesRegex(ValueError, 'Generic --fix is disabled'):
                sweep.diff_repo(str(self.repo), fix=True)
        self.assertEqual(before, {p: p.read_bytes() for p in self.repo.rglob('*') if p.is_file()})

    def test_generic_cli_fix_rejects_without_creating_missing_paths(self):
        missing = self.root / 'absent'
        result = subprocess.run([sys.executable, str(TOOL), str(missing), '--fix'],
                                text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                env=dict(os.environ, PYTHONDONTWRITEBYTECODE='1'))
        self.assertEqual(result.returncode, 2)
        self.assertIn('Generic --fix is disabled', result.stderr)
        self.assertFalse(missing.exists())

    def test_all_fix_rejects_before_workspace_enumeration(self):
        with patch.object(sys, 'argv', [str(TOOL), '--all', '--fix']), \
                patch.object(sweep.os, 'listdir', side_effect=AssertionError('No fleet inspection allowed')), \
                contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit) as error:
                sweep.main()
        self.assertEqual(error.exception.code, 2)

    def test_only_exact_reviewed_old_refs_upgrade(self):
        text = "  - uses: 'actions/setup-java@v6.0.1' # preserve\r\n  - uses: gradle/actions/setup-gradle@v6.3.0\r\n  - uses: actions/setup-node@v7.0.0\r\n"
        after = sweep.rewrite_action_pins(text)
        self.assertEqual(after, text.replace('@v6.0.1', '@v6.1.0').replace('@v6.3.0', '@v6.4.0').replace('@v7.0.0', '@v7.1.0'))
        self.assertEqual(after, sweep.rewrite_action_pins(after))

    def test_newer_unknown_sha_and_non_uses_strings_are_preserved(self):
        text = '\n'.join([
            'uses: actions/setup-java@v6.1.0', 'uses: actions/setup-java@v6.2.0',
            'uses: gradle/actions/setup-gradle@v6.4.0', 'uses: gradle/actions/setup-gradle@v9.0.0',
            'uses: actions/setup-java@' + 'a' * 40, 'uses: actions/setup-java@main',
            'uses: actions/setup-java@v6', 'uses: github/codeql-action/analyze@v99.0.0',
            '# uses: actions/setup-java@v6.0.1', 'run: echo actions/setup-java@v6.0.1',
        ])
        self.assertEqual(text, sweep.rewrite_action_pins(text))

    def test_hidden_yml_yaml_dry_run_apply_and_idempotence(self):
        paths = [self.workflows / 'test.yml', self.repo / '.github/actions/helper/action.yaml', self.repo / '.github/policy.yaml']
        for p in paths:
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text('uses: actions/setup-java@v6.0.1\n')
        before = {p: p.read_bytes() for p in paths}
        with contextlib.redirect_stdout(io.StringIO()):
            proposed = sweep.sync_action_pins(str(self.repo), fix=False)
            self.assertEqual(len(proposed), 3)
            self.assertEqual(before, {p: p.read_bytes() for p in paths})
            self.assertEqual(len(sweep.sync_action_pins(str(self.repo), fix=True)), 3)
            self.assertEqual(sweep.sync_action_pins(str(self.repo), fix=True), [])
        for p in paths:
            self.assertEqual(p.read_text(), 'uses: actions/setup-java@v6.1.0\n')

    def test_generic_diagnostic_reads_current_template_catalog_without_writing(self):
        catalog = self.repo / 'gradle/libs.versions.toml'
        catalog.write_text('[versions]\nkotlin="10.0.0"\n')
        before = catalog.read_bytes()
        output = io.StringIO()
        with patch.object(sweep, 'TEMPLATE_DIR', str(self.template)), \
                patch.object(sweep, 'get_template_files', return_value=([], [])), contextlib.redirect_stdout(output):
            sweep.diff_repo(str(self.repo), fix=False)
        self.assertIn('9.9.9', output.getvalue())
        self.assertNotIn('2.4.20', output.getvalue())
        self.assertEqual(before, catalog.read_bytes())

    def test_generic_diagnostic_preserves_newer_action_and_codeql_pins(self):
        p = self.workflows / 'custom.yaml'
        p.write_text('uses: actions/setup-java@v6.2.0\nuses: github/codeql-action/analyze@v99.0.0\n')
        before = p.read_bytes()
        output = io.StringIO()
        with patch.object(sweep, 'TEMPLATE_DIR', str(self.template)), \
                patch.object(sweep, 'get_template_files', return_value=([], [])), contextlib.redirect_stdout(output):
            sweep.diff_repo(str(self.repo), fix=False)
        self.assertNotIn('Outdated workflows', output.getvalue())
        self.assertEqual(before, p.read_bytes())

    def test_generic_diagnostic_includes_yaml_extension(self):
        p = self.workflows / 'custom.yaml'
        p.write_text('uses: gradle/actions/setup-gradle@v6.3.0\n')
        before = p.read_bytes()
        output = io.StringIO()
        with patch.object(sweep, 'TEMPLATE_DIR', str(self.template)), \
                patch.object(sweep, 'get_template_files', return_value=([], [])), contextlib.redirect_stdout(output):
            sweep.diff_repo(str(self.repo), fix=False)
        self.assertIn('custom.yaml', output.getvalue())
        self.assertEqual(before, p.read_bytes())


if __name__ == '__main__':
    unittest.main(verbosity=2)
