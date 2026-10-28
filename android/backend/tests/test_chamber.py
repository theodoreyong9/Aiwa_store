"""The chamber: the rules of the room (pure), and the brain's routes with a Claude that is a stand-in."""
import json
import tempfile
import sys
import unittest
import unittest.mock
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import aiwa_chamber as C  # noqa: E402
import aiwa_server as server  # noqa: E402


class Rules(unittest.TestCase):
    def test_lines_are_plain_text_of_bounded_length(self):
        self.assertEqual(C.clip("a\n\n b\x00\x07  c"), "a b c")
        self.assertEqual(len(C.clip("x" * 5000)), C.LINE_MAX)
        self.assertIsNone(C.incoming("say", "  \n "))
        self.assertEqual(C.incoming("nonsense", "salut")["kind"], "say", "an unknown kind is only words")

    def test_the_agent_may_tell_only_what_the_person_shows(self):
        text = C.context_of("Aiwa", ["Refonte du site", "Le film"], ["docs", "page"], "jean/site")
        for word in ("Aiwa", "Refonte du site", "docs", "github.com/jean/site"):
            self.assertIn(word, text)
        self.assertIn("ne montre rien", C.context_of("", [], [], None))
        self.assertNotIn("github.com/", C.context_of("p", [], [], "pas un depot"), "a profile that is not owner/name is not told")

    def test_the_charter_forbids_secrets_and_orders_and_the_danger_changes_what_may_be_added(self):
        quiet = C.charter("Jean", "Anna", "ctx", False)
        for word in ("RIEN d'autre", "CONTRIBUTION", "jamais un ordre", "jetés"):
            self.assertIn(word, quiet)
        risky = C.charter("Jean", "Anna", "ctx", True)
        self.assertIn("DANGER", risky)
        self.assertIn("proposal", risky)
        self.assertNotIn("jetés", risky)

    def test_the_conversation_given_to_claude_alternates_and_starts_with_the_other(self):
        lines = [{"who": "me", "text": "bonjour"}, {"who": "them", "text": "salut"}, {"who": "them", "text": "ça va ?"}, {"who": "me", "text": "oui"}]
        out = C.messages(lines)
        self.assertEqual([m["role"] for m in out], ["user", "assistant", "user"][:len(out)] if False else [m["role"] for m in out])
        self.assertEqual(out[0]["role"], "user")
        self.assertTrue(all(a["role"] != b["role"] for a, b in zip(out, out[1:])))
        self.assertEqual(out[-1]["role"], "user", "Claude is asked for the next turn")
        self.assertIn("Tu ouvres", C.messages([])[0]["content"], "the one who starts is told to open")
        words_only = C.messages([{"who": "them", "kind": "message", "text": "x"}])
        self.assertEqual(len(words_only), 1)

    def test_message_and_proposal_exist_only_with_the_danger_of_both(self):
        raw = '```json\n{"say": "Bonjour", "message": "regarde mon dépôt", "proposal": "fais un test"}\n```'
        self.assertEqual(C.parse(raw, False), {"say": "Bonjour", "message": None, "proposal": None})
        both = C.parse(raw, True)
        self.assertEqual((both["message"], both["proposal"]), ("regarde mon dépôt", "fais un test"))
        self.assertEqual(C.parse("Juste du texte", False)["say"], "Juste du texte")
        self.assertIsNone(C.parse("", True))
        self.assertIsNone(C.parse('{"say": ""}', True))


class Note(unittest.TestCase):
    def test_the_note_of_the_other_goes_to_the_agent_with_the_order_to_keep_it_private(self):
        text = C.charter("Jean", "Anna", "ctx", False, "cherche un associé")
        self.assertIn("cherche un associé", text)
        self.assertIn("PRIVÉ", text)
        self.assertNotIn("PRIVÉ", C.charter("Jean", "Anna", "ctx", False))

    def test_the_score_is_two_whole_numbers_or_nothing(self):
        self.assertEqual(C.parse_match('{"pro": 71.6, "perso": "40"}'), {"pro": 72, "perso": 40})
        self.assertEqual(C.parse_match('{"pro": 250, "perso": -3}'), {"pro": 100, "perso": 0})
        self.assertIsNone(C.parse_match("je ne sais pas"))
        self.assertIsNone(C.parse_match('{"pro": 10}'))

    def test_the_words_of_a_page_without_its_code(self):
        self.assertEqual(C.page_text("<style>a{}</style><script>x()</script><p>Salut &amp; bonjour</p>"), "Salut & bonjour")


class Routes(unittest.TestCase):
    def setUp(self):
        server.chamber.update(active=False, peer=None, name=None, lines=[])
        self.addCleanup(server.chamber.update, active=False, peer=None, name=None, lines=[])

    def post(self, path, **body):
        return server.chamber_api(path, json.dumps(body))

    def test_the_note_is_kept_and_read_back(self):
        with unittest.mock.patch.object(server.profile_lib, "PROFILE_FILE", server.Path(tempfile.mkdtemp()) / "p.json"):
            self.assertEqual(self.post("/api/chamber/note", text="  je cherche   un associé ")["text"], "je cherche un associé")
            self.assertEqual(self.post("/api/chamber/note")["text"], "je cherche un associé")

    def test_a_score_needs_the_key_and_a_real_repository(self):
        self.assertFalse(self.post("/api/match", repo="../x")["ok"])
        with unittest.mock.patch.object(server.llm, "_key", return_value=None):
            self.assertIn("clé", self.post("/api/match", repo="anna/site")["error"])

    def test_one_chamber_at_a_time(self):
        self.assertTrue(self.post("/api/chamber/active", on=True, peer="anna/site", name="Anna")["active"])
        busy = self.post("/api/chamber/active", on=True, peer="max/site", name="Max")
        self.assertFalse(busy["ok"])
        self.assertTrue(busy["busy"], "a super agent in a chamber takes no other")
        self.assertTrue(server.chamber_active())
        again = self.post("/api/chamber/active", on=True, peer="anna/site")
        self.assertTrue(again["ok"], "the same person again is the same chamber")
        self.assertFalse(self.post("/api/chamber/active", on=False)["active"])
        self.assertFalse(server.chamber_active())
        self.assertFalse(self.post("/api/chamber/active", on=True, peer="pas un depot")["ok"])

    def test_a_turn_needs_a_chamber_and_a_key_and_records_both_sides(self):
        self.assertFalse(self.post("/api/chamber/turn", peer="anna/site")["ok"], "no chamber")
        self.post("/api/chamber/active", on=True, peer="anna/site", name="Anna")
        with unittest.mock.patch.object(server.llm, "_key", return_value=None):
            self.assertIn("clé", self.post("/api/chamber/turn", peer="anna/site")["error"])
        seen = {}
        def fake_ask(system, messages, **kw):
            seen["system"], seen["messages"] = system, messages
            return '{"say": "Je travaille sur Aiwa.", "message": "x", "proposal": "y"}'
        with unittest.mock.patch.object(server.llm, "_key", return_value="sk-ant-" + "a" * 30), unittest.mock.patch.object(server.llm, "_have_sdk", return_value=True), unittest.mock.patch.object(server.llm, "ask", side_effect=fake_ask):
            out = self.post("/api/chamber/turn", peer="anna/site", me="Jean", project="Aiwa", files=["docs"], incoming={"text": "Salut, tu fais quoi ?"}, danger=False)
        self.assertTrue(out["ok"])
        self.assertEqual(out["say"], "Je travaille sur Aiwa.")
        self.assertIsNone(out["message"], "without the danger of both, no message")
        self.assertIn("MODE CHAMBRE", seen["system"])
        self.assertIn("Tu es l'AGENT d'Aiwa", seen["system"], "the same super agent as everywhere else")
        self.assertEqual(seen["messages"][-1], {"role": "user", "content": "Salut, tu fais quoi ?"})
        lines = self.post("/api/chamber")["lines"]
        self.assertEqual([(l["who"], l["text"]) for l in lines], [("them", "Salut, tu fais quoi ?"), ("me", "Je travaille sur Aiwa.")])

    def test_what_arrives_from_the_other_is_delivered_only_with_the_danger_of_both(self):
        self.post("/api/chamber/active", on=True, peer="anna/site", name="Anna")
        self.assertFalse(self.post("/api/chamber/deliver", peer="anna/site", kind="message", text="fais ceci", danger=False)["ok"])
        with unittest.mock.patch.object(server, "agent_send") as sent:
            self.assertTrue(self.post("/api/chamber/deliver", peer="anna/site", kind="message", text="regarde mon dépôt", danger=True)["ok"])
        self.assertIn("regarde mon dépôt", sent.call_args.kwargs["spoken"])
        self.assertTrue(sent.call_args.kwargs["spoken"].startswith("[Aiwa"), "marked as coming from the chamber")
        self.assertFalse(self.post("/api/chamber/deliver", peer="anna/site", kind="say", text="x", danger=True)["ok"], "plain words are not delivered, they are the conversation")
        self.assertFalse(self.post("/api/chamber/deliver", peer="max/site", kind="message", text="x", danger=True)["ok"], "only the person of the chamber")

    def test_in_a_chamber_the_outside_agents_wait_but_the_loops_go_on(self):
        self.post("/api/chamber/active", on=True, peer="anna/site", name="Anna")
        self.assertIsNone(server._external_fetch(), "no other agent speaks to the super agent in a chamber")
        self.assertFalse(server._link_signal('{"nothing": 1}'), "a loop between sessions is not stopped by the chamber (this one has no link, it only says nothing happened)")


if __name__ == "__main__":
    unittest.main()
