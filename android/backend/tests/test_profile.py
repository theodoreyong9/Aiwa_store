import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import aiwa_agent as A
import aiwa_profile as P


class FakeGithub:
    class GithubError(Exception):
        pass

    def __init__(self):
        self.files = {}

    def put_file(self, repo, path, raw, message):
        self.files[(repo, path)] = raw
        return path


class Profile(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.old = P.PROFILE_FILE
        P.PROFILE_FILE = Path(self.tmp.name) / "profile.json"

    def tearDown(self):
        P.PROFILE_FILE = self.old
        self.tmp.cleanup()

    def test_the_background_is_a_shipped_one_or_an_address_of_a_picture(self):
        self.assertEqual(P.background_key("les néons"), None, "a whole sentence is not a key: the agent writes the key")
        self.assertEqual(P.background_key("Néons"), "neon")
        self.assertEqual(P.background_key("ciel étoilé"), "ciel")
        self.assertEqual(P.background_key("https://exemple.org/fond.mp4"), "https://exemple.org/fond.mp4")
        self.assertIsNone(P.background_key("javascript:alert(1)"))
        for _, (file, _) in P.BACKGROUNDS.items():
            self.assertTrue((P.BG_DIR / file).is_file(), file)

    def test_contacts_become_safe_links_only(self):
        self.assertEqual(P.contact_link("mail", "moi@exemple.org"), "mailto:moi@exemple.org")
        self.assertEqual(P.contact_link("whatsapp", "+33 6 12 34 56 78"), "https://wa.me/33612345678")
        self.assertEqual(P.contact_link("telegram", "@jean_x"), "https://t.me/jean_x")
        self.assertIsNone(P.contact_link("mail", "pas un mail"))
        self.assertIsNone(P.contact_link("telegram", "x\"><script>"))

    def test_a_patch_keeps_what_is_known_and_merges_the_contacts(self):
        P.update({"nom": "Jean", "texte": "Service client", "fond": "neon", "repo": "jean/site", "contacts": {"mail": "j@x.fr", "whatsapp": "+33612345678"}, "inconnu": 1})
        profile = P.update({"contacts": {"mail": ""}, "fond": "pas un fond"})
        self.assertEqual(profile["nom"], "Jean")
        self.assertEqual(profile["fond"], "neon", "an unknown background is dropped, the old one stays")
        self.assertEqual(list(profile["contacts"]), ["whatsapp"], "an empty contact removes it")
        self.assertNotIn("inconnu", profile)

    def test_the_page_is_escaped_and_carries_the_files_and_the_contacts(self):
        page = P.render({"nom": "<b>Jean</b>", "texte": "a & b", "fond": "ville", "contacts": {"mail": "j@x.fr"}, "liens": [{"kind": "page", "url": "https://jean.github.io/site/"}]})
        self.assertNotIn("<b>Jean</b>", page)
        self.assertIn("&lt;b&gt;Jean&lt;/b&gt;", page)
        self.assertIn("bg/ville.jpg", page)
        self.assertIn('href="mailto:j@x.fr"', page)
        self.assertIn("https://jean.github.io/site/", page)
        self.assertNotIn("<script", page)
        video = P.render({"fond": "https://exemple.org/fond.mp4"})
        self.assertIn("<video", video)

    def test_publish_writes_the_page_and_the_background_in_the_repository_chosen(self):
        github = FakeGithub()
        self.assertFalse(P.publish(github, {"nom": "x"})["ok"], "no repository, nothing is written")
        done = P.publish(github, {"nom": "Jean", "repo": "jean/site", "fond": "atelier"})
        self.assertTrue(done["ok"])
        self.assertEqual(done["url"], "https://jean.github.io/site/Profile/")
        self.assertEqual(sorted(path for _, path in github.files), ["Profile/bg/atelier.jpg", "Profile/index.html"])
        self.assertEqual(P.page_url("jean/jean.github.io"), "https://jean.github.io/Profile/")

    def test_the_agent_may_change_the_look_but_never_the_repository(self):
        command = A.parse_command({"profile": {"fond": "neon", "texte": "Bonjour", "repo": "autre/depot", "liens": [{"kind": "page", "url": "https://x.org/"}], "contacts": {"mail": "j@x.fr"}}}, [])
        self.assertEqual(command, {"profile": {"fond": "neon", "texte": "Bonjour", "contacts": {"mail": "j@x.fr"}}})
        spoken, _ = A.command_phrases(command)
        self.assertIn("les néons", spoken)
        self.assertIn("Mail", spoken)
        self.assertIsNone(A.parse_command({"profile": {"fond": "rien du tout"}}, []))


if __name__ == "__main__":
    unittest.main()
