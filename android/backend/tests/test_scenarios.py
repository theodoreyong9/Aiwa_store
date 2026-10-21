"""Scenarios: launch words, special words inside a scenario, the double's reply, and the brain's routes (no network, no real Claude)."""
import json
import sys
import tempfile
import unittest
import unittest.mock
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import aiwa_scenarios as sc  # noqa: E402
import aiwa_server as server  # noqa: E402

TMP = Path(tempfile.mkdtemp(prefix="aiwa-scen-"))


class ScenarioLogic(unittest.TestCase):
    def test_launch_word_matches_without_accents_and_longest_wins(self):
        items = [dict(d) for d in sc.DEFAULTS]
        self.assertEqual(sc.match("Lance le scénario entretien s'il te plaît", items)["id"], "entretien")
        self.assertEqual(sc.match("le rapport", items)["id"], "rapport")
        self.assertIsNone(sc.match("bonjour", items))

    def test_a_short_launch_word_is_refused(self):
        self.assertIsNone(sc.clean({"name": "x", "word": "oui", "prompt": "p"}))
        self.assertIsNone(sc.clean({"name": "x", "word": "lancer", "prompt": ""}))
        self.assertEqual(sc.clean({"name": "Cours", "word": "mode cours", "prompt": "p"})["id"], "cours")

    def test_special_words_are_tagged_for_the_double(self):
        s = sc.DEFAULTS[0]
        self.assertTrue(sc.user_turn(s, "Alors, scène").startswith("[mot spécial : scène]"))
        self.assertEqual(sc.user_turn(s, "il parle du budget"), "il parle du budget")
        self.assertIn("ta position", sc.system(s))

    def test_reply_is_json_or_a_silent_note(self):
        self.assertEqual(sc.parse_reply('ok {"say": "Voilà.", "note": "n"}'), {"say": "Voilà.", "note": "n"})
        self.assertEqual(sc.parse_reply("du texte libre")["say"], "")

    def test_same_launch_word_twice_is_refused(self):
        items, _s, err = sc.upsert([dict(d) for d in sc.DEFAULTS], {"name": "Autre", "word": "le rapport", "prompt": "p"})
        self.assertIn("déjà pris", err)


class ScenarioRoutes(unittest.TestCase):
    def setUp(self):
        server.scenario_sessions.clear()
        patch = unittest.mock.patch.object(server, "SCENARIOS_FILE", TMP / "scenarios.json")
        patch.start()
        self.addCleanup(patch.stop)
        (TMP / "scenarios.json").unlink(missing_ok=True)

    def call(self, path, **data):
        return server.scenario_api(path, json.dumps(data))

    def test_create_launch_once_then_talk(self):
        saved = self.call("/api/scenarios/save", name="Cours", word="mode cours", prompt="Résume le cours.", words=[{"word": "résumé", "does": "résume"}])
        self.assertTrue(saved["ok"])
        first = self.call("/api/scenarios/launch", text="lance mode cours")
        self.assertTrue(first["created"])
        self.assertFalse(self.call("/api/scenarios/launch", text="mode cours")["created"])
        with unittest.mock.patch.object(server, "_scenario_ask", return_value='{"say": "Noté.", "note": ""}') as ask:
            said = self.call("/api/scenarios/say", id="cours", text="un résumé")
        self.assertEqual(said["say"], "Noté.")
        self.assertTrue(ask.call_args[0][1][-1]["content"].startswith("[mot spécial : résumé]"))
        self.assertEqual(self.call("/api/scenarios/end", id="cours")["running"], [])

    def test_say_without_launch_is_refused(self):
        self.call("/api/scenarios/save", name="Cours", word="mode cours", prompt="p")
        self.assertFalse(self.call("/api/scenarios/say", id="cours", text="a")["ok"])

    def test_report_asks_the_super_agent(self):
        with unittest.mock.patch.object(server, "agent_send", return_value={"ok": True}) as send:
            result = self.call("/api/scenarios/launch", text="le rapport")
        self.assertTrue(result["ok"])
        self.assertEqual(send.call_args.kwargs["spoken"], server.agent_lib.REPORT_REQUEST)


if __name__ == "__main__":
    unittest.main()
