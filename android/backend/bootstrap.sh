#!/data/data/com.termux/files/usr/bin/bash
# The ONE command a user ever needs to run in Termux, for both the
# first install and every later update — from wherever they happen to
# be, e.g. wherever Termux resumed their last session in:
#   curl -fsSL https://raw.githubusercontent.com/theodoreyong9/Aiwa_store/main/android/backend/bootstrap.sh | bash
#
# Always installs/updates to the SAME fixed location ($HOME/aiwa_store)
# regardless of the current directory, instead of depending on the user
# remembering where they last cloned it — running `git clone` again
# from inside an existing clone (an easy mistake when Termux resumes in
# whatever directory you left it in) used to produce a nested
# aiwa_store/aiwa_store mess.
set -euo pipefail

REPO_DIR="$HOME/aiwa_store"

# What a phone installs comes from this repository. An install made from another address is replaced by a fresh one (its data lives in $HOME).
SOURCE="${AIWA_SOURCE:-https://github.com/theodoreyong9/Aiwa_store}"
if [ -d "$REPO_DIR/.git" ] && [ "$(git -C "$REPO_DIR" remote get-url origin 2>/dev/null)" = "$SOURCE" ]; then
  echo "== Updating the existing install at $REPO_DIR =="
  BEFORE="$(git -C "$REPO_DIR" rev-parse --short HEAD 2>/dev/null || echo none)"
  if ! git -C "$REPO_DIR" fetch origin main; then
    echo "!! Could not reach GitHub: nothing was downloaded. Check the network and run this command again."
    exit 1
  fi
  if ! git -C "$REPO_DIR" merge --ff-only FETCH_HEAD; then
    echo "!! The install at $REPO_DIR has changes of its own and cannot be moved forward."
    echo "!! To start over from GitHub (your data lives in \$HOME, not there):  rm -rf $REPO_DIR  and run this command again."
    exit 1
  fi
  AFTER="$(git -C "$REPO_DIR" rev-parse --short HEAD)"
  if [ "$BEFORE" = "$AFTER" ]; then
    echo "== Already on the latest version: $AFTER (nothing new on GitHub) =="
  else
    echo "== NEW VERSION DOWNLOADED: $BEFORE -> $AFTER ($(git -C "$REPO_DIR" rev-list --count "$BEFORE..$AFTER" 2>/dev/null || echo '?') changes) =="
  fi
else
  [ -d "$REPO_DIR" ] && echo "== Replacing the install at $REPO_DIR (it now comes from Aiwa_store) ==" && rm -rf "$REPO_DIR"
  echo "== Installing into $REPO_DIR =="
  pkg install -y git
  git clone --depth 1 "$SOURCE" "$REPO_DIR"
  echo "== VERSION DOWNLOADED: $(git -C "$REPO_DIR" rev-parse --short HEAD) =="
fi
echo "== Brain code on disk: version $(sed -n 's/^BACKEND_VERSION = \([0-9]*\).*/\1/p' "$REPO_DIR/android/backend/aiwa_server.py") (commit $(git -C "$REPO_DIR" rev-parse --short HEAD)) =="

# The widget/app's "▶ Démarrer" button needs Termux's own separate
# opt-in for RUN_COMMAND (reported live: "Need allow external apps in
# termux properties files") — a real security gate of Termux's own,
# off by default, that no manifest permission on Aiwa's side can grant
# on its own. Folding it in here means it's set the same one time as
# everything else instead of a manual edit the user has to remember.
echo "== Allowing Aiwa to trigger Termux commands (RUN_COMMAND) =="
mkdir -p "$HOME/.termux"
PROPS="$HOME/.termux/termux.properties"
if ! grep -q "^allow-external-apps *= *true" "$PROPS" 2>/dev/null; then
  echo "allow-external-apps=true" >> "$PROPS"
  termux-reload-settings || true
fi

# The latest APK is downloaded too (install-apk.sh): to Downloads/Aiwa_store.apk, replaced each time, checked before it is kept. It is NOT opened from here:
# opening a file from Termux offers Termux itself ("save in ~/downloads"). The Files app opens it with Android's installer; inside Aiwa, « Mettre à jour
# l'application » does the same without any file. What it said is kept, and repeated at the end of the setup, where it would have scrolled away.
echo "== Downloading the latest Aiwa APK =="
bash "$REPO_DIR/android/backend/install-apk.sh" 2>&1 | tee "$HOME/.aiwa_apk_last.txt" || true

exec bash "$REPO_DIR/android/backend/setup-termux-proot.sh"
