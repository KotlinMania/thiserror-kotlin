"""Read-only known Swift problem scan; source findings are never runtime acceptance."""
import argparse
import hashlib
import json
import re
import shlex
import xml.etree.ElementTree as ET
from pathlib import Path

from sync_android_publication import tokens, validate_warning_policy

REGISTRY = Path(__file__).with_name('swift-known-problems.json')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def source_files(root):
    paths = [root / name for name in ['build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradle/libs.versions.toml']]
    for directory in ['src', 'swift-test-harness', 'native', '.github', 'verification']:
        base = root / directory
        if base.is_dir():
            paths.extend(p for p in base.rglob('*') if p.is_file() and p.suffix in ['.kt', '.swift', '.c', '.cpp', '.h', '.def', '.yml', '.yaml', '.gyp', '.json', '.py', '.xcconfig']
                         and not any(v in ['.build', 'node_modules', 'build', '.git'] for v in p.relative_to(base).parts))
    return sorted(set(p for p in paths if p.is_file()))


def inventory(root, files):
    return {str(p.relative_to(root)): sha(p) for p in files}


def generated_files(root):
    return sorted(p for p in root.rglob('*') if p.is_file() and p.suffix in ['.swift', '.kt', '.h', '.c', '.modulemap']
                  and not any(v in ['.build', '.git'] for v in p.relative_to(root).parts)) if root else []


def scan(root, generated=None, bridge=None, evidence=None, required_tests=()):
    registry = json.loads(REGISTRY.read_text())
    findings = []
    def add(rule, path, text, match=0):
        findings.append(dict(registry['rules'][rule], rule=rule, path=str(path), line=text.count('\n', 0, match) + 1))
    source = source_files(root)
    emitted = generated_files(generated) + generated_files(bridge)
    manifests = [p for p in source + emitted if p.name == 'Package.swift']
    for p in source + emitted:
        text = p.read_text(errors='replace')
        is_generated = p in emitted
        if p.name == 'build.gradle.kts':
            try:
                validate_warning_policy(text)
            except ValueError:
                add('SWIFT001', p, text)
            if re.search(r'(?:patched[-_].*export|swiftExportClasspath\s*\.\s*setFrom)', text, re.I):
                add('SWIFT007', p, text)
            if ('writeText' in text and 'replace' in text and re.search(r'(?:Serialization|removeRange|filterNot).*', text)
                    and re.search(r'(?:spmDir|\.swift["\'])', text)):
                add('SWIFT003', p, text)
            if re.search(r'replace[^\n]*swift-tools-version[^\n]*5\.9', text):
                add('SWIFT002', p, text)
        if p.suffix in ['.yml', '.yaml', '.xcconfig']:
            if re.search(r'SWIFT_VERSION\s*[:=]\s*[\"\']?5|swift-version[\"\']?\s*[, ]+5', text):
                add('SWIFT002', p, text)
            if re.search(r'SWIFT_TREAT_WARNINGS_AS_ERRORS\s*[:=]\s*(?:NO|false)', text, re.I):
                add('SWIFT001', p, text)
        if p.suffix == '.swift':
            if not is_generated:
                for match in re.finditer(r'(?:#expect\(\s*(?:Bool\()?true|XCTAssertTrue\(\s*true)', text):
                    add('SWIFT008', p, text, match.start())
            if is_generated:
                for pattern, rule in [(r'@_implementationOnly\s+import', 'SWIFT004'),
                                      (r'struct\s+KotlinError\s*:[\s\S]{0,500}wrapped\s*:\s*KotlinRuntime\.KotlinBase', 'SWIFT005'),
                                      (r'@preconcurrency|@unchecked\s+Sendable', 'SWIFT007')]:
                    match = re.search(pattern, text)
                    if match:
                        add(rule, p, text, match.start())
        if is_generated and p.suffix == '.kt':
            match = re.search(r'as\s+kotlin\.Function\d+<|as\s+io\.github\.kotlinmania\.[^<\n]+<kotlin\.Any|_result\s*;\s*true', text)
            if match:
                add('SWIFT006', p, text, match.start())
    for p in manifests:
        text = p.read_text()
        values = [v[0] for v in tokens(text)]
        header = re.match(r'// swift-tools-version:\s*(\d+)\.(\d+)', text)
        strict = ['swiftLanguageModes', ':', '[', '.', 'v6', ']']
        package_mode = any(values[i:i + len(strict)] == strict for i in range(len(values)))
        target_modes = [i for i, v in enumerate(values) if v == 'swiftLanguageMode']
        weaker = any(values[i:i + 5] != ['swiftLanguageMode', '(', '.', 'v6', ')'] for i in target_modes)
        if not header or int(header[1]) < 6 or not package_mode or weaker or 'swiftLanguageVersions' in values:
            add('SWIFT002', p, text)
    source_hashes = inventory(root, source)
    emitted_hashes = {}
    modules = set()
    for prefix, base in [('spm', generated), ('bridge', bridge)]:
        for p in generated_files(base):
            rel = p.relative_to(base)
            emitted_hashes[prefix + '/' + str(rel)] = sha(p)
            if p.suffix == '.swift' and 'Sources' in rel.parts:
                i = rel.parts.index('Sources')
                if i + 1 < len(rel.parts):
                    modules.add(rel.parts[i + 1])
    errors = []
    if evidence is not None:
        try:
            data = json.loads(evidence.read_text())
            if data.get('schema_version') != 1 or data.get('source_sha256') != source_hashes:
                errors.append('Missing/stale exact source inventory')
            if not emitted_hashes or not generated or not bridge or data.get('generated_sha256') != emitted_hashes:
                errors.append('Missing/stale generated Swift AND Kotlin bridge inventory')
            generation = data.get('generation_receipt', {})
            run_id = generation.get('run_id')
            if (not run_id or generation.get('exit_code') != 0
                    or generation.get('source_sha256') != source_hashes
                    or generation.get('generated_sha256') != emitted_hashes
                    or not any('SwiftExport' in v or 'swiftExport' in v for v in generation.get('argv', []))):
                errors.append('Fresh exact generation receipt missing or failed')
            if not generation.get('log_path') or sha(Path(generation['log_path'])) != generation.get('log_sha256'):
                errors.append('Missing/stale generation command log')
            tools = data.get('toolchain_files', [])
            if not tools or any(sha(Path(v['path'])) != v['sha256'] for v in tools):
                errors.append('Missing/stale exact toolchain files')
            strict_modules = set()
            for record in data.get('frontend_logs', []):
                p = Path(record['path'])
                if record.get('run_id') != run_id or record.get('exit_code') != 0:
                    errors.append('Failed/stale compiler or runtime run receipt: ' + str(p))
                if sha(p) != record['sha256']:
                    errors.append('Stale frontend log: ' + str(p))
                    continue
                for line in p.read_text(errors='replace').splitlines():
                    if 'swift-frontend' not in line and '/swiftc ' not in line:
                        continue
                    args = shlex.split(line)
                    if '-swift-version' in args and args[args.index('-swift-version') + 1] == '6' and '-warnings-as-errors' in args and '-module-name' in args:
                        strict_modules.add(args[args.index('-module-name') + 1])
            consumer = data.get('consumer_module')
            if not modules or not consumer or not modules.union({consumer}).issubset(strict_modules):
                errors.append('Actual strict frontend commands missing for generated dependencies or consumer')
            passed = set()
            for record in data.get('test_results_xml', []):
                p = Path(record['path'])
                if record.get('run_id') != run_id or record.get('exit_code') != 0:
                    errors.append('Failed/stale compiler or runtime run receipt: ' + str(p))
                if sha(p) != record['sha256']:
                    errors.append('Stale runtime XML: ' + str(p))
                    continue
                cases = list(ET.parse(p).getroot().iter('testcase'))
                if not cases:
                    errors.append('No actual named consumer tests: ' + str(p))
                for case in cases:
                    if any(case.find(v) is not None for v in ['failure', 'error', 'skipped']):
                        errors.append('Failed/errored/skipped consumer test: ' + case.get('name', '?'))
                    else:
                        passed.add(case.get('classname', '') + '.' + case.get('name', ''))
            if not required_tests or not set(required_tests).issubset(passed):
                errors.append('Caller-required actual named behavior tests missing')
        except (OSError, ValueError, KeyError, IndexError, ET.ParseError) as exc:
            errors.append('Unsupported/incomplete acceptance receipt: ' + str(exc))
    else:
        errors.append('No compiler/runtime evidence supplied')
    return {'schema_version': 1, 'recipe_version': registry['recipe_version'], 'findings': findings,
            'source_sha256': source_hashes, 'generated_sha256': emitted_hashes, 'generated_swift_modules': sorted(modules),
            'acceptance': {'status': 'evidence-consistent-scoped-consumer' if not errors else 'unverified', 'errors': errors,
                           'limit': 'No all-target, full-library, publication, or toolchain adoption acceptance follows from this scan.'},
            'required_actions': registry['acceptance_contract']}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--root', type=Path, required=True)
    p.add_argument('--generated-root', type=Path)
    p.add_argument('--bridge-root', type=Path)
    p.add_argument('--evidence', type=Path)
    p.add_argument('--required-test', action='append', default=[])
    p.add_argument('--require-acceptance', action='store_true')
    p.add_argument('--output', type=Path)
    args = p.parse_args()
    result = scan(args.root.resolve(), args.generated_root, args.bridge_root, args.evidence, args.required_test)
    text = json.dumps(result, indent=2) + '\n'
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text)
    else:
        print(text, end='')
    bad = any(v['severity'] == 'error' for v in result['findings'])
    return 1 if bad or (args.require_acceptance and result['acceptance']['status'] == 'unverified') else 0


if __name__ == '__main__':
    raise SystemExit(main())
