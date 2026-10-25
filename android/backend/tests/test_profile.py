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

    def test_the_super_agent_has_no_order_to_write_the_profile(self):
        self.assertIsNone(A.parse_command({"profile": {"fond": "neon", "texte": "x"}}, []), "the profile is the work of a session, not an order")


if __name__ == "__main__":
    unittest.main()
