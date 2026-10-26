"""Template defaults and project-preservation regressions; no runtime acceptance."""
import copy
import json
import re
import tomllib
import unittest
from pathlib import Path

from sync_android_publication import block, tokens, transform
from test_sync_android_publication import BUILD

ROOT = Path(__file__).resolve().parent.parent
BUILD_TEXT = (ROOT / 'build.gradle.kts').read_text()
CATALOG = tomllib.loads((ROOT / 'gradle/libs.versions.toml').read_text())
PROPERTIES = dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text().splitlines()
                  if '=' in line and not line.lstrip().startswith('#'))
FRAGMENT = (ROOT / 'verification/android-publication.gradle.kts.fragment').read_text()
PLUGIN_ID = 'org.jetbrains.kotlin.plugin.serialization'


def default_serialization_dependencies(catalog):
    selected = []
    for suffix in ['commonMainBundle', 'commonMainApiBundle', 'commonTestBundle', 'commonBenchmarkBundle']:
        bundle = PROPERTIES.get('project.dependencies.' + suffix, '').strip()
        for alias in catalog.get('bundles', {}).get(bundle, []):
            library = catalog['libraries'][alias]
            module = library['module'] if isinstance(library, dict) else ':'.join(library.split(':')[:2])
            if module.startswith('org.jetbrains.kotlinx:kotlinx-serialization-'):
                selected.append(module)
    return selected


def serialization_plugin_applications(build, catalog):
    ts = tokens(build)
    opening, closing = block(ts, ['plugins', '{'])
    selected = []
    for name, plugin in catalog['plugins'].items():
        if plugin['id'] != PLUGIN_ID:
            continue
        pattern = [t[0] for t in tokens('alias(libs.plugins.' + re.sub('[-_]', '.', name) + ')')]
        for i in range(opening + 1, closing - len(pattern) + 1):
            if [t[0] for t in ts[i:i + len(pattern)]] == pattern:
                if [t[0] for t in ts[i + len(pattern):i + len(pattern) + 2]] != ['apply', 'false']:
                    selected.append(plugin['id'])
    for i in range(opening + 1, closing - 2):
        # Structural tokens ignore comments and strings; read only this call's literal argument.
        call = [t[0] for t in ts[i:i + 3]]
        if call in [['id', '(', ')'], ['kotlin', '(', ')']]:
            value = json.loads(build[ts[i + 1][2]:ts[i + 2][1]])
            actual = 'org.jetbrains.kotlin.' + value if call[0] == 'kotlin' else value
            if actual == PLUGIN_ID:
                selected.append(actual)
    return selected


class SerializationDefaultsTests(unittest.TestCase):
    def test_default_bundles_do_not_inject_serialization(self):
        self.assertEqual(default_serialization_dependencies(CATALOG), [])

    def test_default_build_does_not_apply_serialization_plugin(self):
        self.assertEqual(serialization_plugin_applications(BUILD_TEXT, CATALOG), [])

    def test_default_bundle_excludes_known_datetime_serialization_path(self):
        selected = CATALOG['bundles'][PROPERTIES['project.dependencies.commonMainBundle']]
        modules = {CATALOG['libraries'][alias]['module'] for alias in selected}
        self.assertNotIn('org.jetbrains.kotlinx:kotlinx-datetime', modules)
        self.assertEqual(CATALOG['libraries']['kotlinx-datetime']['module'], 'org.jetbrains.kotlinx:kotlinx-datetime')

    def test_unused_catalog_opt_ins_keep_real_modules_and_version(self):
        self.assertEqual(CATALOG['plugins']['kotlin-serialization']['id'], PLUGIN_ID)
        for kind in ['core', 'json']:
            entry = CATALOG['libraries']['kotlinx-serialization-' + kind]
            self.assertEqual(entry['module'], 'org.jetbrains.kotlinx:kotlinx-serialization-' + kind)
            self.assertIn(entry['version']['ref'], CATALOG['versions'])
        self.assertEqual(default_serialization_dependencies(CATALOG), [])

    def test_renamed_alias_cannot_hide_automatic_core_or_json(self):
        for kind in ['core', 'json']:
            catalog = copy.deepcopy(CATALOG)
            catalog['libraries']['wire-codec'] = copy.deepcopy(catalog['libraries']['kotlinx-serialization-' + kind])
            catalog['bundles'][PROPERTIES['project.dependencies.commonMainBundle']].append('wire-codec')
            self.assertEqual(default_serialization_dependencies(catalog), ['org.jetbrains.kotlinx:kotlinx-serialization-' + kind])

    def test_plugin_reintroduction_detected_for_catalog_and_direct_dsl(self):
        ts = tokens(BUILD_TEXT)
        _, end = block(ts, ['plugins', '{'])
        for application in ['alias(libs.plugins.kotlin.serialization)',
                            'id("org.jetbrains.kotlin.plugin.serialization")', 'kotlin("plugin.serialization")']:
            build = BUILD_TEXT[:ts[end][1]] + application + '\n' + BUILD_TEXT[ts[end][1]:]
            self.assertEqual(serialization_plugin_applications(build, CATALOG), [PLUGIN_ID])

    def test_comment_and_literal_examples_are_not_plugin_applications(self):
        ts = tokens(BUILD_TEXT)
        _, end = block(ts, ['plugins', '{'])
        example = '// alias(libs.plugins.kotlin.serialization)\nval example = "alias(libs.plugins.kotlin.serialization)"\n'
        build = BUILD_TEXT[:ts[end][1]] + example + BUILD_TEXT[ts[end][1]:]
        self.assertEqual(serialization_plugin_applications(build, CATALOG), [])

    def test_safe_publication_sync_preserves_project_serialization_opt_in(self):
        build = BUILD.replace('plugins {', 'plugins { alias(libs.plugins.kotlin.serialization)\n')
        build = build.replace('implementation(libs.private.kotlin)', 'implementation(libs.kotlinx.serialization.json)')
        after = transform(build, ['libs.syn.kotlin'], FRAGMENT)
        self.assertIn('alias(libs.plugins.kotlin.serialization)', after)
        self.assertIn('implementation(libs.kotlinx.serialization.json)', after)
        self.assertNotIn('serde', after.lower())
        self.assertEqual(after, transform(after, ['libs.syn.kotlin'], FRAGMENT))


if __name__ == '__main__':
    unittest.main(verbosity=2)
