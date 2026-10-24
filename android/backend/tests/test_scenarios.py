"""Scenarios: launch words, special words inside a scenario, the double's reply, and the brain's routes (no network, no real Claude)."""
import json
import time
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
    def test_the_application_scenario_is_a_default_and_is_offered_once_to_an_old_list(self):
        ids = [d["id"] for d in sc.DEFAULTS]
        self.assertIn("candidature", ids)
        cand = next(d for d in sc.DEFAULTS if d["id"] == "candidature")
        self.assertIsNotNone(sc.clean(cand))
        self.assertEqual(sc.match("lance le scénario candidature", [dict(d) for d in sc.DEFAULTS])["id"], "candidature")
        path = TMP / "old.json"
        sc.save(path, [d for d in sc.DEFAULTS if d["id"] != "candidature"])
        self.assertIn("candidature", [x["id"] for x in sc.load(path)])
        sc.save(path, [x for x in sc.load(path) if x["id"] != "candidature"])
        self.assertNotIn("candidature", [x["id"] for x in sc.load(path)], "deleted by the person: not offered again")

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
        self.assertIn("[mot spécial : scène]", sc.user_turn(s, "Alors, scène"))
        self.assertNotIn("mot spécial", sc.user_turn(s, "il parle du budget"))

    def test_the_charter_gives_the_prompt_the_words_and_the_reply_command(self):
        text = sc.charter(sc.DEFAULTS[0], "https://ntfy.sh/x")
        self.assertIn("ta position", text)
        self.assertIn("Title: aiwa-scenario", text)
        self.assertIn('"scenario":"entretien"', text)
        self.assertIn("Tu ne parles à voix haute que quand je dis", text)

    def test_same_launch_word_twice_is_refused(self):
        items, _s, err = sc.upsert([dict(d) for d in sc.DEFAULTS], {"name": "Autre", "word": "le rapport", "prompt": "p"})
        self.assertIn("déjà pris", err)


def scen_lib_charter():
    return sc.charter(sc.DEFAULTS[0], "https://ntfy.sh/x")


class ScenarioRoutes(unittest.TestCase):
    def setUp(self):
        server.scenario_run.clear()
        for name in ("SCENARIOS_FILE", "SCEN_SESSIONS_FILE"):
            patch = unittest.mock.patch.object(server, name, TMP / (name + ".json"))
            patch.start()
            self.addCleanup(patch.stop)
            (TMP / (name + ".json")).unlink(missing_ok=True)

    def call(self, path, **data):
        return server.scenario_api(path, json.dumps(data))

    def test_launch_makes_the_session_once_and_the_phone_talks_to_it(self):
        self.assertTrue(self.call("/api/scenarios/save", name="Cours", word="mode cours", prompt="Résume le cours.", words=[{"word": "résumé", "does": "résume"}])["ok"])
        sent = []
        with unittest.mock.patch.object(server, "cloud_send", return_value={"ok": True, "session_id": "session_ABC"}) as cs, \
                unittest.mock.patch.object(server, "_queue_followup", side_effect=lambda s, t: sent.append((s, t)) or {"ok": True}):
            first = self.call("/api/scenarios/launch", text="lance mode cours")
            self.assertTrue(first["created"])
            for _ in range(100):
                if server._scen_sessions().get("cours", {}).get("current"):
                    break
                time.sleep(0.02)
            self.assertEqual(server._scen_sessions()["cours"]["current"], "session_ABC")
            self.assertIn("Title: aiwa-scenario", cs.call_args[0][0])
            self.assertFalse(self.call("/api/scenarios/launch", id="cours")["created"])
            self.assertEqual(cs.call_count, 1)
            self.assertTrue(self.call("/api/scenarios/say", id="cours", text="un résumé")["ok"])
            for _ in range(100):
                if sent:
                    break
                time.sleep(0.02)
        self.assertEqual(sent[0][0], "session_ABC")
        self.assertIn("[mot spécial : résumé]", sent[0][1])

    def test_a_scenario_can_propose_a_message_for_another_session_like_the_super_agent(self):
        self.call("/api/scenarios/save", name="Cours", word="mode cours", prompt="p")
        with unittest.mock.patch.object(server, "_agent_said", return_value=True) as said:
            ok = server._scenario_said(json.dumps({"scenario": "cours", "say": "", "note": "", "send": [{"session": "session_ABC", "message": "Fais le compte rendu"}]}))
        self.assertTrue(ok)
        self.assertIn("session_ABC", said.call_args[0][0])
        self.assertIn("Title: aiwa-scenario", scen_lib_charter())
        self.assertIn('"send"', scen_lib_charter())

    def test_what_a_scenario_sends_goes_at_once_and_what_it_does_is_marked_for_the_phone(self):
        self.call("/api/scenarios/save", name="Cours", word="mode cours", prompt="p")

        def fake_said(raw):
            server.agent["proposal"] = {"id": "p1", "session": "session_ABC", "message": "x"}
            server.agent["command"] = {"id": "c1", "items": {"open": "documents"}}
            return True
        confirmed = []
        with unittest.mock.patch.object(server, "_agent_said", side_effect=fake_said), unittest.mock.patch.object(server, "agent_confirm", side_effect=lambda i: confirmed.append(i) or {"ok": True}):
            server._scenario_said(json.dumps({"scenario": "cours", "say": "ok", "send": [{"session": "session_ABC", "message": "x"}], "do": {"open": "documents"}}))
            for _ in range(100):
                if confirmed:
                    break
                time.sleep(0.02)
        self.assertEqual(confirmed, ["p1"])
        self.assertTrue(server.agent["command"]["auto"])
        self.assertTrue(self.call("/api/scenarios/said", id="cours")["says"][0]["do"])
        server.agent["proposal"] = server.agent["command"] = None
        self.assertIn('"cast"', scen_lib_charter())
        self.assertIn('"connector"', scen_lib_charter())

    def test_say_while_the_session_is_being_made_waits(self):
        self.call("/api/scenarios/save", name="Cours", word="mode cours", prompt="p")
        self.assertTrue(self.call("/api/scenarios/say", id="cours", text="a")["pending"])

    def test_what_the_session_says_through_the_relay_is_taken_once(self):
        self.call("/api/scenarios/save", name="Cours", word="mode cours", prompt="p")
        self.assertTrue(server._scenario_said(json.dumps({"scenario": "cours", "say": "Voilà.", "note": "n"})))
        self.assertFalse(server._scenario_said(json.dumps({"scenario": "inconnu", "say": "x"})))
        got = self.call("/api/scenarios/said", id="cours")
        self.assertEqual(got["says"][0]["say"], "Voilà.")
        self.assertEqual(self.call("/api/scenarios/said", id="cours")["says"], [])

    def test_report_asks_the_super_agent(self):
        with unittest.mock.patch.object(server, "agent_send", return_value={"ok": True}) as send:
            result = self.call("/api/scenarios/launch", text="le rapport")
        self.assertTrue(result["ok"])
        self.assertEqual(send.call_args.kwargs["spoken"], server.agent_lib.REPORT_REQUEST)


if __name__ == "__main__":
    unittest.main()
