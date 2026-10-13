import sys
import tempfile
import types
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import aiwa_server as srv  # noqa: E402


def fake(answers):
    def run(cmd, **kw):
        out, code = answers(cmd[3:])
        return types.SimpleNamespace(stdout=out, returncode=code)
    return run


class SelfUpdate(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        (self.root / ".git").mkdir()

    def test_outcomes(self):
        once = lambda f, root=None: srv._self_update_once(fake(f), root or self.root)
        self.assertEqual(once(lambda a: (" M x", 0) if a[0] == "status" else ("", 0)), "local changes")
        self.assertEqual(once(lambda a: ("", 1) if a[0] == "fetch" else ("", 0)), "offline")
        self.assertEqual(once(lambda a: ("", 0) if a[0] == "status" else ("abc", 0)), "current")
        self.assertEqual(once(lambda a: ("", 0) if a[0] in ("status", "fetch", "merge") else ("a" if a[-1] == "HEAD" else "b", 0)), "updated")
        self.assertEqual(once(lambda a: ("", 1) if a[0] == "merge" else ("", 0) if a[0] in ("status", "fetch") else ("a" if a[-1] == "HEAD" else "b", 0)), "cannot fast-forward")
        self.assertEqual(once(lambda a: ("", 0), self.root / "nowhere"), "not a git install")


if __name__ == "__main__":
    unittest.main()
