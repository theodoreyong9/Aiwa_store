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

    def test_a_profile_is_the_showcase_with_no_limit_of_technique_and_a_short_minimum(self):
        text = P.instruction("jean/site")
        for word in ("VITRINE", "10 secondes", "AUCUNE limite technique", "UN SEUL écran", "sans défilement", "360×640", "Three.js", "consigne Design", "390 px", "aiwa-design/references.md", "page de référence", "jean/site"):
            self.assertIn(word, text)
        good = '<!doctype html><title>x</title><meta name="viewport" content="width=device-width"><h1>x</h1><video src="media/a.mp4"></video><a href="https://github.com/x">x</a>'
        self.assertEqual(P.check(good), [])
        heavy = good + '<script src="https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.module.js"></script><script>fetch("https://x.org/a.json")</script>'
        self.assertEqual(P.check(heavy), [], "a library from a CDN and a call to the network are the maker's choice")
        bad = P.check('<html><a href="http://x.org/">x</a></html>')
        self.assertGreaterEqual(len(bad), 4)
        self.assertTrue(any("http://" in b for b in bad))

    def test_the_profile_of_this_repository_reaches_the_minimum(self):
        root = Path(__file__).resolve().parents[3] / "Profile"
        media = {f"media/{f.name}": f.stat().st_size for f in (root / "media").glob("*") if f.is_file()}
        self.assertEqual(P.check((root / "index.html").read_text(encoding="utf-8")), [])

    def test_the_super_agent_has_no_order_to_write_the_profile(self):
        self.assertIsNone(A.parse_command({"profile": {"fond": "neon", "texte": "x"}}, []), "the profile is the work of a session, not an order")


if __name__ == "__main__":
    unittest.main()
