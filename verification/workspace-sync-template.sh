#!/bin/bash
# Deliberately limited to reviewed Android publication hunks; no blanket copies.
set -euo pipefail
workspace="$(cd "$(dirname "$0")/.." && pwd)"
exec "${KOTLINMANIA_PYTHON:-python3}" "$workspace/template/verification/sync_android_publication.py" "$@"
