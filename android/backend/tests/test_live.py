"""« Création à la volée »: the cutting of the stream, the actions, the progress sentences, and a turn of the mode with a fake Claude."""
import json
import sys
import unittest
import unittest.mock
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import aiwa_live as live  # noqa: E402
import aiwa_server as server  # noqa: E402


class LiveLogic(unittest.TestCase):
    def test_sentences_come_as_they_are_finished_and_the_marker_cut_in_two_is_still_found(self):
        s = live.Splitter()
        out = []
        for piece in ["Je lance ça. Et je ", "te dis quand c'est prêt.\n@@AC", "TIONS {\"send\":[{\"session\":\"session_A\",\"message\":\"fais x\"}]}"]:
            out += s.feed(piece)
        last, actions = s.finish()
        self.assertEqual(out, ["Je lance ça.", "Et je te dis quand c'est prêt."])
        self.assertEqual(actions["send"][0]["session"], "session_A")
        self.assertNotIn("@@", " ".join(out + last))

    def test_a_reply_without_actions_is_only_said(self):
        s = live.Splitter()
        out = s.feed("D'accord, je regarde ça avec toi.")
        last, actions = s.finish()
        self.assertEqual(out + last, ["D'accord, je regarde ça avec toi."])
        self.assertEqual(actions, {})

    def test_bad_actions_are_ignored_and_never_raise(self):
        self.assertEqual(live.parse_actions("pas du json"), {})
        self.assertEqual(live.parse_actions('{"send":[{"session":"","message":"x"}]}'), {})

    def test_progress_says_the_new_commit_and_the_failed_workflow_but_nothing_on_the_first_look(self):
        self.assertEqual(live.progress({}, {"sha": "a", "ci": "success"}), [])
        out = live.progress({"sha": "a", "ci": "running"}, {"sha": "b", "message": "Ajoute la page", "ci": "failure"})
        self.assertEqual(out, ["Nouveau commit : Ajoute la page.", "Le workflow a échoué."])
        self.assertEqual(live.progress({"sha": "a", "ci": "success"}, {"sha": "a", "ci": "success"}), [])


class LiveTurn(unittest.TestCase):
    def test_a_turn_says_the_sentences_then_sends_what_it_was_asked_at_once(self):
        said, confirmed = [], []

        def fake_stream(prompt, on_piece, timeout=90):
            for piece in ["C'est parti. ", "Je le dis à la session.\n@@ACTIONS ", '{"send":[{"session":"session_ABC","message":"fais la page"}],"do":{"open":"documents"}}']:
                on_piece(piece)
            return "x"

        def fake_said(raw):
            server.agent["proposal"] = {"id": "p1", "session": "session_ABC", "message": "fais la page"}
            server.agent["command"] = {"id": "c1", "items": {"open": "documents"}}
            return True
        with unittest.mock.patch.object(server, "_claude_stream", fake_stream), unittest.mock.patch.object(server.llm, "_key", lambda: None), \
                unittest.mock.patch.object(server, "_agent_digest", lambda with_ci=False: ("Sessions : ...", "sig")), unittest.mock.patch.object(server, "_agent_said", fake_said), \
                unittest.mock.patch.object(server, "agent_confirm", lambda i: confirmed.append(i) or {"ok": True}):
            result = server.live_stream("fais-moi la page d'accueil", said.append)
        self.assertEqual(said, ["C'est parti.", "Je le dis à la session."])
        self.assertTrue(result["do"])
        for _ in range(100):
            if confirmed:
                break
            import time
            time.sleep(0.02)
        self.assertEqual(confirmed, ["p1"])
        server.agent["proposal"] = server.agent["command"] = None

    def test_the_mode_is_switched_and_its_events_are_taken_once(self):
        self.assertTrue(server.live_api("/api/live/set", json.dumps({"on": True}))["on"])
        server.live["events"].append("Nouveau commit.")
        self.assertEqual(server.live_api("/api/live/events", "")["events"], ["Nouveau commit."])
        self.assertEqual(server.live_api("/api/live/events", "")["events"], [])
        server.live_api("/api/live/set", json.dumps({"on": False}))


if __name__ == "__main__":
    unittest.main()
