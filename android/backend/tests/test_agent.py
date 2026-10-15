import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import aiwa_agent as A  # noqa: E402


class Agent(unittest.TestCase):
    def test_charter_holds_the_fixed_rules(self):
        text = A.charter("http://relay/topic")
        for word in ("DONNÉE", "Aiwa-agent".lower() if False else "aiwa-agent", "vas-y, dis-lui", "Tu ne modifies aucun code", "http://relay/topic"):
            self.assertIn(word, text)

    def test_digest_signature_ignores_the_passing_minutes_but_not_real_changes(self):
        base = [{"id": "session_A", "title": "Site", "repo": "o/r", "work": "aiwa/x", "commit_age_min": 3, "ci": "success"}]
        t1, s1 = A.digest(base, [], [], current_id="session_A")
        t2, s2 = A.digest([{**base[0], "commit_age_min": 5}], [], [])
        self.assertEqual(s1, s2, "two minutes later is not news")
        _, s3 = A.digest([{**base[0], "ci": "failure"}], [], [])
        self.assertNotEqual(s1, s3)
        _, s4 = A.digest(base, [], [{"session": "Site", "question": "Quelle couleur ?"}])
        self.assertNotEqual(s1, s4)
        self.assertIn("session en cours d'utilisation", t1)
        self.assertIn("[session_A]", t1)

    def test_a_reply_is_read_and_a_proposal_must_name_a_known_session_that_is_not_the_agent(self):
        known = {"session_A", "session_AGENT"}
        ok = A.parse_message(json.dumps({"say": "Le site est en échec.", "send": {"session": "session_A", "message": "regarde le workflow"}}), known, "session_AGENT")
        self.assertEqual(ok["proposal"], {"session": "session_A", "message": "regarde le workflow", "more": []})
        unknown = A.parse_message(json.dumps({"say": "x", "send": {"session": "session_Z", "message": "m"}}), known, "session_AGENT")
        self.assertIsNone(unknown["proposal"])
        self_send = A.parse_message(json.dumps({"say": "x", "send": {"session": "session_AGENT", "message": "m"}}), known, "session_AGENT")
        self.assertIsNone(self_send["proposal"])
        self.assertIsNone(A.parse_message("pas du json", known))
        self.assertIsNone(A.parse_message(json.dumps({"say": ""}), known))
        long = A.parse_message(json.dumps({"say": "a" * 5000}), known)
        self.assertEqual(len(long["say"]), A.SAY_MAX)

    def test_the_point_carries_what_the_person_said_and_how_to_answer(self):
        message = A.point_message("Sessions :\n- x", "http://relay/t", spoken="où en est-on ?", stamp="09:30")
        self.assertIn("[Point Aiwa 09:30]", message)
        self.assertIn("où en est-on ?", message)
        self.assertIn("Title: aiwa-agent", message)
        self.assertEqual(A.clamp_interval(1), A.INTERVAL_MIN)
        self.assertEqual(A.clamp_interval("x"), A.INTERVAL_DEFAULT)



class OpenProductionTests(unittest.TestCase):
    def test_open_is_a_known_kind_or_nothing(self):
        cmd = A.parse_command({"open": "Vidéo"}, set())
        self.assertEqual(cmd, {"open": "video"})
        self.assertEqual(A.parse_command({"open": "page"}, set()), {"open": "pages"})
        self.assertIsNone(A.parse_command({"open": "rm -rf"}, set()))
        spoken, _ = A.command_phrases({"open": "video"})
        self.assertIn("ouvrir les vidéos", spoken)

if __name__ == "__main__":
    unittest.main()


class CommandTest(unittest.TestCase):
    def test_only_known_keys_and_plain_values_become_a_command(self):
        known = {"session_WORK", "session_AGENT"}
        raw = {"say": "ok", "do": {"model": "opus", "push": "Main", "deploy": "pages", "evil": "x", "repo": "a;rm -rf", "session": "session_WORK", "effort": ""}}
        parsed = A.parse_message(raw, known, "session_AGENT")
        self.assertEqual(parsed["command"], {"session": "session_WORK"}, "model, effort, push and deploy are no longer spoken settings")
        self.assertIsNone(A.parse_command({"session": "session_AGENT"}, known, "session_AGENT"), "never the agent itself")
        self.assertIsNone(A.parse_command({"session": "session_UNKNOWN", "push": "force"}, known))
        self.assertEqual(A.parse_command({"session": "nouvelle"}, known), {"session": "nouvelle"})
        self.assertIsNone(A.parse_command("opus", known))

    def test_the_cast_is_a_command_of_its_own_and_is_not_in_the_instruction(self):
        cast = A.parse_command({"cast": {"tv": "Salon", "wait": True}}, set())
        self.assertEqual(cast, {"cast": {"tv": "Salon", "wait": True}})
        spoken, instruction = A.command_phrases(cast)
        self.assertEqual(spoken, "J'applique : la prochaine vidéo sur la télévision « Salon ».")
        self.assertEqual(instruction, "", "the phone does the cast itself, its parser has nothing to read")
        self.assertEqual(A.parse_command({"cast": "salon"}, set())["cast"], {"tv": "salon", "wait": False})
        self.assertIsNone(A.parse_command({"cast": {"tv": "a;b"}}, set()))
        both = A.command_phrases({"repo": "test", "cast": {"tv": "", "wait": False}})
        self.assertEqual(both, ("J'applique : dépôt test, la dernière vidéo sur la télévision.", "instruction aiwa dépôt test"))

    def test_several_sessions_can_be_proposed_at_once_and_are_all_read_back(self):
        known = {"session_A", "session_B", "session_AGENT"}
        raw = {"say": "x", "send": [{"session": "session_A", "message": "ajoute un test"}, {"session": "session_B", "message": "regarde le workflow"}, {"session": "session_A", "message": "double"}]}
        proposal = A.parse_message(raw, known, "session_AGENT")["proposal"]
        self.assertEqual(proposal["session"], "session_A")
        self.assertEqual(proposal["more"], [{"session": "session_B", "message": "regarde le workflow"}], "the same session twice counts once")
        said = A.read_back_all([("Site", "ajoute un test"), ("App", "regarde le workflow")])
        self.assertTrue(said.startswith("J'envoie à 2 sessions."))
        self.assertIn("À la session App : regarde le workflow.", said)
        self.assertEqual(A.read_back_all([("Site", "m")]), "J'envoie à la session Site : m.")

    def test_the_agent_is_an_intermediary_and_knows_the_vocabulary_message(self):
        charter = A.charter("http://relay/t")
        for wanted in ("INTERMÉDIAIRE", "JAMAIS rien toi-même", "cast", "corpus", "résumé"):
            self.assertIn(wanted, charter)
        self.assertIn("INTERMÉDIAIRE", A.direct_system(set()))
        text = A.vocabulary_message({"Réveil": ["mon agent"], "Stop": ["stop claude", "arrête claude"], "Vide": []})
        self.assertIn("Réveil : mon agent", text)
        self.assertIn("Stop : stop claude / arrête claude", text)
        self.assertNotIn("Vide", text)

    def test_a_command_alone_is_a_reply_and_is_read_as_an_instruction(self):
        parsed = A.parse_message({"do": {"repo": "test", "session": "nouvelle"}}, set())
        self.assertEqual(parsed["say"], "")
        spoken, instruction = A.command_phrases(parsed["command"])
        self.assertEqual(spoken, "J'applique : nouvelle session, dépôt test.")
        self.assertEqual(instruction, "instruction aiwa nouvelle session dépôt test")
