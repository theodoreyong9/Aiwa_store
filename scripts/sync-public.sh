#!/usr/bin/env bash
# Copies what has to be readable without an account into the public repository (theodoreyong9/public): the standards a Claude session
# reads, the tools it runs (design research and its catalogue, the video kit), the Termux backend and its install scripts, the papers and
# the promotional video. What the public repository keeps of its own (access.json, its README) is never touched.
#   scripts/sync-public.sh <directory of a checkout of the public repository>
set -euo pipefail
DEST="${1:?the directory of the public repository}"
SRC="$(cd "$(dirname "$0")/.." && pwd)"
MANAGED=(docs design-research video-kit android/backend LICENSE)
for item in "${MANAGED[@]}"; do rm -rf "${DEST:?}/$item"; done
mkdir -p "$DEST/android"
for item in "${MANAGED[@]}"; do
  [ -e "$SRC/$item" ] || continue
  mkdir -p "$DEST/$(dirname "$item")"
  tar -C "$SRC" --exclude='node_modules' --exclude='.research' --exclude='__pycache__' --exclude='*.pyc' -cf - "$item" | tar -C "$DEST" -xf -
done
echo "synced: ${MANAGED[*]}"
