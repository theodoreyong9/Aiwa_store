import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import aiwa_agent as A
import aiwa_profile as P


class Profile(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.old = P.PROFILE_FILE
        P.PROFILE_FILE = Path(self.tmp.name) / "profile.json"

    def tearDown(self):
        P.PROFILE_FILE = self.old
        self.tmp.cleanup()

    def test_the_repository_is_remembered_and_only_if_it_is_owner_slash_name(self):
        self.assertIsNone(P.repo())
        self.assertFalse(P.set_repo("pas un depot"))
        self.assertFalse(P.set_repo("a/b; rm -rf"))
        self.assertTrue(P.set_repo("jean/site"))
        self.assertEqual(P.repo(), "jean/site")

    def test_the_sessions_are_told_what_the_folder_is_and_which_repository(self):
        text = P.instruction("jean/site")
        self.assertIn("Profile/index.html", text)
        self.assertIn("jean/site", text)
        self.assertNotIn("jean/site", P.instruction())

    def test_the_minimum_of_quality_is_in_the_instruction_and_checked(self):
        text = P.instruction("jean/site")
        for word in ("nom", "viewport", "<script src>", "Mo", "page de référence", "consigne Design", "390 px", "aiwa-design/references.md"):
            self.assertIn(word, text)
        good = '<!doctype html><title>x</title><meta name="viewport" content="width=device-width"><h1>x</h1><video src="media/a.mp4"></video><a href="https://github.com/x">x</a>'
        self.assertEqual(P.check(good, {"media/a.mp4": 900_000}), [])
        bad = P.check('<html><script src="http://x.org/a.js"></script></html>', {"media/a.mp4": 9_000_000})
        self.assertGreaterEqual(len(bad), 6)
        self.assertTrue(any("script src" in b for b in bad) and any("http://" in b for b in bad) and any("Mo" in b for b in bad))
        self.assertEqual(P.check(good.replace("</video>", "</video><script>var a=1;</script>"), {}), [], "an inline script that only draws is allowed")
        self.assertTrue(any("réseau" in b for b in P.check(good + "<script>fetch('x')</script>", {})), "a script that calls the network is not")

    def test_the_profile_of_this_repository_reaches_the_minimum(self):
        root = Path(__file__).resolve().parents[3] / "Profile"
        media = {f"media/{f.name}": f.stat().st_size for f in (root / "media").glob("*") if f.is_file()}
        self.assertEqual(P.check((root / "index.html").read_text(encoding="utf-8"), media), [])

    def test_the_super_agent_has_no_order_to_write_the_profile(self):
        self.assertIsNone(A.parse_command({"profile": {"fond": "neon", "texte": "x"}}, []), "the profile is the work of a session, not an order")


if __name__ == "__main__":
    unittest.main()
