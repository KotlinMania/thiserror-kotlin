"""Read-only Cargo workspace declaration audit. Python 3.11+; no Cargo/Gradle/network.

This inventories requirements and reviewed evidence. It never establishes semantic
port parity from catalog aliases, test counts, or a successful source scan.
"""
import argparse
import fnmatch
import hashlib
import json
import posixpath
import re
from pathlib import Path

try:
    import tomllib
except ModuleNotFoundError:
    raise SystemExit('scan_rust_parity.py requires an existing Python 3.11+ interpreter')

KINDS = ('dependencies', 'build-dependencies', 'dev-dependencies')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def normalized(path):
    path = posixpath.normpath(path)
    if path.startswith('/') or path == '..' or path.startswith('../') or '\\' in path:
        raise ValueError(f'Path outside indexed upstream repository: {path}')
    return path


def matches(pattern, path):
    """Cargo-style path-component glob; * does not consume a slash."""
    a, b = pattern.split('/'), path.split('/')
    def consume(i, j):
        if i == len(a):
            return j == len(b)
        if a[i] == '**':
            return any(consume(i + 1, k) for k in range(j, len(b) + 1))
        return j < len(b) and fnmatch.fnmatchcase(b[j], a[i]) and consume(i + 1, j + 1)
    return consume(0, 0)


def tables(data):
    for kind in KINDS:
        for alias, value in data.get(kind, {}).items():
            yield kind, '*', alias, value
    for target, sections in data.get('target', {}).items():
        for kind in KINDS:
            for alias, value in sections.get(kind, {}).items():
                yield kind, target, alias, value


def audit(index_path, kotlin_root, mapping=None):
    index = json.loads(Path(index_path).read_text())
    if not re.fullmatch('[0-9a-f]{40}', index.get('revision', '')):
        raise ValueError('Pin the exact upstream revision')
    findings = []
    def gap(code, key, detail):
        findings.append({'code': code, 'key': key, 'detail': detail})
    manifests, proofs = {}, {}
    for row in index['manifests']:
        name = normalized(row['upstream_path'])
        if name in manifests:
            raise ValueError(f'Duplicate manifest: {name}')
        content = Path(row['local_path']).read_bytes()
        if digest(content) != row['sha256']:
            raise ValueError(f'Stale manifest hash: {name}')
        blob = hashlib.sha1(b'blob ' + str(len(content)).encode() + b'\0' + content).hexdigest()
        if row.get('git_blob_sha1') != blob:
            raise ValueError(f'Unverified Git blob: {name}')
        manifests[name] = tomllib.loads(content.decode())
        proofs[name] = row
    root_name = normalized(index.get('root_manifest', 'Cargo.toml'))
    root = manifests[root_name]
    base = posixpath.dirname(root_name)
    workspace = root.get('workspace', {})
    tree_paths = set()
    if index.get('tree'):
        tree_row = index['tree']
        content = Path(tree_row['local_path']).read_bytes()
        if digest(content) != tree_row['sha256'] or tree_row['revision'] != index['revision']:
            raise ValueError('Stale upstream tree inventory')
        tree = json.loads(content)
        if tree.get('truncated') is not False:
            raise ValueError('Truncated tree cannot establish workspace coverage')
        tree_blobs = {x['path']: x for x in tree['tree'] if x['type'] == 'blob'}
        for name, row in proofs.items():
            if tree_blobs.get(name, {}).get('sha') != row['git_blob_sha1']:
                raise ValueError(f'Manifest/tree blob mismatch: {name}')
        tree_paths = set(tree_blobs)
    else:
        gap('SOURCE_TREE_UNVERIFIED', root_name, 'Automatic targets and omitted workspace manifests are not inventoried')
    excluded_patterns = [normalized(posixpath.join(base, x)) for x in workspace.get('exclude', [])]
    def excluded(name):
        directory = posixpath.dirname(name) or '.'
        return any(matches(pattern, directory) for pattern in excluded_patterns)
    members = {root_name} if 'package' in root else set()
    known_paths = tree_paths | set(manifests)
    for pattern in workspace.get('members', []):
        full = normalized(posixpath.join(base, pattern))
        selected = {p for p in known_paths if p.endswith('Cargo.toml') and matches(full, posixpath.dirname(p) or '.') and not excluded(p)}
        if not selected:
            gap('MISSING_MEMBER', full, 'Declared workspace member has no indexed Cargo.toml')
        members.update(selected)
    dependencies = []
    processed = set()
    while members - processed:
        name = sorted(members - processed)[0]
        processed.add(name)
        if name not in manifests:
            gap('MISSING_MANIFEST_BYTES', name, 'Retrieve this exact revision manifest; tree presence alone is insufficient')
            continue
        data = manifests[name]
        for kind, target, alias, value in tables(data):
            spec = {'version': value} if isinstance(value, str) else dict(value)
            origin = posixpath.dirname(name)
            uncertain_default = False
            if spec.pop('workspace', False):
                inherited = workspace.get('dependencies', {}).get(alias)
                if inherited is None:
                    gap('UNRESOLVED_INHERITANCE', name + ':' + alias, 'Missing workspace.dependencies entry')
                    continue
                inherited = {'version': inherited} if isinstance(inherited, str) else dict(inherited)
                features = sorted(set(inherited.get('features', []) + spec.get('features', [])))
                if inherited.get('optional'):
                    gap('UNSUPPORTED_INHERITANCE', name + ':' + alias, 'Workspace dependencies cannot be optional')
                if spec.get('default-features') is False and inherited.get('default-features', True):
                    gap('DEFAULT_FEATURE_OVERRIDE', name + ':' + alias, 'Cargo/toolchain/edition evidence required; do not assume default features disabled')
                    uncertain_default = True
                inherited.update({k: v for k, v in spec.items() if k != 'features'})
                spec = dict(inherited, features=features)
                origin = base
            key = f'{name}::{kind}::{target}::{alias}'
            edge = {'id': key, 'manifest': name, 'kind': kind, 'target_condition': target,
                    'alias': alias, 'package': spec.get('package', alias), 'specification': spec,
                    'features': spec.get('features', []), 'default_features': None if uncertain_default else spec.get('default-features', True),
                    'optional': spec.get('optional', False)}
            if 'path' in spec:
                path = normalized(posixpath.join(origin, spec['path'], 'Cargo.toml'))
                edge['path_manifest'] = path
                if path not in known_paths:
                    gap('MISSING_PATH_DEPENDENCY', key, path)
                elif not excluded(path) and (not base or path.startswith(base + '/')):
                    if 'workspace' in root:
                        members.add(path)
                    else:
                        gap('PATH_CRATE_GRAPH_UNVERIFIED', key, 'No explicit workspace: path dependency is not assumed to be a workspace member')
            dependencies.append(edge)
    features, targets, member_rows = [], [], []
    for name in sorted(processed & manifests.keys()):
        data = manifests[name]
        package = dict(data.get('package', {}))
        for key, value in package.items():
            if isinstance(value, dict) and value.get('workspace'):
                if key not in workspace.get('package', {}):
                    gap('UNRESOLVED_PACKAGE_INHERITANCE', name + ':' + key, 'Missing workspace.package value')
                else:
                    package[key] = workspace['package'][key]
        member_rows.append({'manifest': name, 'package': package, 'sha256': proofs[name]['sha256']})
        for feature, value in data.get('features', {}).items():
            features.append({'id': name + '::' + feature, 'manifest': name, 'name': feature, 'enables': value})
        directory = posixpath.dirname(name)
        for kind, folder, auto in [('test', 'tests', 'autotests'), ('bench', 'benches', 'autobenches'), ('example', 'examples', 'autoexamples'), ('bin', 'src/bin', 'autobins')]:
            selected = {x['name']: dict(x, discovered='explicit') for x in data.get(kind, [])}
            prefix = posixpath.join(directory, folder) + '/'
            main_source = posixpath.join(directory, 'src/main.rs')
            if kind == 'bin' and package.get(auto, True) and main_source in tree_paths:
                selected.setdefault(package['name'], {'name': package['name'], 'path': 'src/main.rs', 'discovered': 'automatic'})
            if package.get(auto, True):
                for source in sorted(tree_paths):
                    if source.startswith(prefix):
                        relative = source[len(prefix):]
                        if '/' not in relative and relative.endswith('.rs'):
                            stem = relative[:-3]
                            selected.setdefault(stem, {'name': stem, 'path': posixpath.join(folder, relative), 'discovered': 'automatic'})
                        elif relative.endswith('/main.rs') and relative.count('/') == 1:
                            stem = relative.split('/')[0]
                            selected.setdefault(stem, {'name': stem, 'path': posixpath.join(folder, relative), 'discovered': 'automatic'})
            for target_name, target in selected.items():
                source = normalized(posixpath.join(directory, target.get('path', f'{folder}/{target_name}.rs')))
                targets.append(dict(target, id=f'{name}::{kind}::{target_name}', manifest=name, kind=kind,
                                    source=source, source_in_tree=source in tree_paths))
                if tree_paths and source not in tree_paths:
                    gap('TARGET_SOURCE_MISSING', name + ':' + target_name, source)
        for kind, default, enabled in [('lib', 'src/lib.rs', package.get('autolib', True)), ('build', 'build.rs', package.get('build') is not False)]:
            config = data.get('lib', {}) if kind == 'lib' else {}
            if kind == 'lib' and config:
                enabled = True
            if not enabled:
                continue
            source = normalized(posixpath.join(directory, config.get('path', default) if kind == 'lib' else package.get('build', default)))
            if enabled and (source in tree_paths or (kind == 'lib' and config)):
                targets.append({'id': f'{name}::{kind}', 'manifest': name, 'kind': kind, 'source': source, 'source_in_tree': source in tree_paths, 'test': config.get('test', True) if kind == 'lib' else False})
    for edge in dependencies:
        key = edge['manifest'] + '::' + edge['alias']
        existing = [x for x in features if x['manifest'] == edge['manifest']]
        explicit_dep = any('dep:' + edge['alias'] in x['enables'] for x in existing)
        if edge['optional'] and not explicit_dep and not any(x['id'] == key for x in existing):
            features.append({'id': key, 'manifest': edge['manifest'], 'name': edge['alias'], 'enables': ['dep:' + edge['alias']], 'implicit_optional_dependency': True})
    overrides = {key: root[key] for key in ['patch', 'replace'] if key in root}
    if overrides:
        gap('CARGO_OVERRIDE_GRAPH_UNVERIFIED', root_name, 'Patch/replace affects actual selection; retain Cargo resolver evidence')
    excluded_rows = [{'manifest': p, 'reason': 'workspace.exclude' if excluded(p) else 'outside selected member/path closure; scope review required'} for p in sorted(known_paths) if p.endswith('Cargo.toml') and p not in processed and p != root_name]
    mapping = mapping or {}
    for group, rows in [('members', member_rows), ('dependencies', dependencies), ('features', features), ('targets', targets)]:
        for row in rows:
            key = row.get('id', row.get('manifest'))
            review = mapping.get(group, {}).get(key)
            row['review'] = review or {'status': 'unmapped'}
            if not review or review.get('status') not in ['implemented', 'language-specific', 'upstream-release-tooling'] or not review.get('rationale'):
                gap('UNMAPPED_' + group.upper(), key, 'Explicit behavior/tooling/feature mapping and justified differences required')
            elif not review.get('evidence'):
                gap('REVIEW_EVIDENCE_MISSING', key, 'Classification alone does not establish the mapping')
            else:
                for evidence in review['evidence']:
                    path = Path(evidence['path'])
                    if not path.is_file() or digest(path.read_bytes()) != evidence['sha256']:
                        gap('STALE_REVIEW_EVIDENCE', key, str(path))
    for row in excluded_rows:
        if row['reason'] != 'workspace.exclude' and not mapping.get('excluded', {}).get(row['manifest'], {}).get('rationale'):
            gap('UNREVIEWED_EXCLUDED_MANIFEST', row['manifest'], row['reason'])
    catalog = Path(kotlin_root) / 'gradle/libs.versions.toml'
    kotlin_catalog = tomllib.loads(catalog.read_text()) if catalog.exists() else None
    return {'schema_version': 1, 'upstream': {'repository': index.get('repository'), 'revision': index['revision'], 'root_manifest': root_name},
            'workspace_context': workspace, 'manifest_proofs': list(proofs.values()), 'members': member_rows, 'excluded_manifests': excluded_rows,
            'dependencies': dependencies, 'features': features, 'targets': targets, 'dependency_overrides': overrides, 'findings': findings,
            'kotlin_catalog': {'path': str(catalog), 'sha256': digest(catalog.read_bytes()) if catalog.exists() else None, 'declarations': kotlin_catalog},
            'declaration_mapping_status': 'incomplete' if findings else 'classified',
            'actual_runtime_test_tooling_graph': 'unverified; catalog declarations are not resolved dependency evidence',
            'published_sibling_capabilities': 'unverified; require actual variant/API and consumer evidence',
            'ast_function_mapping': 'unverified; require established ast_distance binary/command/input hashes and reviewed missing/divergent functions; score alone is insufficient',
            'resolved_feature_graph': 'unverified; these are declarations, not Cargo resolver output or transitive feature unification',
            'port_acceptance': 'unverified; source classification and counts never establish semantic parity or runtime acceptance'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest-index', required=True, type=Path)
    parser.add_argument('--kotlin-root', required=True, type=Path)
    parser.add_argument('--mapping', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--strict', action='store_true', help='Fail on missing/incomplete declaration mappings; does not grant port acceptance')
    args = parser.parse_args()
    result = audit(args.manifest_index, args.kotlin_root, json.loads(args.mapping.read_text()) if args.mapping else None)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'mapping': result['declaration_mapping_status'], 'findings': len(result['findings']), 'members': len(result['members']), 'targets': len(result['targets']), 'port_acceptance': result['port_acceptance']}))
    return int(args.strict and bool(result['findings']))


if __name__ == '__main__':
    raise SystemExit(main())
