"""Focused, opt-in Android publication sync. Plan by default; never scan/apply a fleet."""
import argparse
import difflib
import hashlib
import json
import re
import subprocess
from pathlib import Path

SOURCE = Path(__file__).resolve().parent.parent
MARKER = 'kotlinmania:android-publication:v1'
FILES = ['verification/verify_publication_pins.py', 'verification/verify_android_publication.py', 'verification/check_android_classpath.py']


def require(ok, message):
    if not ok:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def git(repo, *args):
    return subprocess.check_output(['git', '-C', str(repo), *args], text=True).strip()


def tokens(text):
    """Kotlin structural tokens with offsets; comments/strings never become edit anchors."""
    result = []
    i = 0
    while i < len(text):
        if text[i].isspace():
            i += 1
        elif text.startswith('//', i):
            end = text.find('\n', i)
            i = len(text) if end < 0 else end
        elif text.startswith('/*', i):
            depth = 1
            i += 2
            while depth and i < len(text):
                if text.startswith('/*', i):
                    depth += 1
                    i += 2
                elif text.startswith('*/', i):
                    depth -= 1
                    i += 2
                else:
                    i += 1
            require(depth == 0, 'Unterminated Kotlin comment')
        elif text.startswith('"""', i):
            end = text.find('"""', i + 3)
            require(end >= 0, 'Unterminated Kotlin raw string')
            i = end + 3
        elif text[i] in '\"\'':
            quote = text[i]
            i += 1
            while i < len(text) and text[i] != quote:
                i += 2 if text[i] == '\\' else 1
            require(i < len(text), 'Unterminated Kotlin string')
            i += 1
        else:
            match = re.match(r'[A-Za-z_][A-Za-z_0-9]*|.', text[i:])
            result.append((match[0], i, i + len(match[0])))
            i += len(match[0])
    return result


def validate_warning_policy(text):
    values = [t[0] for t in tokens(text)]
    patterns = [
        ['allWarningsAsErrors', '.', 'set', '(', 'false', ')'],
        ['allWarningsAsErrors', '=', 'false'],
    ]
    for pattern in patterns:
        require(not any(values[i:i + len(pattern)] == pattern for i in range(len(values))),
                'Warnings-as-errors relaxation requires source repair; sync refuses to preserve/reintroduce it')


def block(ts, pattern, start=0, end=None):
    end = len(ts) if end is None else end
    candidates = [i + len(pattern) - 1 for i in range(start, end - len(pattern) + 1)
                  if [t[0] for t in ts[i:i + len(pattern)]] == pattern]
    require(len(candidates) == 1, f'Unsupported/ambiguous Kotlin block: {pattern}')
    opening = candidates[0]
    depth = 1
    for i in range(opening + 1, end):
        depth += (ts[i][0] == '{') - (ts[i][0] == '}')
        if depth == 0:
            return opening, i
    raise ValueError('Unbalanced Kotlin block')


def transform(text, aliases, fragment, bundle_aliases=()):
    validate_warning_policy(text)
    validate_warning_policy(fragment)
    ts = tokens(text)
    ko, kc = block(ts, ['kotlin', '{'])
    block(ts, ['android', '{'], ko + 1, kc)
    so, sc = block(ts, ['sourceSets', '{'], ko + 1, kc)
    co, cc = block(ts, ['commonMain', '.', 'dependencies', '{'], so + 1, sc)
    changes = []
    for alias in aliases:
        require(re.fullmatch(r'libs\.[a-zA-Z0-9_.]+', alias), f'Unsupported catalog accessor: {alias}')
        middle = [t[0] for t in tokens(alias)]
        calls = []
        for i in range(co + 1, cc):
            if ts[i][0] in {'api', 'implementation'}:
                pattern = [ts[i][0], '('] + middle + [')']
                if [t[0] for t in ts[i:i + len(pattern)]] == pattern:
                    depth = sum((t[0] == '{') - (t[0] == '}') for t in ts[co + 1:i])
                    require(depth == 0 and ts[i + len(pattern)][0] != '{',
                            'Conditional/custom dependency override requires manual review')
                    calls.append(i)
        if not calls and alias in bundle_aliases:
            pattern = ['implementation', '(', 'commonMainDependencyBundle', ')']
            require(sum([t[0] for t in ts[i:i + len(pattern)]] == pattern for i in range(co + 1, cc)) == 1,
                    'Unsupported commonMain bundle call')
            changes.append((ts[cc][1], ts[cc][1], f'    api({alias})\n        '))
            continue
        require(len(calls) == 1, f'Expected one direct commonMain dependency for {alias}; preserve custom DSL')
        i = calls[0]
        if ts[i][0] == 'implementation':
            changes.append((ts[i][1], ts[i][2], 'api'))
    for start, end, value in sorted(changes, reverse=True):
        text = text[:start] + value + text[end:]
    begin = f'// {MARKER} begin'
    finish = f'// {MARKER} end'
    if begin in text:
        require(text.count(begin) == text.count(finish) == 1, 'Ambiguous publication guard markers')
        existing = text[text.index(begin):text.index(finish) + len(finish)]
        require(existing == fragment.strip(), 'Existing publication override differs; manual review required')
    else:
        words = {t[0] for t in tokens(text)}
        require(not {'verifyAndroidPublication', 'verifyPublicationPins'} & words, 'Existing project publication verifier: preserve and review manually')
        require({'publishing', 'publishToCentralPortal', 'centralPortalStaging'} <= words or
                ('centralPortalStaging' in text and {'publishing', 'publishToCentralPortal'} <= words),
                'Unsupported first-party staging/upload layout')
        require({'centralPortalBundle', 'kotlinVersion'} <= words,
                'Unsupported release bundle/Kotlin version layout; preserve project specialization')
        require('MavenPublication' in words and 'PublishToMavenRepository' in words,
                'Unsupported publication import layout')
        text = text.rstrip() + '\n\n' + fragment
    return text


def inventory(repo):
    paths = [p for p in (repo / '.github').rglob('*')
             if p.is_file() and p.suffix in {'.yaml', '.yml'}] if (repo / '.github').exists() else []
    require(not any(p.is_symlink() for p in paths), 'Symlink GitHub YAML is unsupported')
    return {str(p.relative_to(repo)): sha(p.read_bytes()) for p in sorted(paths)}


def plan(repo, module, aliases):
    repo = Path(repo).resolve()
    require(repo.parent == SOURCE.parent and (repo / '.git').is_dir(), 'Use an existing primary shared checkout')
    require(not Path(module).is_absolute() and '..' not in Path(module).parts, 'Invalid Gradle module path')
    branch = git(repo, 'branch', '--show-current')
    require(bool(branch), 'Detached checkout is unsupported')
    # Planning is safe for dirty/held checkouts. Application requires an explicit current grant and clean tree.
    build = repo / module / 'build.gradle.kts'
    require(build.resolve().is_relative_to(repo), 'Module escapes checkout')
    require(build.exists() and not build.is_symlink(), 'Unsupported/missing Kotlin build file')
    catalog = repo / 'gradle/libs.versions.toml'
    require(catalog.is_file(), 'Unsupported catalog layout')
    try:
        import tomllib
    except ImportError as error:
        raise ValueError('Python 3.11+ is required for code-aware version-catalog parsing') from error
    parsed = tomllib.loads(catalog.read_text())
    plugins = parsed.get('plugins', {})
    require(any(p.get('id') == 'com.android.kotlin.multiplatform.library' for p in plugins.values()),
            'Android KMP plugin missing; target migration requires owner review')
    # Verify that the module actually applies its declared catalog plugin (not apply false).
    active = False
    ts = tokens(build.read_text())
    po, pc = block(ts, ['plugins', '{'])
    for name, plugin in plugins.items():
        if plugin.get('id') != 'com.android.kotlin.multiplatform.library':
            continue
        accessor = 'libs.plugins.' + re.sub('[-_]', '.', name)
        pattern = ['alias', '('] + [t[0] for t in tokens(accessor)] + [')']
        for i in range(po + 1, pc):
            if [t[0] for t in ts[i:i + len(pattern)]] == pattern:
                require([t[0] for t in ts[i + len(pattern):i + len(pattern) + 2]] != ['apply', 'false'],
                        'Android plugin is apply false in selected module')
                active = True
    require(active, 'Unsupported Android plugin application')
    fragment = (SOURCE / 'verification/android-publication.gradle.kts.fragment').read_text()
    properties = dict(line.split('=', 1) for line in (repo / 'gradle.properties').read_text().splitlines()
                      if '=' in line and not line.lstrip().startswith('#'))
    bundle = parsed.get('bundles', {}).get(properties.get('project.dependencies.commonMainBundle', '').strip(), [])
    bundle_aliases = {'libs.' + re.sub('[-_]', '.', name) for name in bundle}
    if bundle_aliases:
        require('"project.dependencies.commonMainBundle"' in build.read_text(), 'Unsupported bundle property mapping')
    contents = {str(build.relative_to(repo)): transform(build.read_text(), aliases, fragment, bundle_aliases)}
    contents.update({name: (SOURCE / name).read_text() for name in FILES})
    changes = {}
    for name, after in contents.items():
        path = repo / name
        require(not path.is_symlink(), f'Symlink not supported: {name}')
        before = path.read_text() if path.exists() else ''
        if name != str(build.relative_to(repo)) and before:
            require(before == after, f'Existing project override: {name}')
        changes[name] = {'before': sha(before.encode()), 'after': sha(after.encode()), 'text': after,
                         'diff': ''.join(difflib.unified_diff(before.splitlines(True), after.splitlines(True),
                                                              fromfile='a/' + name, tofile='b/' + name))}
    inputs = {str(p.relative_to(repo)): sha(p.read_bytes()) for p in
              [catalog, repo / 'settings.gradle.kts', repo / 'gradle.properties'] if p.exists()}
    return {'repo': str(repo), 'head': git(repo, 'rev-parse', 'HEAD'), 'branch': branch,
            'module': module, 'api_aliases': sorted(set(aliases)), 'source_head': git(SOURCE, 'rev-parse', 'HEAD'),
            'source_hashes': {name: sha((SOURCE / name).read_bytes()) for name in FILES +
                              ['verification/android-publication.gradle.kts.fragment', 'verification/sync_android_publication.py']},
            'inputs': inputs, 'workflows': inventory(repo), 'changes': changes}


def apply(saved, grant):
    require(grant.get('repo') == saved['repo'] and grant.get('head') == saved['head'] and
            grant.get('source_write_granted') is True and bool(grant.get('owner')),
            'Explicit current repo/HEAD/owner source grant required; held projects cannot be applied')
    require(saved.get('branch') not in {'main', 'master', ''}, 'A preserved feature branch is required to apply')
    fresh = plan(saved['repo'], saved['module'], saved['api_aliases'])
    require(fresh == saved, 'Stale HEAD, source, hash, catalog or workflow plan; review a fresh diff')
    repo = Path(saved['repo'])
    require(not git(repo, 'status', '--porcelain'), 'Dirty checkout; preserve concurrent work')
    for name in saved['changes']:
        path = repo / name
        require(not any(p.is_symlink() for p in [path, *path.parents]), 'Symlink path is unsupported')
    for name, change in saved['changes'].items():
        path = repo / name
        require(not any(p.is_symlink() for p in [path, *path.parents] if p != repo.parent),
                'Symlink path is unsupported')
        if change['before'] != change['after']:
            # Exclusive creation avoids replacing a file that appeared after planning.
            if path.exists():
                require(sha(path.read_bytes()) == change['before'], 'Concurrent file modification')
                path.write_text(change['text'])
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                with path.open('x') as handle:
                    handle.write(change['text'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path)
    parser.add_argument('--module', default='.', help='Explicit root or child Gradle module directory')
    parser.add_argument('--api-alias', action='append', default=[])
    parser.add_argument('--plan', type=Path)
    parser.add_argument('--apply-plan', type=Path)
    parser.add_argument('--grant', type=Path)
    args = parser.parse_args()
    try:
        if args.apply_plan:
            require(args.grant is not None, '--grant is required to apply')
            apply(json.loads(args.apply_plan.read_text()), json.loads(args.grant.read_text()))
            print('Applied reviewed source plan; build/consumer/publication validation still required')
        else:
            require(args.repo is not None, 'Name one --repo; fleet sync is unsupported')
            result = plan(args.repo, args.module, args.api_alias)
            for change in result['changes'].values():
                print(change['diff'], end='')
            if args.plan:
                args.plan.parent.mkdir(parents=True, exist_ok=True)
                args.plan.write_text(json.dumps(result, indent=2) + '\n')
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        parser.exit(2, f'Unsupported or unsafe sync: {error}\n')


if __name__ == '__main__':
    main()
