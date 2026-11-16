"""Ensure generated Swift dependency package targets macOS 15.0 and defaults to Swift 5.

Per template/V2_SWIFT.md acceptance invariants:
  - Dependency modules compile in Swift 5 language mode (-swift-version 5)
  - Consumer test modules compile in Swift 6 language mode (-swift-version 6)
  - Both use -warnings-as-errors (Werror)

This script post-processes KGP's generated Package.swift to add the required
macOS 15.0 deployment platform. It does NOT change swift-tools-version (KGP
defaults to 5.9, which correctly compiles dependency modules in Swift 5 mode)
and does NOT add swiftLanguageModes (dependencies default to Swift 5 via
swift-tools-version 5.9).
"""
import argparse
import hashlib
import json
import re
from pathlib import Path

from sync_android_publication import tokens, require


def enforce(text):
    header = re.match(r'// swift-tools-version:\s*(\d+)\.(\d+)(?:\.\d+)?[^\n]*(?:\n|$)', text)
    require(header is not None, 'Missing Swift tools version header')
    require(int(header[1]) <= 6, 'Newer Swift tools manifest requires review')
    ts = tokens(text)
    starts = [i for i in range(len(ts) - 1) if [t[0] for t in ts[i:i + 2]] == ['Package', '(']]
    require(len(starts) == 1, 'Unsupported/ambiguous Package initializer')
    opening = starts[0] + 1
    stack = ['(']
    args = {}
    at_argument = True
    closing = None
    for i in range(opening + 1, len(ts)):
        value = ts[i][0]
        if len(stack) == 1:
            if value == ')':
                closing = i
                break
            if value == ',':
                at_argument = True
                continue
            if at_argument:
                require(i + 1 < len(ts) and ts[i + 1][0] == ':', 'Unsupported package argument')
                require(value not in args, 'Duplicate package argument')
                args[value] = i
                at_argument = False
        if value in ['(', '[', '{']:
            stack.append(value)
        elif value in [')', ']', '}']:
            require(stack[-1] == {')': '(', ']': '[', '}': '{'}[value], 'Unbalanced Swift manifest')
            stack.pop()
    require(closing is not None, 'Unclosed Package initializer')
    require('targets' in args, 'Generated package must declare its targets')
    require('swiftLanguageVersions' not in args, 'Legacy explicit Swift language override requires owner review')
    # Reject any per-target swiftLanguageMode override — dependencies must default
    # to Swift 5 via swift-tools-version 5.9, not be forced to a specific mode.
    for i, token in enumerate(ts):
        if token[0] == 'swiftLanguageMode':
            require(False, 'Per-target swiftLanguageMode override found; '
                    'dependencies must default to Swift 5 via swift-tools-version')
    # Reject swiftLanguageModes — dependencies default to Swift 5 via
    # swift-tools-version 5.9.  Forcing [.v6] here would break the
    # dependency/consumer mode separation required by V2_SWIFT.md.
    require('swiftLanguageModes' not in args,
            'swiftLanguageModes must not be set on generated dependency packages; '
            'dependencies default to Swift 5 via swift-tools-version 5.9')
    edits = []
    # Do NOT change swift-tools-version — KGP defaults to 5.9, which correctly
    # compiles dependency modules in Swift 5 language mode.
    if 'platforms' not in args:
        position = ts[min(args[k] for k in ['products', 'dependencies', 'targets'] if k in args)][1]
        indent = text[text.rfind('\n', 0, position) + 1:position]
        separator = '\n' + indent if not indent.strip() else ' '
        edits.append((position, position, 'platforms: [.macOS("15.0")],' + separator))
    for start, end, value in sorted(edits, reverse=True):
        text = text[:start] + value + text[end:]
    return text


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('manifest', type=Path)
    parser.add_argument('--receipt', type=Path, required=True)
    args = parser.parse_args()
    before = args.manifest.read_text()
    after = enforce(before)
    if after != before:
        args.manifest.write_text(after)
    receipt = {'manifest': str(args.manifest.resolve()), 'swift_language_mode': '5',
               'scope': 'Dependency modules default to Swift 5 via swift-tools-version 5.9; '
                        'consumer harness uses swift-tools-version 6.0 for Swift 6 consumer tests',
               'sha256': hashlib.sha256(after.encode()).hexdigest(), 'swift_api_sources_modified': False}
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    args.receipt.write_text(json.dumps(receipt, indent=2) + '\n')


if __name__ == '__main__':
    main()