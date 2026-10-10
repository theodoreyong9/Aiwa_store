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
  git -C "$REPO_DIR" pull --ff-only
else
  [ -d "$REPO_DIR" ] && echo "== Replacing the install at $REPO_DIR (it now comes from Aiwa_store) ==" && rm -rf "$REPO_DIR"
  echo "== Installing into $REPO_DIR =="
  pkg install -y git
  git clone --depth 1 "$SOURCE" "$REPO_DIR"
fi

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

# The APK download is folded into this same one command (see install-apk.sh for what it checks and where it
# leaves the file: Downloads, for you to open from the Files app — the installer is NOT opened from here). What it says is kept in a file, because the rest of this script is long and ends
# with the server's own output: setup-termux-proot.sh prints that last line again right before it starts.
echo "== Downloading the latest Aiwa APK =="
bash "$REPO_DIR/android/backend/install-apk.sh" 2>&1 | tee "$HOME/.aiwa_apk_last.txt" || true

exec bash "$REPO_DIR/android/backend/setup-termux-proot.sh"
