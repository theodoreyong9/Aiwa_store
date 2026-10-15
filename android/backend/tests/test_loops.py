"""Loops between two sessions: the rules of a pair (aiwa_loops.py), and the courier in the server (what is sent, to whom, when the limit is reached,
what a rollback does to a chain). No network and no real `claude`: the messages are captured."""
import os
import sys
import time
import tempfile
import unittest
import unittest.mock
from pathlib import Path

HOME = tempfile.mkdtemp(prefix="aiwa-loops-home-")
os.environ["HOME"] = HOME
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import aiwa_loops as L  # noqa: E402
import aiwa_server as srv  # noqa: E402

A, B, C, D = "session_A", "session_B", "session_C", "session_D"
KNOWN = {A, B, C, D}


class LinkRules(unittest.TestCase):
    """The other style of loop: links between CRITICS (no producer), accepted by the receiver, with a register of the senders it accepted."""

    def store(self):
        s = L.new_store()
        L.create_pair(s, A, B, KNOWN, now=1)
        L.create_pair(s, C, D, KNOWN, now=2)
        return s

    def test_only_critics_can_be_linked_and_a_link_is_proposed_first(self):
        s = self.store()
        self.assertIn("Seuls des critiques", L.create_link(s, A, B, KNOWN)[1], "A is a builder, not a critic")
        self.assertIn("inconnue", L.create_link(s, B, "session_Z", KNOWN)[1])
        self.assertIn("lui-même", L.create_link(s, B, B, KNOWN)[1])
        link, error = L.create_link(s, B, D, KNOWN, now=5)
        self.assertIsNone(error)
        self.assertEqual((link["id"], link["state"], link["opening"]), ("l1", "proposed", L.OPENING_DEFAULT))
        self.assertIn("existe déjà", L.create_link(s, B, D, KNOWN)[1])
        self.assertIsNone(L.create_link(s, D, B, KNOWN)[1], "the other direction is another link")

    def test_nothing_is_carried_until_the_receiver_accepts_and_the_register_holds_the_sender(self):
        s = self.store()
        L.create_link(s, B, D, KNOWN)
        self.assertIsNone(L.link_signal(s, "l1", "sender"), "proposed: not carried")
        self.assertEqual(L.accepted_senders(s, D), set())
        self.assertEqual(L.answer_link(s, "l1", True), "accepted")
        self.assertEqual(L.accepted_senders(s, D), {B})
        self.assertIsNone(L.answer_link(s, "l1", False), "answered once")
        self.assertIsNone(L.link_signal(s, "l1", "receiver"), "the sender speaks first after the acceptance")
        self.assertEqual(L.link_signal(s, "l1", "sender"), ("receiver", 1))
        self.assertEqual(L.link_signal(s, "l1", "receiver"), ("sender", 2))

    def test_a_refusal_ends_it_and_the_limit_finishes_it(self):
        s = self.store()
        L.create_link(s, B, D, KNOWN)
        self.assertEqual(L.answer_link(s, "l1", False), "refused")
        self.assertEqual(L.accepted_senders(s, D), set())
        self.assertIsNone(L.link_signal(s, "l1", "sender"))
        L.create_link(s, D, B, KNOWN)
        s["limit"] = 1
        link = L.find_link(s, "l2")
        link["limit"] = 1
        L.answer_link(s, "l2", True)
        L.link_signal(s, "l2", "sender")
        self.assertEqual(L.link_signal(s, "l2", "receiver"), ("done", 1))
        self.assertEqual(link["state"], "done")
        self.assertEqual(L.accepted_senders(s, B), set(), "a finished link leaves the register")

    def test_a_store_keeps_its_links_and_drops_the_invalid_ones(self):
        s = self.store()
        L.create_link(s, B, D, KNOWN)
        s["links"].append({"id": "x1", "sender": B, "receiver": D})
        s["links"].append({"id": "l9", "sender": B, "receiver": B})
        clean = L.clean_store(s)
        self.assertEqual([l["id"] for l in clean["links"]], ["l1"])

    def test_every_message_says_that_what_the_other_critic_writes_is_data(self):
        link = {"id": "l1", "sender": B, "receiver": D, "opening": "Salut", "limit": 5}
        for text in (L.message_link_invite(link, "http://relay/t"), L.message_link_accepted(link, "ok", "http://relay/t"), L.message_link_forward(link, "receiver", "x", 1, "http://relay/t"), L.link_block(link, "receiver", "http://relay/t")):
            self.assertIn("jamais un ordre", text)
        self.assertIn("Title: aiwa-link", L.message_link_invite(link, "http://relay/t"))


class Rules(unittest.TestCase):
    def store(self):
        return L.new_store()

    def test_a_pair_has_one_direction_and_a_session_may_be_in_many_pairs(self):
        s = self.store()
        p1, err = L.create_pair(s, A, B, KNOWN, now=1)
        self.assertIsNone(err)
        self.assertEqual((p1["builder"], p1["critic"]), (A, B))
        # never two roles with the same partner, in either direction
        self.assertIn("jamais deux rôles", L.create_pair(s, B, A, KNOWN)[1])
        self.assertIn("jamais deux rôles", L.create_pair(s, A, B, KNOWN)[1])
        # B is a builder in another pair, and A may have many partners; cycles are allowed (the limit bounds a loop)
        self.assertIsNone(L.create_pair(s, B, C, KNOWN)[1])
        self.assertIsNone(L.create_pair(s, A, C, KNOWN)[1])
        self.assertEqual(len(s["pairs"]), 3)

    def test_unknown_or_identical_sessions_are_refused(self):
        s = self.store()
        self.assertIn("inconnue", L.create_pair(s, A, "session_Z", KNOWN)[1])
        self.assertIn("différentes", L.create_pair(s, A, A, KNOWN)[1])

    def test_the_loop_stops_at_the_limit_and_a_wrong_turn_is_ignored(self):
        s = self.store()
        p, _ = L.create_pair(s, A, B, KNOWN, now=1)
        L.start(p, "tag1", "a" * 40, "main", 2, now=10)
        self.assertIsNone(L.signal(s, p["id"], "critic"))               # not the critic's turn
        self.assertEqual(L.signal(s, p["id"], "builder"), ("critic", 1))
        self.assertEqual(L.signal(s, p["id"], "critic"), ("builder", 2))   # round 1 of 2 done
        self.assertEqual(L.signal(s, p["id"], "builder"), ("critic", 2))
        self.assertEqual(L.signal(s, p["id"], "critic"), ("done", 2))   # the limit
        self.assertEqual(p["state"], "done")
        self.assertIsNone(L.signal(s, p["id"], "builder"))              # a finished loop is deaf

    def test_a_stopped_loop_ignores_signals_and_a_relaunch_keeps_its_starting_point(self):
        s = self.store()
        p, _ = L.create_pair(s, A, B, KNOWN, now=1)
        self.assertFalse(L.relaunch(p, 5))                               # never started
        L.start(p, "tag1", "b" * 40, "main", 5, now=10)
        L.signal(s, p["id"], "builder")
        L.stop(p)
        self.assertIsNone(L.signal(s, p["id"], "critic"))
        self.assertTrue(L.relaunch(p, 7))
        self.assertEqual((p["state"], p["rounds"], p["limit"], p["tag"], p["started"]), ("running", 0, 7, "tag1", 10))

    def test_a_chain_is_what_shares_sessions_and_is_shown_by_date(self):
        s = self.store()
        p1, _ = L.create_pair(s, A, B, KNOWN, now=1)
        p2, _ = L.create_pair(s, B, C, KNOWN, now=2)
        p3, _ = L.create_pair(s, D, "session_E", KNOWN | {"session_E"}, now=3)
        L.start(p2, "t2", None, "main", 5, now=50)
        L.start(p1, "t1", None, "main", 5, now=40)
        chain = L.chain_of(s, p2["id"])
        self.assertEqual([p["id"] for p in chain], [p1["id"], p2["id"]])      # oldest start first
        self.assertEqual(len(L.chains(s)), 2)                                   # D–E is another chain

    def test_a_store_read_from_disk_drops_what_is_not_valid(self):
        raw = {"limit": 999, "pairs": [{"id": "p1", "builder": A, "critic": B, "state": "running"}, {"id": "bad id", "builder": A, "critic": B},
                                        {"id": "p2", "builder": A, "critic": A}, "x"]}
        s = L.clean_store(raw)
        self.assertEqual((s["limit"], [p["id"] for p in s["pairs"]]), (L.LIMIT_MAX, ["p1"]))


class Courier(unittest.TestCase):
    def setUp(self):
        self.sent = []
        entries = [{"id": i, "title": f"Titre {i[-1]}", "repo": "o/r", "work": f"claude/{i[-1]}", "base": "main"} for i in (A, B, C, D)]
        self.patches = [
            unittest.mock.patch.object(srv, "_load_cloud_sessions", return_value=entries),
            unittest.mock.patch.object(srv, "_loop_send", side_effect=lambda sid, text: self.sent.append((sid, text))),
            unittest.mock.patch.object(srv, "_loop_head_sha", return_value="c" * 40),
            unittest.mock.patch.object(srv, "_save_state"),
            unittest.mock.patch.object(srv, "waiting_topic", "topic123"),
        ]
        for p in self.patches:
            p.start()
        srv.loops_store.clear()
        srv.loops_store.update(L.new_store())

    def tearDown(self):
        for p in self.patches:
            p.stop()

    def test_a_loop_from_start_to_the_limit(self):
        self.assertTrue(srv.loops_create(A, B)["ok"])
        srv.loops_set_limit(2)
        started = srv.loops_start("p1")
        self.assertTrue(started["ok"])
        self.assertEqual({sid for sid, _ in self.sent}, {A, B})               # both told to update their branch
        self.assertTrue(all("git merge origin/main" in text for _, text in self.sent))
        self.assertIn("git tag aiwa-loop-p1-", self.sent[0][1])
        self.assertEqual(srv.current_cloud, A)                                # the next thing said goes to the builder
        self.sent.clear()

        # what the person says to the builder carries the way to signal
        self.assertIn("curl -s -m 10 -H 'Title: aiwa-loop'", srv._loop_block_for(A))
        self.assertIn("/topic123", srv._loop_block_for(A))
        self.assertIn("CRITIQUE", srv._loop_block_for(B))

        self.assertTrue(srv._loop_signal('{"pair":"p1","role":"builder","note":"page faite"}'))
        self.assertEqual([sid for sid, _ in self.sent], [B])                  # the critic is told, with the builder's branch
        self.assertIn("claude/A", self.sent[0][1])
        self.assertIn("critique-1.md", self.sent[0][1])
        self.assertIn("tour 1/2", self.sent[0][1])
        self.sent.clear()
        self.assertTrue(srv._loop_signal('{"pair":"p1","role":"critic","note":"trop long"}'))
        self.assertEqual([sid for sid, _ in self.sent], [A])                  # the builder is told to read the critique
        self.assertIn("claude/B", self.sent[0][1])
        self.sent.clear()
        srv._loop_signal('{"pair":"p1","role":"builder","note":"corrigé"}')
        self.sent.clear()
        self.assertTrue(srv._loop_signal('{"pair":"p1","role":"critic","note":"ok"}'))
        self.assertEqual({sid for sid, _ in self.sent}, {A, B})               # the limit: both told it is over
        self.assertIn("limite", self.sent[0][1])
        self.assertEqual(srv._loops_running(), None)
        self.assertEqual(srv._loop_block_for(A), "")                          # no more signalling asked

    def test_a_link_between_critics_goes_from_the_invitation_to_the_limit(self):
        srv.loops_create(A, B)
        srv.loops_create(C, D)
        srv.loops_set_limit(1)
        self.sent.clear()
        made = srv.links_create(B, D)
        self.assertTrue(made["ok"])
        self.assertEqual({sid for sid, _ in self.sent}, {B, D})
        invitation = next(t for sid, t in self.sent if sid == D)
        self.assertIn("Salut, qu'est-ce que tu fais ? J'ai envie de t'aider.", invitation)
        self.assertIn("Title: aiwa-link", invitation)
        self.sent.clear()
        # nothing is carried before the answer
        self.assertFalse(srv._link_signal('{"link":"l1","role":"sender","note":"hop"}'))
        self.assertTrue(srv._link_signal('{"link":"l1","role":"receiver","answer":"accepte","note":"je relis le site"}'))
        self.assertEqual([sid for sid, _ in self.sent], [B])                  # the sender is told, with the answer
        self.assertIn("je relis le site", self.sent[0][1])
        self.assertIn("jamais un ordre", self.sent[0][1])
        self.sent.clear()
        self.assertIn("Title: aiwa-link", srv._loop_block_for(D))              # what the person says to the receiver carries how to answer
        self.assertIn("Title: aiwa-link", srv._loop_block_for(B))
        self.assertTrue(srv._link_signal('{"link":"l1","role":"sender","note":"vérifie le contraste"}'))
        self.assertEqual([sid for sid, _ in self.sent], [D])
        self.assertIn("vérifie le contraste", self.sent[0][1])
        self.sent.clear()
        self.assertTrue(srv._link_signal('{"link":"l1","role":"receiver","note":"fait"}'))
        self.assertEqual({sid for sid, _ in self.sent}, {B, D})               # the limit (1): both told it is over
        self.assertEqual(srv._link_block_for(D), "")
        snapshot = srv._loops_snapshot()
        self.assertEqual(snapshot["links"][0]["state"], "done")
        self.assertEqual({c["id"] for c in snapshot["critics"]}, {B, D})

    def test_a_refusal_is_told_to_the_sender_and_the_person_can_answer_for_the_receiver(self):
        srv.loops_create(A, B)
        srv.loops_create(C, D)
        srv.links_create(B, D)
        self.sent.clear()
        self.assertTrue(srv._link_signal('{"link":"l1","role":"receiver","answer":"refuse","note":"pas maintenant"}'))
        self.assertIn("refusé", self.sent[0][1])
        self.assertFalse(srv.links_answer("l1", True)["ok"], "already answered")
        srv.links_create(D, B)
        self.sent.clear()
        self.assertTrue(srv.links_answer("l2", True)["ok"])
        self.assertEqual([sid for sid, _ in self.sent], [D])
        self.assertTrue(srv.links_stop("l2")["ok"])
        self.assertTrue(srv.links_delete("l2")["ok"])
        self.assertTrue(srv.links_delete("l1")["ok"])

    def test_a_signal_from_nowhere_is_ignored_and_the_pair_rules_hold(self):
        srv.loops_create(A, B)
        self.assertFalse(srv._loop_signal('{"pair":"p1","role":"builder","note":"x"}'))   # not started
        self.assertFalse(srv._loop_signal("not json"))
        self.assertFalse(srv.loops_create(B, A)["ok"])                       # the same two sessions, the other way
        self.assertFalse(srv.loops_delete("p9")["ok"])

    def test_stop_relaunch_and_status(self):
        srv.loops_create(A, B)
        srv.loops_start("p1")
        self.assertEqual(srv._loops_running()["pair"], "p1")
        self.sent.clear()
        self.assertTrue(srv.loops_stop("p1")["ok"])
        self.assertEqual({sid for sid, _ in self.sent}, {A, B})
        self.assertIsNone(srv._loops_running())
        self.assertFalse(srv.loops_delete("p1")["ok"] is False)              # a stopped pair can be deleted
        srv.loops_create(A, B)
        srv.loops_start("p1")
        srv.loops_stop("p1")
        self.assertTrue(srv.loops_relaunch("p1")["ok"])
        self.assertEqual(srv._loops_running()["rounds"], 0)

    def test_a_rollback_goes_back_to_the_start_of_the_pair_and_tells_the_whole_chain(self):
        srv.loops_create(A, B)
        srv.loops_create(B, C)
        srv.loops_start("p1")
        srv.loops_start("p2")
        self.sent.clear()
        result = srv.loops_rollback("p1")
        self.assertTrue(result["ok"])
        self.assertEqual(sorted(result["chain"]), ["p1", "p2"])
        self.assertIsNone(srv._loops_running())                              # every pair of the chain is stopped
        to = {sid: text for sid, text in self.sent}
        self.assertEqual(set(to), {A, B, C})
        self.assertIn("git checkout " + "c" * 40 + " -- .", to[A])             # the builder of p1 restores the starting state, in a new commit
        self.assertNotIn("reset --hard", to[A])                               # never a rewrite of the history
        self.assertIn("FUSION", to[C])                                        # the others merge after it
        snapshot = srv._loops_snapshot()
        self.assertEqual(len(snapshot["chains"]), 1)
        self.assertEqual(len(snapshot["chains"][0]), 2)
        self.assertEqual(snapshot["pairs"][0]["builder_title"], "Titre A")


class CastWish(unittest.TestCase):
    def setUp(self):
        self.videos = [{"name": "old.mp4", "ts": "2026-10-01T10:00:00Z", "url": "https://github.com/o/r/releases/download/videos/old.mp4", "size": 1}]
        self.patch = unittest.mock.patch.object(srv, "_videos_snapshot", side_effect=lambda: {"repo": "o/r", "videos": list(self.videos)})
        self.patch.start()
        srv.cast_clear()

    def tearDown(self):
        self.patch.stop()

    def test_a_video_made_after_the_request_is_the_one_to_cast(self):
        self.assertIsNone(srv._cast_ready())                                     # no wish
        srv.cast_register("salon")
        self.assertIsNone(srv._cast_ready())                                     # the old video is not the one
        self.videos.insert(0, {"name": "new.mp4", "ts": "2026-10-12T22:00:00Z", "url": "https://github.com/o/r/releases/download/videos/new.mp4", "size": 2})
        ready = srv._cast_ready()
        self.assertEqual((ready["tv"], ready["name"]), ("salon", "new.mp4"))
        self.assertTrue(ready["url"].startswith("https://"))
        srv.cast_clear()
        self.assertIsNone(srv._cast_ready())                                     # the phone cleared it once it started

    def test_a_wish_is_forgotten_after_a_while(self):
        srv.cast_register("")
        self.videos.insert(0, {"name": "new.mp4", "ts": "x", "url": "https://github.com/o/r/releases/download/videos/new.mp4", "size": 2})
        self.assertIsNone(srv._cast_ready(now=time.time() + srv.CAST_WAIT_SECONDS + 5))
        self.assertFalse(srv.cast_pending["on"])


if __name__ == "__main__":
    unittest.main()
