"""Tests of what comes back through the relay (an app, the relay test, the
"Claude waits" ping), of the Claude login the app drives, of the CI news behind the
widget's "Prêt" chip, and of the HTTP routes.

No network and no real `claude`: a fake ntfy server and a fake `claude` script stand
in for them. What the fakes copy was seen with the real ones — the real CLI prints
the login address inside terminal escape codes, then "Paste code here if prompted >",
and answers a refused code with "Login failed: Request failed with status code 400" and
exits; ntfy documents that a file sent with a PUT becomes an attachment whose message
event carries {name, url, size}. Run: python3 -m unittest discover -s backend/tests
"""
import http.server
import json
import os
import re
import stat
import sys
import tempfile
import threading
import time
import unittest
import unittest.mock
import urllib.request
from pathlib import Path

HOME = tempfile.mkdtemp(prefix="aiwa-test-home-")
os.environ["HOME"] = HOME  # before the import: the server builds its paths from it
BIN = Path(HOME) / "bin"
BIN.mkdir()
os.environ["PATH"] = f"{BIN}{os.pathsep}{os.environ['PATH']}"
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import aiwa_github as gh  # noqa: E402
import aiwa_server as srv  # noqa: E402

FAKE_CLAUDE = r'''#!/usr/bin/env python3
import os, sys
marker = os.path.expanduser("~/.fake_logged_in")
if sys.argv[1:3] == ["auth", "status"]:
    print('{\n  "loggedIn": %s\n}' % ("true" if os.path.exists(marker) else "false"))
elif sys.argv[1:3] == ["auth", "login"]:
    print("Opening browser to sign in…")
    url = "https://claude.com/cai/oauth/authorize?code=true&client_id=x&state=abc"
    print("If the browser didn't open, visit: \x1b]8;;" + url + "\x07\x1b[94m" + url + "\x1b[39m\x1b]8;;\x07")
    sys.stdout.write("Paste code here if prompted > ")
    sys.stdout.flush()
    line = sys.stdin.readline().strip()
    if line == "good#state":
        open(marker, "w").close()
        print("Login successful")
        sys.exit(0)
    print("Login failed: Request failed with status code 400")
    sys.exit(1)
elif "--cloud" in sys.argv:
    import json
    # Creation and follow-up (the /rename queued after a creation) run at the same time: two files.
    name = "last_followup_args.json" if "-p" in sys.argv else "last_create_args.json"
    open(os.path.expanduser("~/" + name), "w").write(json.dumps({"argv": sys.argv[1:]}))
    if "--debug-file" in sys.argv and os.environ.get("FAKE_CLAUDE_MODE") == "noapp":
        # what the real CLI writes there when Claude's GitHub app is not installed on the repository
        open(sys.argv[sys.argv.index("--debug-file") + 1], "w").write("Checking GitHub app installation for o/r\nGitHub app is not installed on o/r\n[teleportToRemote] Bundling (reason: github_preflight_failed)\n")
    if "--debug-file" in sys.argv and os.environ.get("FAKE_CLAUDE_MODE") == "nobranch":
        open(sys.argv[sys.argv.index("--debug-file") + 1], "w").write("[teleportToRemote] Bundling (reason: branch_not_on_remote)\n")
    if os.environ.get("FAKE_CLAUDE_MODE") == "fail":
        print("Error: Not logged in · Please run /login")
        sys.exit(1)
    if os.environ.get("FAKE_CLAUDE_MODE") == "slow":
        import time
        for pct in (10, 40, 80):
            print("Uploading repository bundle %d%%" % pct)
            sys.stdout.flush()
            time.sleep(0.8)
    if os.environ.get("FAKE_CLAUDE_MODE") == "silent":
        import time
        print("Uploading repository bundle 10%")
        sys.stdout.flush()
        time.sleep(120)
    if os.environ.get("FAKE_CLAUDE_MODE") == "prompt":
        sys.stdout.write("Quick safety check: do you trust the files in this folder? (1. Yes, 2. No)")
        sys.stdout.flush()
        import time
        time.sleep(120)
    if os.environ.get("FAKE_CLAUDE_MODE") == "footer":
        sys.stdout.write("Enter to confirm \u00b7 Esc to cancel")
        sys.stdout.flush()
        import time
        time.sleep(120)
    if "-p" in sys.argv:
        print('{"ok": true, "session_id": "session_TEST123abc", "url": "https://claude.ai/code/session_TEST123abc"}')
    else:
        print("Session created: session_TEST123abc https://claude.ai/code/session_TEST123abc")
'''
(BIN / "claude").write_text(FAKE_CLAUDE)
(BIN / "claude").chmod((BIN / "claude").stat().st_mode | stat.S_IEXEC)

APP = "<!doctype html><html><head><title>Demo App</title></head><body><script>/* an app for the Store */</script></body></html>\n" + "<!-- pad -->\n" * 300


class FakeNtfy(http.server.BaseHTTPRequestHandler):
    files = {}      # /file/<id> -> bytes
    stream = []     # the events the next /json request gets

    def log_message(self, *args):
        pass

    def do_GET(self):
        if self.path.startswith("/file/"):
            body = self.files.get(self.path[len("/file/"):])
            if body is None:
                self.send_error(404)
                return
            self.send_response(200)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        elif "/json" in self.path:
            events, FakeNtfy.stream = list(FakeNtfy.stream), []
            body = "".join(json.dumps(e) + "\n" for e in events).encode()
            self.send_response(200)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_error(404)


class FakeRaw(http.server.BaseHTTPRequestHandler):
    """raw.githubusercontent.com: /<owner>/<repo>/refs/heads/<branch>/<path>, with a cache-busting query the fake ignores."""
    files = {}

    def log_message(self, *args):
        pass

    def do_GET(self):
        body = self.files.get(self.path.split("?")[0])
        if body is None:
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def wait_for(condition, seconds=10):
    end = time.time() + seconds
    while time.time() < end:
        if condition():
            return True
        time.sleep(0.05)
    return False


class Base(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ntfy = http.server.ThreadingHTTPServer(("127.0.0.1", 0), FakeNtfy)
        threading.Thread(target=cls.ntfy.serve_forever, daemon=True).start()
        srv.NTFY_SERVER = f"http://127.0.0.1:{cls.ntfy.server_address[1]}"

    @classmethod
    def tearDownClass(cls):
        cls.ntfy.shutdown()

    def setUp(self):
        srv.waiting_topic = "aiwa-" + "ab" * 12
        srv.sent_app = None
        srv.deploy_mode = "none"
        srv.current_repo = None
        srv.current_cloud = None
        srv.relay_cloud.update(asked=None, ok=None)
        srv.relay_seen.update(id=None, time=None)
        srv.claude_login.update(state="unknown", at=0.0, busy=False)
        srv._clear_waiting()
        FakeNtfy.files.clear()
        FakeNtfy.stream = []
        for old in srv.APP_DIR.glob("*"):
            old.unlink()

    def app_event(self, event_id="e1", name="demo-app.app.html", code=APP, **over):
        FakeNtfy.files[event_id] = code.encode()
        event = {
            "id": event_id, "time": int(time.time()), "event": "message", "topic": srv.waiting_topic,
            "title": "aiwa-app", "message": f"You received a file: {name}",
            "attachment": {"name": name, "url": f"{srv.NTFY_SERVER}/file/{event_id}", "size": len(code.encode())},
        }
        event.update(over)
        return event


class SentAppTests(Base):
    """The "store" mode: Claude sends an app (one self-contained index.html, name.app.html). The "aiwa" mode: the same, name.aiwa.html."""

    def test_an_app_of_the_aiwa_mode_is_kept_with_its_kind(self):
        srv._app_received(self.app_event(name="vote.aiwa.html"))
        self.assertEqual(srv._sent_app_snapshot()["name"], "vote.aiwa.html")
        self.assertEqual(srv._sent_app_snapshot()["kind"], "aiwa")
        self.assertEqual(srv._sent_app_code()["kind"], "aiwa")
        srv._app_received(self.app_event("e2", name="plain.app.html"))
        self.assertEqual(srv._sent_app_snapshot()["kind"], "code")

    def test_a_file_without_a_known_name_takes_the_extension_of_the_mode(self):
        srv.deploy_mode = "aiwa"
        srv._app_received(self.app_event(name="upload.bin", code="<title>Show of hands</title><p>x"))
        self.assertEqual(srv._sent_app_snapshot()["name"], "show-of-hands.aiwa.html")
        srv.deploy_mode = "store"
        srv._app_received(self.app_event("e2", name="upload.bin", code="<title>Show of hands</title><p>x"))
        self.assertEqual(srv._sent_app_snapshot()["name"], "show-of-hands.app.html")

    def test_the_relay_recognises_an_aiwa_file_by_its_name(self):
        event = self.app_event("c3", name="vote.aiwa.html")
        event.pop("title")
        srv._relay_event(event)
        self.assertTrue(wait_for(lambda: (srv._sent_app_snapshot() or {}).get("name") == "vote.aiwa.html"))

    def test_the_aiwa_instruction_says_what_a_contract_app_is_and_where_the_sdk_is(self):
        srv.deploy_mode = "aiwa"
        text = dict(srv._instruction_lines(None, None, None, True))["deploy"]
        for wanted in ("-T nom.aiwa.html", "Title: aiwa-app", f"{srv.NTFY_SERVER}/{srv.waiting_topic}", "Ne la pousse sur AUCUN dépôt",
                       srv.AIWA_SDK_URL, "signedAction", "verifySignedAction", "DANS l'action", 'sandbox="allow-scripts"', "512 Ko",
                       "aiwa-app-example.html", "pointeur", "immuable", "deux fichiers", "paquet de code signé", "autre version"):
            self.assertIn(wanted, text)
        self.assertNotIn("ne contient qu'un pointeur", text, "the registry keeps the code too, in the second file: it was wrong to say GitHub only holds a pointer")
        self.assertIn("lib/aiwa.js", srv.AIWA_SDK_URL)

    def test_an_app_sent_with_curl_is_downloaded_and_kept(self):
        srv._app_received(self.app_event())
        self.assertEqual(srv._sent_app_snapshot()["name"], "demo-app.app.html")
        self.assertFalse(srv._sent_app_snapshot()["seen"])
        got = srv._sent_app_code()
        self.assertEqual(got, {"ok": True, "name": "demo-app.app.html", "kind": "code", "code": APP})
        self.assertEqual((srv.APP_DIR / "demo-app.app.html").read_text(), APP)

    def test_the_name_falls_back_to_the_title_of_the_page(self):
        srv._app_received(self.app_event(name="upload.bin", code="<title>My Token!</title><p>x"))
        self.assertEqual(srv._sent_app_snapshot()["name"], "my-token.app.html")
        srv._app_received(self.app_event("c9", name="x.app.html", code="<p>no title here</p>"))
        self.assertEqual(srv._sent_app_snapshot()["name"], "x.app.html")

    def test_a_dangerous_name_is_never_used(self):
        srv._app_received(self.app_event(name="../../etc/passwd.app.html", code="<p>no title here</p>"))
        self.assertEqual(srv._sent_app_snapshot()["name"], "app.app.html")
        self.assertFalse(Path(HOME, "..", "etc").exists())

    def test_an_attachment_from_another_host_is_not_fetched(self):
        event = self.app_event()
        event["attachment"]["url"] = "http://127.0.0.1:1/file/e1"
        srv._app_received(event)
        self.assertIsNone(srv._sent_app_snapshot())

    def test_a_too_big_or_empty_or_binary_file_is_not_kept(self):
        big = self.app_event("big")
        big["attachment"]["size"] = srv.APP_MAX + 1
        srv._app_received(big)
        binary = self.app_event("bin")
        FakeNtfy.files["bin"] = b"\xff\xfe\x00\x01"  # after the helper, which stores an app under that id
        binary["attachment"]["size"] = 4
        srv._app_received(binary)
        srv._app_received(self.app_event("empty", code="  \n"))
        self.assertIsNone(srv._sent_app_snapshot())

    def test_the_relay_recognises_an_app_by_title_or_by_file_name(self):
        srv._relay_event(self.app_event("c1"))
        self.assertTrue(wait_for(lambda: (srv._sent_app_snapshot() or {}).get("name") == "demo-app.app.html"))
        untitled = self.app_event("c2", name="other.app.html")
        untitled.pop("title")
        srv._relay_event(untitled)
        self.assertTrue(wait_for(lambda: (srv._sent_app_snapshot() or {}).get("name") == "other.app.html"))

    def test_a_small_app_sent_as_a_plain_message_is_kept_too(self):
        srv._relay_event({"id": "m1", "time": int(time.time()), "event": "message", "title": "aiwa-app", "message": "<title>Tiny</title><p>x</p>"})
        self.assertTrue(wait_for(lambda: srv._sent_app_snapshot() is not None))
        self.assertEqual(srv._sent_app_snapshot()["name"], "tiny.app.html")

    def test_only_the_last_ten_are_kept(self):
        for n in range(13):
            srv._app_received(self.app_event(f"e{n}", name=f"app{n}.app.html"))
            time.sleep(0.01)
        self.assertEqual(len(list(srv.APP_DIR.glob("*.app.html"))), srv.APP_KEEP)
        self.assertEqual(srv._sent_app_snapshot()["name"], "app12.app.html")

    def test_seen_clears_the_widget_state(self):
        srv.deploy_mode = "store"
        self.assertEqual(srv._site_snapshot(), {"url": None, "state": "waiting", "kind": "store"})
        srv._app_received(self.app_event())
        self.assertEqual(srv._site_snapshot()["state"], "live")
        srv._sent_app_seen()
        self.assertEqual(srv._site_snapshot()["state"], "waiting")

    def test_it_survives_a_restart(self):
        srv._app_received(self.app_event())
        srv._cloud_check_seen()
        srv._save_state()
        srv.sent_app = None
        srv.relay_cloud.update(asked=None, ok=None)
        srv._load_state()
        self.assertEqual(srv._sent_app_snapshot()["name"], "demo-app.app.html")
        self.assertEqual(srv._relay_cloud_state(), "ok")

    def test_the_instruction_says_what_to_write_read_and_send(self):
        srv.deploy_mode = "store"
        text = dict(srv._instruction_lines(None, None, None, True))["deploy"]
        for wanted in ("-T nom.app.html", "Title: aiwa-app", f"{srv.NTFY_SERVER}/{srv.waiting_topic}", "Ne la pousse sur AUCUN dépôt",
                       # the app runs in an isolated frame: what that means for the file
                       'sandbox="allow-scripts"', "512 Ko", "URL ABSOLUE", "localStorage", "store-app-example.html", "bouton ▦"):
            self.assertIn(wanted, text)
        self.assertIn("raw.githubusercontent.com/theodoreyong9/Aiwa_store/main/", text)
        for mode in ("none", "pages", "android"):
            srv.deploy_mode = mode
            joined = " ".join(t for _, t in srv._instruction_lines("o/r", "w", "main", True))
            self.assertNotIn("aiwa-app", joined)


class MailboxTests(Base):
    """The GitHub mailbox: with the relay blocked, Claude pushes one line, aiwa-out/SIGNAL (and for an app the app), to its own branch of the
    attached repository, and the phone reads it from raw.githubusercontent.com for a while after a message: an app, the alert, the relay test."""
    REPO, WORK = "someone/some-repo", "aiwa/20261004-120000"

    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.raw = http.server.ThreadingHTTPServer(("127.0.0.1", 0), FakeRaw)
        threading.Thread(target=cls.raw.serve_forever, daemon=True).start()
        srv.RAW_BASE = f"http://127.0.0.1:{cls.raw.server_address[1]}"

    @classmethod
    def tearDownClass(cls):
        cls.raw.shutdown()
        super().tearDownClass()

    def setUp(self):
        super().setUp()
        FakeRaw.files.clear()
        srv.mailbox.update(key=None, until=0.0, running=False, seen=None)
        srv.last_message_at = int(time.time()) - 30      # a message was sent a moment ago

    def base(self, work=None):
        return f"/{self.REPO}/refs/heads/{work or self.WORK}/aiwa-out"

    def signal(self, line):
        FakeRaw.files[f"{self.base()}/SIGNAL"] = line.encode()

    def push_app(self, name="demo-app.app.html", code=APP, moment=None):
        FakeRaw.files[f"{self.base()}/{name}"] = code.encode()
        self.signal(f"app {name} {moment or time.time_ns()}")

    def now(self):
        return int(time.time())

    # ---- an app ----
    def test_an_app_pushed_to_the_branch_is_found_and_kept(self):
        self.push_app()
        self.assertTrue(srv._mailbox_once(self.REPO, self.WORK))
        self.assertEqual(srv._sent_app_code(), {"ok": True, "name": "demo-app.app.html", "kind": "code", "code": APP})
        self.assertFalse(srv._sent_app_snapshot()["seen"])

    def test_the_same_app_is_announced_once_and_a_correction_again(self):
        self.push_app()
        self.assertTrue(srv._mailbox_once(self.REPO, self.WORK))
        srv._sent_app_seen()
        self.assertFalse(srv._mailbox_once(self.REPO, self.WORK), "nothing new: nothing announced")
        self.assertTrue(srv._sent_app_snapshot()["seen"], "and the widget is not woken again")
        self.push_app(code=APP.replace("Demo App", "Demo App v2"))
        self.assertTrue(srv._mailbox_once(self.REPO, self.WORK))
        self.assertIn("Demo App v2", srv._sent_app_code()["code"])
        self.assertFalse(srv._sent_app_snapshot()["seen"])

    def test_nothing_pushed_yet_is_nothing(self):
        self.assertFalse(srv._mailbox_once(self.REPO, self.WORK))
        self.assertIsNone(srv._sent_app_snapshot())
        self.assertEqual(srv._relay_cloud_state(), "untested")

    def test_a_signal_that_is_not_well_formed_is_never_followed(self):
        for bad in ("app ../../etc/passwd 1", "app x/y.app.html 1", "app UPPER.app.html 1", "app notes.txt 1", "app demo-app.app.html", "app demo-app.app.html x",
                    "attend", "attend soon", "check", "check ok", "wait 5", "", "attend 1\nattend 2", "attend 1 2", "-x 1"):
            self.signal(bad)
            self.assertFalse(srv._mailbox_once(self.REPO, self.WORK), bad)
        self.assertIsNone(srv._sent_app_snapshot())
        self.assertEqual(srv._relay_cloud_state(), "untested")
        self.assertIsNone(srv.waiting["since"])

    def test_a_too_big_or_empty_or_binary_file_is_not_kept(self):
        with unittest.mock.patch.object(srv, "APP_MAX", 50):
            self.push_app()
            self.assertFalse(srv._mailbox_once(self.REPO, self.WORK))
        self.push_app(code="   \n")
        self.assertFalse(srv._mailbox_once(self.REPO, self.WORK))
        FakeRaw.files[f"{self.base()}/demo-app.app.html"] = b"\xff\xfe\x00"
        self.signal(f"app demo-app.app.html {time.time_ns()}")
        self.assertFalse(srv._mailbox_once(self.REPO, self.WORK))
        self.assertIsNone(srv._sent_app_snapshot())

    def test_an_app_whose_file_is_not_there_yet_is_looked_for_again(self):
        self.signal(f"app demo-app.app.html {time.time_ns()}")
        self.assertFalse(srv._mailbox_once(self.REPO, self.WORK))
        FakeRaw.files[f"{self.base()}/demo-app.app.html"] = APP.encode()
        self.assertTrue(srv._mailbox_once(self.REPO, self.WORK), "the same signal, now that the file has arrived")

    # ---- the alert and the relay test ----
    def test_claude_waiting_through_github_wakes_the_alert_and_proves_the_cloud_can_speak(self):
        self.assertEqual(srv._relay_cloud_state(), "untested")
        self.signal(f"attend {self.now()}")
        self.assertTrue(srv._mailbox_once(self.REPO, self.WORK))
        self.assertIsNotNone(srv.waiting["since"])
        self.assertEqual(srv._relay_cloud_state(), "ok")
        self.assertFalse(srv._mailbox_once(self.REPO, self.WORK), "the same signal does not wake it twice")

    def test_a_second_signal_wakes_it_again_after_the_answer(self):
        self.signal(f"attend {self.now()}")
        self.assertTrue(srv._mailbox_once(self.REPO, self.WORK))
        srv._clear_waiting()                                  # the user answered
        self.assertFalse(srv._mailbox_once(self.REPO, self.WORK), "the old signal is still in the branch: it must not wake the alert")
        self.assertIsNone(srv.waiting["since"])
        self.signal(f"attend {self.now() + 1}")
        self.assertTrue(srv._mailbox_once(self.REPO, self.WORK))
        self.assertIsNotNone(srv.waiting["since"])

    def test_the_relay_test_through_github_confirms_the_relay_without_waking_the_alert(self):
        srv.relay_cloud.update(asked=time.time(), ok=None)
        self.signal(f"check {self.now()}")
        self.assertTrue(srv._mailbox_once(self.REPO, self.WORK))
        self.assertEqual(srv._relay_cloud_state(), "ok")
        self.assertIsNone(srv.waiting["since"])

    def test_a_signal_older_than_our_last_message_is_not_an_answer_to_it(self):
        self.signal(f"attend {self.now() - 3600}")
        self.assertFalse(srv._mailbox_once(self.REPO, self.WORK))
        self.assertIsNone(srv.waiting["since"])
        self.assertEqual(srv._relay_cloud_state(), "untested")

    # ---- the watch ----
    def test_the_watch_runs_in_the_background_for_a_sane_repository_and_branch_only(self):
        for repo, work in ((None, self.WORK), ("not a repo", self.WORK), (self.REPO, "../x"), (self.REPO, "a b"), (self.REPO, None), (self.REPO, "a/../b")):
            srv._mailbox_watch(repo, work)
            self.assertFalse(srv.mailbox["running"], (repo, work))
        for mode in ("none", "aiwa"):                         # whatever the mode: the alert is for every one of them
            srv.mailbox.update(key=None, until=0.0, running=False, seen=None)
            srv.deploy_mode = mode
            srv.waiting["since"] = None
            self.signal(f"attend {self.now()}")
            # The pace stays short until the watch has ended: the loop reads it each time it goes to sleep, and a patch that ended first (on a slow
            # machine the thread was not scheduled in between) left it asleep for the real 20 seconds.
            with unittest.mock.patch.object(srv, "MAILBOX_EVERY", 0.05):
                srv._mailbox_watch(self.REPO, self.WORK)
                self.assertTrue(wait_for(lambda: srv.waiting["since"] is not None), mode)
                srv.mailbox["until"] = 0.0      # the watch ends by itself at its deadline
                self.assertTrue(wait_for(lambda: not srv.mailbox["running"]), mode)
        self.push_app(name="demo.aiwa.html")
        with unittest.mock.patch.object(srv, "MAILBOX_EVERY", 0.05):
            srv._mailbox_watch(self.REPO, self.WORK)
            self.assertTrue(wait_for(lambda: srv._sent_app_snapshot() is not None))
            self.assertEqual(srv._sent_app_snapshot()["kind"], "aiwa")
            srv.mailbox["until"] = 0.0
            self.assertTrue(wait_for(lambda: not srv.mailbox["running"]))

    # ---- where the code of the app is on GitHub ----
    def session_on(self, direct, work=None, base="main"):
        srv._save_cloud_session("session_TEST123abc", "t", None, repo=self.REPO, work=work or self.WORK, base=base, direct=direct)
        srv.current_cloud = "session_TEST123abc"
        srv.app_github.update(key=None, url=None, at=0.0, busy=False)
        self.addCleanup(lambda: (Path(srv.CLOUD_STORE).unlink(missing_ok=True), setattr(srv, "current_cloud", None)))

    def apps_path(self, branch, name="demo-app.app.html"):
        return f"/{self.REPO}/refs/heads/{branch}/aiwa-apps/{name}"

    def github_url(self):
        srv._sent_app_snapshot()           # starts the look-up
        wait_for(lambda: not srv.app_github["busy"])
        return srv._sent_app_snapshot()["github"]

    def test_the_code_of_the_app_is_found_on_github_once_claude_has_put_it_there(self):
        self.session_on(direct=False)
        srv._app_ready(APP, "demo-app.app.html", "relay:2")
        self.assertIsNone(self.github_url(), "not there yet: nothing is promised")
        FakeRaw.files[self.apps_path(self.WORK)] = APP.encode()
        srv.app_github["at"] = 0.0         # the next look-up may start at once
        self.assertEqual(self.github_url(), f"https://github.com/{self.REPO}/blob/{self.WORK}/aiwa-apps/demo-app.app.html")

    def test_with_a_direct_integration_the_base_branch_comes_first(self):
        self.session_on(direct=True)
        srv._app_ready(APP, "demo-app.app.html", "relay:3")
        FakeRaw.files[self.apps_path("main")] = APP.encode()
        FakeRaw.files[self.apps_path(self.WORK)] = APP.encode()
        self.assertEqual(self.github_url(), f"https://github.com/{self.REPO}/blob/main/aiwa-apps/demo-app.app.html")

    def test_a_session_with_no_repository_has_no_github_address(self):
        srv.current_cloud = None
        srv.last_cloud = None
        Path(srv.CLOUD_STORE).unlink(missing_ok=True)
        srv._app_ready(APP, "demo-app.app.html", "relay:4")
        self.assertIsNone(srv._sent_app_snapshot()["github"])

    # ---- what an APK needs besides itself ----
    def termux_line(self, repo="someone/some-repo", branch="main"):
        srv.apk_termux.update(repo=None, command=None, at=0.0, busy=False)
        srv._apk_termux_command(repo)        # starts the look-up
        wait_for(lambda: not srv.apk_termux["busy"])
        return srv._apk_termux_command(repo)

    def test_an_app_that_needs_termux_says_so_in_the_repository_and_the_line_comes_to_the_phone(self):
        FakeRaw.files["/someone/some-repo/refs/heads/main/aiwa-android.json"] = json.dumps({"termux": "curl -fsSL https://raw.githubusercontent.com/someone/some-repo/main/setup.sh | bash"}).encode()
        self.assertEqual(self.termux_line(), "curl -fsSL https://raw.githubusercontent.com/someone/some-repo/main/setup.sh | bash")

    def test_no_declaration_no_line_and_a_bad_one_is_not_taken(self):
        self.assertIsNone(self.termux_line())
        for bad in ({"termux": "line one\nline two"}, {"termux": ""}, {"termux": 7}, {"termux": "x" * 601}, ["not", "an", "object"]):
            FakeRaw.files["/someone/some-repo/refs/heads/main/aiwa-android.json"] = json.dumps(bad).encode()
            self.assertIsNone(self.termux_line(), bad)
        FakeRaw.files["/someone/some-repo/refs/heads/main/aiwa-android.json"] = b"not json"
        self.assertIsNone(self.termux_line())

    def test_this_repository_declares_its_own_termux_line(self):
        # The Aiwa app needs Termux: the widget's download button, on this very repository, copies this line and opens Termux.
        declared = json.loads((Path(__file__).resolve().parents[3] / "aiwa-android.json").read_text(encoding="utf-8"))
        self.assertTrue(srv.TERMUX_LINE_RE.fullmatch(declared["termux"]))
        self.assertIn("raw.githubusercontent.com/theodoreyong9/Aiwa_store/main/android/backend/bootstrap.sh", declared["termux"])

    def test_the_android_instruction_asks_for_the_declaration_only_when_termux_is_needed(self):
        srv.deploy_mode = "android"
        text = dict(srv._instruction_lines("someone/some-repo", self.WORK, "main", True))["deploy"]
        self.assertIn("aiwa-android.json", text)
        self.assertIn("a besoin de Termux", text)
        self.assertIn("Sinon n'ajoute rien", text)
        srv.deploy_mode = "none"

    # ---- what Claude is told ----
    def test_the_instructions_give_the_fallbacks_only_when_a_repository_is_attached(self):
        for mode, example in (("store", "nom.app.html"), ("aiwa", "nom.aiwa.html")):
            srv.deploy_mode = mode
            lines = dict(srv._instruction_lines(self.REPO, self.WORK, "main", True))
            for wanted in (f"aiwa-out/{example}", f'echo "app {example} $(date +%s)" > aiwa-out/SIGNAL', f"git push origin {self.WORK}", f"aiwa-apps/{example}",
                           "jamais sur la branche principale", "colle le code dans ta réponse", f"{srv.NTFY_SERVER}/{srv.waiting_topic}"):
                self.assertIn(wanted, lines["deploy"], (mode, wanted))
            self.assertNotIn("Ne la pousse sur AUCUN dépôt", lines["deploy"], "with a repository, the code goes there")
            alone = dict(srv._instruction_lines(None, None, None, True))["deploy"]
            for unwanted in ("aiwa-out", "aiwa-apps"):
                self.assertNotIn(unwanted, alone)
            self.assertIn("Ne la pousse sur AUCUN dépôt", alone)
            self.assertIn("colle le code dans ta réponse", alone)
        for mode in ("none", "pages", "store"):
            srv.deploy_mode = mode
            alert = dict(srv._instruction_lines(self.REPO, self.WORK, "main", True))["alert"]
            for wanted in (f"-d attend {srv.NTFY_SERVER}/{srv.waiting_topic}", 'echo "attend $(date +%s)" > aiwa-out/SIGNAL', f"git push origin {self.WORK}",
                           "SEULEMENT quand tu attends une réponse", "jamais sur la branche principale"):
                self.assertIn(wanted, alert, (mode, wanted))
            bare = dict(srv._instruction_lines(None, None, None, True))["alert"]
            self.assertNotIn("aiwa-out", bare)
            self.assertIn("ignore l'erreur", bare)

    def test_the_relay_test_gives_its_fallback_only_when_a_repository_is_attached(self):
        with_repo = srv._relay_check_text(force=True, repo=self.REPO, work=self.WORK)
        for wanted in ("aiwa-check", 'echo "check $(date +%s)" > aiwa-out/SIGNAL', f"git push origin {self.WORK}"):
            self.assertIn(wanted, with_repo)
        without = srv._relay_check_text(force=True)
        self.assertIn("aiwa-check", without)
        self.assertNotIn("aiwa-out", without)


class DesignLineTests(Base):
    def test_a_session_with_an_interface_to_make_is_told_how_to_use_the_design_research(self):
        for repo, deploy in (("o/r", "pages"), (None, "store"), (None, "aiwa"), ("o/r", "android")):
            with unittest.mock.patch.object(srv, "deploy_mode", deploy):
                text = dict(srv._instruction_lines(repo, "w", "main", True)).get("design", "")
            self.assertIn("design-research/src/cli.js research", text, f"{repo} {deploy}")
            self.assertIn("DR_CATALOG_URL=" + srv.REFERENCE_RAW + "/design-research/catalog", text)
            self.assertIn("ne recopie ni l'identité", text)
            self.assertIn("conçois sans", text, "the catalogue may be empty or unreachable: Claude goes on without it")

    def test_a_game_is_asked_for_the_standard_before_it_is_built(self):
        with unittest.mock.patch.object(srv, "deploy_mode", "store"):
            text = dict(srv._instruction_lines(None, None, None, True))["game"]
        self.assertIn(srv.REFERENCE_RAW + "/docs/GAME-FOUNDATION.md", text)
        self.assertIn("Godot 4 + Nakama", text)
        self.assertIn("sans la sortie de son exécution", text)
        self.assertIn("n'invente aucune méthode", text)

    def test_it_is_told_once_and_again_only_when_it_changes(self):
        with unittest.mock.patch.object(srv, "deploy_mode", "store"):
            first, told = srv._compose(None, None, None, None, True)
            self.assertIn("Design :", first)
            again, _ = srv._compose({"instr": told}, None, None, None, True)
            self.assertEqual(again, "")


class AskAndVideoTests(Base):
    def setUp(self):
        super().setUp()
        with srv.lock:
            srv.ask = None

    def test_a_question_from_claude_reaches_the_widget_and_its_answer_goes_back_to_the_session(self):
        srv._relay_event({"id": "e1", "time": int(time.time()), "title": "aiwa-ask", "message": json.dumps({"question": "Quelle couleur ?", "options": ["Bleu", "Rouge", "", 3]})})
        shown = srv._ask_snapshot()
        self.assertEqual(shown["question"], "Quelle couleur ?")
        self.assertEqual(shown["options"], ["Bleu", "Rouge"])
        sent = []
        with unittest.mock.patch.object(srv, "cloud_send", lambda text, command=False: (sent.append(text), {"ok": True})[1]):
            self.assertFalse(srv._ask_answer(json.dumps({"id": "other", "answer": "Bleu"}))["ok"], "an answer to another question is refused")
            self.assertFalse(srv._ask_answer(json.dumps({"id": shown["id"], "answer": "  "}))["ok"])
            self.assertTrue(srv._ask_answer(json.dumps({"id": shown["id"], "answer": "Bleu"}))["ok"])
            self.assertFalse(srv._ask_answer(json.dumps({"id": shown["id"], "answer": "Bleu"}))["ok"], "answered once")
        self.assertEqual(sent, ["Ma réponse à ta demande « Quelle couleur ? » : Bleu"])
        self.assertIsNone(srv._ask_snapshot())

    def test_a_failed_send_keeps_the_question_so_that_it_can_be_answered_again(self):
        srv._ask_set(json.dumps({"question": "Oui ou non ?"}), "e2")
        with unittest.mock.patch.object(srv, "cloud_send", lambda text, command=False: {"ok": False, "error": "busy"}):
            self.assertFalse(srv._ask_answer(json.dumps({"id": "e2", "answer": "Oui"}))["ok"])
        self.assertIsNotNone(srv._ask_snapshot())

    def test_what_is_not_a_question_is_not_kept(self):
        for raw in ("pas du json", "[]", json.dumps({"question": "  "}), json.dumps({"options": ["a"]})):
            self.assertFalse(srv._ask_set(raw, "x"))
        self.assertIsNone(srv._ask_snapshot())
        self.assertTrue(srv._ask_set(json.dumps({"question": "q"}), "same"))
        self.assertFalse(srv._ask_set(json.dumps({"question": "q"}), "same"), "the same event twice is one question")

    def test_every_session_is_told_to_ask_through_the_widget_and_never_in_the_conversation(self):
        for repo in (None, "o/r"):
            text = dict(srv._instruction_lines(repo, "w", "main", True))["ask"]
            self.assertIn("ne me pose JAMAIS de question dans ta conversation", text)
            self.assertIn("Title: aiwa-ask", text)
            self.assertIn("Ne t'arrête pas pour l'attendre", text)
        self.assertIn("aiwa-out/ask.json", dict(srv._instruction_lines("o/r", "w", "main", True))["ask"])

    def test_the_videos_of_a_repository_are_the_files_of_its_release(self):
        release = {"assets": [
            {"name": "demo-9x16.mp4", "size": 10, "browser_download_url": "https://github.com/o/r/releases/download/videos/demo-9x16.mp4", "updated_at": "2026-10-01T00:00:00Z"},
            {"name": "demo-16x9.mp4", "size": 20, "browser_download_url": "https://github.com/o/r/releases/download/videos/demo-16x9.mp4", "updated_at": "2026-10-02T00:00:00Z"},
            {"name": "notes.txt", "size": 1, "browser_download_url": "https://github.com/o/r/releases/download/videos/notes.txt", "updated_at": "2026-10-03T00:00:00Z"},
            {"name": "evil.mp4", "size": 1, "browser_download_url": "https://evil.example/evil.mp4", "updated_at": "2026-10-04T00:00:00Z"},
        ]}
        asked = []
        with unittest.mock.patch.object(srv, "current_repo", "o/r"), unittest.mock.patch.object(srv, "current_cloud", None), unittest.mock.patch.object(srv, "last_cloud", None), \
                unittest.mock.patch.object(srv, "_github_json", lambda url: (asked.append(url), release)[1]):
            srv.video_cache.update(key=None, items=[], at=0.0)
            got = srv._videos_snapshot()
            again = srv._videos_snapshot()
        self.assertEqual([v["name"] for v in got["videos"]], ["demo-16x9.mp4", "demo-9x16.mp4"])
        self.assertEqual(asked, ["https://api.github.com/repos/o/r/releases/tags/videos"], "one look a minute")
        self.assertEqual(again, got)

    def test_a_session_on_a_repository_is_told_where_the_videos_go(self):
        text = dict(srv._instruction_lines("o/r", "w", "main", True))["video"]
        self.assertIn("release GitHub `videos` du dépôt o/r", text)
        self.assertIn("N'invente aucune statistique", text)
        self.assertNotIn("video", dict(srv._instruction_lines(None, None, None, True)))


class RelayTests(Base):
    def event(self, message="attend", **over):
        event = {"id": f"id{time.time_ns()}", "time": int(time.time()), "event": "message", "message": message}
        event.update(over)
        return event

    def test_the_relay_test_goes_untested_pending_missing_then_ok(self):
        self.assertEqual(srv._relay_cloud_state(), "untested")
        text = srv._relay_check_text()
        self.assertIn("Title: aiwa-check", text)
        self.assertIn(srv.waiting_topic, text)
        self.assertEqual(srv._relay_cloud_state(), "pending")
        srv.relay_cloud["asked"] -= srv.CLOUD_CHECK_WAIT + 1
        self.assertEqual(srv._relay_cloud_state(), "missing")
        srv._relay_event(self.event("ok", title="aiwa-check"))
        self.assertEqual(srv._relay_cloud_state(), "ok")
        self.assertEqual(srv._relay_check_text(), "")  # confirmed: nothing more to ask
        self.assertIn("Title: aiwa-check", srv._relay_check_text(force=True))  # unless asked again

    def test_claudes_ping_proves_the_cloud_reaches_the_relay_and_wakes_the_alert(self):
        srv._relay_event(self.event("attend"))
        self.assertEqual(srv._relay_cloud_state(), "ok")
        self.assertIsNotNone(srv.waiting["since"])

    def test_the_apps_own_test_ping_proves_only_the_phone_side(self):
        srv._relay_event(self.event("test"))
        self.assertEqual(srv._relay_cloud_state(), "untested")
        self.assertIsNotNone(srv.waiting["since"])

    def test_an_old_ping_replayed_after_a_restart_does_not_wake_the_alert(self):
        srv._relay_event(self.event("attend", time=int(time.time()) - 600))
        self.assertIsNone(srv.waiting["since"])

    def test_the_last_event_replayed_is_ignored(self):
        first = self.event("attend")
        srv._relay_event(first)
        srv._clear_waiting()
        srv._relay_event(dict(first))
        self.assertIsNone(srv.waiting["since"])

    def test_the_listener_reads_a_stream_and_gets_an_app_through(self):
        FakeNtfy.stream = [self.app_event("live1"), self.event("ok", title="aiwa-check")]
        threading.Thread(target=srv._relay_listener, daemon=True).start()
        self.assertTrue(wait_for(lambda: srv._sent_app_snapshot() is not None and srv._relay_cloud_state() == "ok"))
        self.assertEqual(srv._sent_app_code()["code"], APP)

    def test_a_login_problem_in_a_failed_send_makes_the_widget_ask_for_it(self):
        srv._note_login_problem("Error: Not logged in · Please run /login")
        self.assertEqual(srv._login_state(), "needed")
        srv._set_login_state("ok")
        srv._note_login_problem("git push failed: permission denied")
        self.assertEqual(srv.claude_login["state"], "ok")


class InstructionTests(Base):
    def test_the_reference_repository_is_offered_in_store_mode_only_and_as_an_optional_one(self):
        for repo in (None, "o/r"):
            srv.deploy_mode = "store"
            lines = dict(srv._instruction_lines(repo, "w", "main", True))
            self.assertIn("sources", lines, f"repo={repo}")
            for mode in ("none", "pages", "android"):
                srv.deploy_mode = mode
                self.assertNotIn("sources", dict(srv._instruction_lines(repo, "w", "main", True)), f"repo={repo} mode={mode}")
        srv.deploy_mode = "store"
        text = dict(srv._instruction_lines(None, None, None, True))["sources"]
        for wanted in ("FACULTATIVE", "ignore cette ligne", "lecture seule", "theodoreyong9/Aiwa_store",
                       "docs/YELLOWPAPER.md", "docs/EXPLAINED.md"):
            self.assertIn(wanted, text)
        # read-only: it must not contradict the rule that only the session's repositories are touched
        self.assertNotIn("add_repo", text)

    def test_the_reference_is_told_once_then_only_when_it_changes(self):
        srv.deploy_mode = "store"
        first, told = srv._compose({}, None, "w", "main", True)
        self.assertIn("Dépôt de référence", first)
        again, _ = srv._compose({"instr": told}, None, "w", "main", True)
        self.assertEqual(again, "")
        # a session from before this instruction existed gets it once, as an update, and nothing else repeated
        older = {k: v for k, v in told.items() if k != "sources"}
        update, _ = srv._compose({"instr": older}, None, "w", "main", True)
        self.assertIn("consignes mises à jour", update)
        self.assertIn("Dépôt de référence", update)
        self.assertNotIn("Alerte (obligatoire)", update)

    def test_no_mention_of_anything_but_this_product(self):
        for mode in ("none", "pages", "android", "store", "aiwa"):
            srv.deploy_mode = mode
            joined = " ".join(t for _, t in srv._instruction_lines("o/r", "w", "main", True)).lower()
            for old in ("yourmine", "sphère", "sphere", "aiwa_project", "jobber"):
                self.assertNotIn(old, joined)


class LoginTests(Base):
    def tearDown(self):
        srv._login_cancel()
        Path(HOME, ".fake_logged_in").unlink(missing_ok=True)

    def test_status_reads_the_cli(self):
        self.assertIs(srv._claude_auth_status(), False)
        Path(HOME, ".fake_logged_in").touch()
        self.assertIs(srv._claude_auth_status(), True)

    def test_the_address_is_found_inside_the_escape_codes(self):
        snapshot = srv._login_start()
        self.assertEqual(snapshot["phase"], "url")
        self.assertEqual(snapshot["url"], "https://claude.com/cai/oauth/authorize?code=true&client_id=x&state=abc")

    def test_a_refused_code_ends_the_login_with_the_clis_words(self):
        srv._login_start()
        snapshot = srv._login_code("bad#code")
        self.assertEqual(snapshot["phase"], "failed")
        self.assertEqual(snapshot["message"], "Login failed: Request failed with status code 400")
        self.assertEqual(srv._login_code("good#state")["message"], "Aucune connexion en cours : ouvre d'abord la page de connexion.")

    def test_a_good_code_logs_in(self):
        srv._login_start()
        snapshot = srv._login_code("good#state")
        self.assertEqual(snapshot["phase"], "done")
        self.assertEqual(srv.claude_login["state"], "ok")
        self.assertIs(srv._claude_auth_status(), True)

    def test_a_code_with_spaces_is_refused_without_touching_the_login(self):
        srv._login_start()
        snapshot = srv._login_code("two words")
        self.assertEqual(snapshot["phase"], "url")
        self.assertIn("sans espace", snapshot["message"])

    def test_cancel_stops_the_cli(self):
        srv._login_start()
        proc = srv.login_flow["proc"]
        srv._login_cancel()
        self.assertTrue(wait_for(lambda: proc.poll() is not None))
        self.assertEqual(srv._login_snapshot()["phase"], "idle")


class CloudSendTests(Base):
    """cloud_send with the fake CLI: what a new session's first message carries, and what a
    failure does to the login and relay states."""

    def setUp(self):
        super().setUp()
        srv.cloud_busy = False
        srv.current_model = srv.current_effort = None
        srv.last_cloud = None
        # Every session starts on a repository: the one chosen here, prepared without the network.
        srv.current_repo = "o/r"
        prepare = unittest.mock.patch.object(gh, "prepare_repo_dir", lambda repo: (Path(HOME), "aiwa/20260101-000000", "main"))
        prepare.start()
        self.addCleanup(prepare.stop)
        os.environ.pop("FAKE_CLAUDE_MODE", None)
        Path(HOME, "last_create_args.json").unlink(missing_ok=True)

    def tearDown(self):
        os.environ.pop("FAKE_CLAUDE_MODE", None)

    def sent(self):
        return json.loads(Path(HOME, "last_create_args.json").read_text())

    def test_a_dialog_footer_alone_is_also_taken_for_a_question(self):
        # what the phone showed: only "Enter to confirm · Esc to cancel", the question above it already scrolled away
        os.environ["FAKE_CLAUDE_MODE"] = "footer"
        original, srv.PROMPT_STALL = srv.PROMPT_STALL, 1
        self.addCleanup(setattr, srv, "PROMPT_STALL", original)
        started = time.time()
        answer = srv.cloud_send("bonjour")
        self.assertFalse(answer["ok"])
        self.assertLess(time.time() - started, 30)
        self.assertIn("attend une réponse", answer["error"])
        self.assertIn("proot-distro login ubuntu", answer["error"])

    def test_a_session_that_started_without_the_repository_says_so_and_how_to_fix_it(self):
        # the CLI's own words: Claude's GitHub app is not installed on the repository, so it uploaded the empty folder
        os.environ["FAKE_CLAUDE_MODE"] = "noapp"
        answer = srv.cloud_send("bonjour")
        self.assertTrue(answer["ok"], "the session exists, it just starts empty")
        self.assertEqual(srv.repo_access_missing, "o/r")
        self.assertIn("app GitHub", srv.github_error)
        self.assertFalse(Path(srv.CLI_DEBUG).exists(), "the debug log holds the message: it is not kept")
        # once the app is there, the next session names the repository and the warning goes
        os.environ.pop("FAKE_CLAUDE_MODE", None)
        srv.cloud_busy = False
        srv.current_cloud = None                                    # a new session, not a message into the first
        self.assertTrue(srv.cloud_send("encore")["ok"])
        self.assertIsNone(srv.repo_access_missing)

    def test_another_reason_to_send_the_folder_is_said_as_the_cli_said_it_not_blamed_on_the_app(self):
        os.environ["FAKE_CLAUDE_MODE"] = "nobranch"
        self.assertTrue(srv.cloud_send("bonjour")["ok"])
        self.assertIsNone(srv.repo_access_missing, "this is not the missing app")
        self.assertIn("branch_not_on_remote", srv.github_error)
        self.assertNotIn("app GitHub", srv.github_error)

    def test_a_cli_that_waits_for_an_answer_is_reported_at_once_with_what_it_asks(self):
        # e.g. "do you trust this folder?" in a folder the CLI has never seen: nobody is there to answer, so waiting 3 minutes tells nothing
        os.environ["FAKE_CLAUDE_MODE"] = "prompt"
        original, srv.PROMPT_STALL = srv.PROMPT_STALL, 1
        self.addCleanup(setattr, srv, "PROMPT_STALL", original)
        started = time.time()
        answer = srv.cloud_send("bonjour")
        self.assertFalse(answer["ok"])
        self.assertLess(time.time() - started, 30, "not the 180 s of the timeout")
        self.assertIn("attend une réponse", answer["error"])
        self.assertIn("do you trust the files in this folder", answer["error"])
        self.assertIn("&& claude", answer["error"], "and what to do about it")
        self.assertFalse(srv.cloud_busy)
        self.assertEqual(srv._relay_cloud_state(), "untested", "no session: the relay test was not asked")

    def test_the_widget_can_say_what_a_creation_is_doing_and_a_slow_one_is_not_cut_at_3_minutes(self):
        os.environ["FAKE_CLAUDE_MODE"] = "slow"
        seen = []
        result = []
        sender = threading.Thread(target=lambda: result.append(srv.cloud_send("bonjour")), daemon=True)
        sender.start()
        end = time.time() + 20
        while sender.is_alive() and time.time() < end:
            if "Uploading repository bundle" in srv.cloud_progress:
                seen.append(srv.cloud_progress)
            time.sleep(0.05)
        sender.join(10)
        self.assertTrue(result and result[0]["ok"], result)
        self.assertTrue(seen, "the line the CLI printed was there while it ran")
        self.assertEqual(srv.cloud_progress, "", "and gone when the send ended")

    def test_a_cli_that_goes_silent_is_given_up_after_the_idle_time_and_says_what_it_last_printed(self):
        os.environ["FAKE_CLAUDE_MODE"] = "silent"
        original, srv.CLOUD_IDLE = srv.CLOUD_IDLE, 2
        self.addCleanup(setattr, srv, "CLOUD_IDLE", original)
        started = time.time()
        answer = srv.cloud_send("bonjour")
        self.assertFalse(answer["ok"])
        self.assertLess(time.time() - started, 30)
        self.assertIn("n'a rien affiché depuis 2 s", answer["error"])
        self.assertIn("Uploading repository bundle 10%", answer["error"])
        self.assertFalse(srv.cloud_busy)

    def test_there_is_no_free_conversation_a_new_session_needs_a_repository(self):
        srv.current_repo = None
        answer = srv.cloud_send("bonjour")
        self.assertFalse(answer["ok"])
        self.assertIn("dépôt", answer["error"])
        self.assertFalse(Path(HOME, "last_create_args.json").exists(), "no session was created")
        self.assertFalse(srv.cloud_busy)
        self.assertEqual(srv._relay_cloud_state(), "untested", "the relay test was not asked either")

    def test_a_follow_up_in_a_session_that_has_none_still_goes(self):
        # A session begun before this rule keeps working: its repository was fixed when it started.
        srv.current_repo = None
        srv.current_cloud = "session_TEST123abc"
        self.assertTrue(srv.cloud_send("encore")["ok"])

    def test_a_new_sessions_first_message_carries_the_relay_test_once(self):
        answer = srv.cloud_send("bonjour")
        self.assertTrue(answer["ok"], answer)
        self.assertEqual(answer["session_id"], "session_TEST123abc")
        task = self.sent()["argv"][-1]
        self.assertIn("bonjour", task)
        self.assertIn("Title: aiwa-check", task)
        self.assertEqual(srv._relay_cloud_state(), "pending")
        self.assertEqual(srv.claude_login["state"], "ok")  # a send that worked proves the login
        self.assertAlmostEqual(srv.last_message_at, time.time(), delta=60)  # the "Prêt" chip counts from here
        self.assertAlmostEqual(json.loads(Path(srv.STATE_FILE).read_text())["last_message"], time.time(), delta=60)

    def test_once_the_relay_is_confirmed_a_new_session_does_not_ask_again(self):
        srv._cloud_check_seen()
        self.assertTrue(srv.cloud_send("bonjour")["ok"])
        self.assertNotIn("aiwa-check", self.sent()["argv"][-1])

    def test_a_failed_creation_because_of_the_login_says_so_and_asks_nothing(self):
        os.environ["FAKE_CLAUDE_MODE"] = "fail"
        answer = srv.cloud_send("bonjour")
        self.assertFalse(answer["ok"])
        self.assertEqual(srv.claude_login["state"], "needed")
        self.assertEqual(srv._relay_cloud_state(), "untested")  # no session: the test was not asked

    def test_a_failed_follow_up_because_of_the_login_says_so_too(self):
        srv.current_cloud = "session_TEST123abc"
        os.environ["FAKE_CLAUDE_MODE"] = "fail"
        self.assertFalse(srv.cloud_send("encore")["ok"])
        self.assertEqual(srv.claude_login["state"], "needed")

    def test_the_store_instruction_goes_with_the_first_message(self):
        srv.deploy_mode = "store"
        self.assertTrue(srv.cloud_send("fais une app météo")["ok"])
        task = self.sent()["argv"][-1]
        self.assertIn("Title: aiwa-app", task)
        self.assertIn(f"{srv.NTFY_SERVER}/{srv.waiting_topic}", task)


class RepoStubTests(unittest.TestCase):
    """The repository is only an address: nothing of it is downloaded to the phone, and one folder serves every repository."""

    def run_git(self, directory, *args):
        import subprocess
        return subprocess.run(["git", *args], cwd=directory, capture_output=True, text=True).stdout.strip()

    def test_one_folder_is_an_empty_stub_pointing_at_the_chosen_repository(self):
        folder = Path(tempfile.mkdtemp(prefix="aiwa-stub-")) / "chat-cloud"
        with unittest.mock.patch.object(gh, "SESSION_DIR", folder), \
                unittest.mock.patch.object(gh, "GITHUB_BASE", "https://example.invalid"), \
                unittest.mock.patch.object(gh, "default_branch", lambda repo: "trunk"):
            directory, work, base = gh.prepare_repo_dir("o/r")
            first = self.run_git(directory, "rev-parse", "HEAD")
            self.assertEqual(self.run_git(directory, "remote", "get-url", "origin"), "https://example.invalid/o/r.git")
            other, _, _ = gh.prepare_repo_dir("p/q")
        self.assertEqual(base, "trunk")
        self.assertTrue(work.startswith("aiwa/"))
        self.assertEqual(sorted(p.name for p in directory.iterdir()), [".git"])
        self.assertEqual(other, directory, "the same folder, so the CLI's trust answer holds for every repository")
        self.assertEqual(self.run_git(directory, "remote", "get-url", "origin"), "https://example.invalid/p/q.git")
        self.assertEqual(self.run_git(directory, "rev-list", "--count", "HEAD"), "1")
        self.assertEqual(self.run_git(directory, "rev-parse", "HEAD"), first)

    def test_the_branch_is_known_on_origin_so_the_cli_names_the_repository_instead_of_uploading_the_folder(self):
        import subprocess
        folder = Path(tempfile.mkdtemp(prefix="aiwa-stub-")) / "chat-cloud"
        with unittest.mock.patch.object(gh, "SESSION_DIR", folder), \
                unittest.mock.patch.object(gh, "GITHUB_BASE", "https://example.invalid"), \
                unittest.mock.patch.object(gh, "default_branch", lambda repo: "trunk"):
            directory, _, _ = gh.prepare_repo_dir("o/r")
            first = subprocess.run(["git", "show-ref", "--verify", "--quiet", "refs/remotes/origin/trunk"], cwd=directory)
            with unittest.mock.patch.object(gh, "default_branch", lambda repo: "main"):
                gh.prepare_repo_dir("p/q")
            stale = subprocess.run(["git", "show-ref", "--verify", "--quiet", "refs/remotes/origin/trunk"], cwd=directory)
            now = subprocess.run(["git", "show-ref", "--verify", "--quiet", "refs/remotes/origin/main"], cwd=directory)
        self.assertEqual(first.returncode, 0, "the CLI's own question: is the current branch on the remote?")
        self.assertNotEqual(stale.returncode, 0, "nothing is left of the previous repository")
        self.assertEqual(now.returncode, 0)

    def test_a_folder_left_by_the_first_widget_is_reused_as_it_is(self):
        # ~/chat-cloud of the first widget: a repository with one commit on master, no remote, the user's trust answer given there
        folder = Path(tempfile.mkdtemp(prefix="aiwa-old-")) / "chat-cloud"
        folder.mkdir()
        self.run_git(folder, "init", "-q", "-b", "master")
        self.run_git(folder, "-c", "user.name=a", "-c", "user.email=a@b", "commit", "-q", "--allow-empty", "-m", "old")
        old = self.run_git(folder, "rev-parse", "HEAD")
        with unittest.mock.patch.object(gh, "SESSION_DIR", folder), \
                unittest.mock.patch.object(gh, "GITHUB_BASE", "https://example.invalid"), \
                unittest.mock.patch.object(gh, "default_branch", lambda repo: "main"):
            directory, _, base = gh.prepare_repo_dir("o/r")
        self.assertEqual(directory, folder)
        self.assertEqual(self.run_git(folder, "rev-parse", "--abbrev-ref", "HEAD"), "main")
        self.assertEqual(self.run_git(folder, "rev-parse", "HEAD"), old, "no new commit, nothing deleted")
        self.assertEqual(self.run_git(folder, "remote", "get-url", "origin"), "https://example.invalid/o/r.git")


class BranchSessionTests(unittest.TestCase):
    """Reading a session back from its branch, against real git repositories on disk."""

    def setUp(self):
        import subprocess
        self.base = Path(tempfile.mkdtemp(prefix="aiwa-branches-"))
        self.repo = self.base / "o" / "r.git"
        self.repo.parent.mkdir(parents=True)
        self.work = self.base / "work"

        def git(cwd, *args):
            return subprocess.run(["git", "-c", "user.name=t", "-c", "user.email=t@e", *args], cwd=cwd, capture_output=True, text=True, check=True).stdout

        self.git = git
        git(self.base, "init", "-q", "--bare", "-b", "main", str(self.repo))
        git(self.base, "clone", "-q", str(self.repo), str(self.work))
        git(self.work, "checkout", "-q", "-b", "main")
        (self.work / "a.txt").write_text("a")
        git(self.work, "add", "a.txt")
        git(self.work, "commit", "-q", "-m", "first\n\nClaude-Session: https://claude.ai/code/session_OTHER111")
        git(self.work, "push", "-q", "origin", "main")
        patch = unittest.mock.patch.object(gh, "GITHUB_BASE", "file://" + str(self.base))
        patch.start()
        self.addCleanup(patch.stop)

    def commit_on(self, branch, message, push_main=False):
        self.git(self.work, "checkout", "-q", "-B", branch, "main")
        (self.work / f"{branch.replace('/', '_')}.txt").write_text(message)
        self.git(self.work, "add", "-A")
        self.git(self.work, "commit", "-q", "-m", message)
        self.git(self.work, "push", "-q", "origin", branch)
        if push_main:
            self.git(self.work, "push", "-q", "origin", f"{branch}:main")

    def test_the_session_is_read_from_a_commit_of_the_branch(self):
        self.commit_on("claude/own-1", "work\n\nClaude-Session: https://claude.ai/code/session_OWN222")
        self.assertEqual(gh._branch_session("o/r", "claude/own-1"), ("o/r", "found", "session_OWN222"))

    def test_a_branch_already_pushed_to_main_is_still_read(self):
        # the session pushed its work straight to the default branch: the branch has no commit of its own any more
        self.commit_on("claude/merged-1", "work\n\nClaude-Session: https://claude.ai/code/session_MERGED333", push_main=True)
        self.assertEqual(gh._branch_session("o/r", "claude/merged-1"), ("o/r", "found", "session_MERGED333"))

    def test_a_missing_branch_is_missing(self):
        self.assertEqual(gh._branch_session("o/r", "claude/nope"), ("o/r", "missing", None))


class FakeGithub(http.server.BaseHTTPRequestHandler):
    runs = []       # the workflow_runs the next request gets, newest first
    status = 200

    def log_message(self, *args):
        pass

    def do_GET(self):
        body = json.dumps({"workflow_runs": self.runs}).encode()
        self.send_response(self.status)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def run(run_id, sha, status="completed", conclusion="success", event="push", name="Build", title="a commit", actor="someone", updated="2026-09-30T05:17:06Z"):
    return {"id": run_id, "head_sha": sha, "status": status, "conclusion": conclusion if status == "completed" else None, "event": event,
            "name": name, "display_title": title, "actor": {"login": actor}, "updated_at": updated, "html_url": f"https://github.com/o/r/actions/runs/{run_id}"}


class CiNewsTests(unittest.TestCase):
    """The verdict on the latest commit's runs, and when it is news (the "Prêt" chip)."""

    @classmethod
    def setUpClass(cls):
        cls.api = http.server.ThreadingHTTPServer(("127.0.0.1", 0), FakeGithub)
        threading.Thread(target=cls.api.serve_forever, daemon=True).start()
        gh.GITHUB_API = f"http://127.0.0.1:{cls.api.server_address[1]}"

    @classmethod
    def tearDownClass(cls):
        cls.api.shutdown()

    def setUp(self):
        FakeGithub.runs, FakeGithub.status = [], 200
        srv.current_repo = "o/r"
        srv.ci_seen.clear()
        srv.ci_announced.clear()
        srv.last_message_at = 0
        with srv.site_lock:
            srv.ci_cache.update(repo=None, info=None, at=0.0, busy=False)

    def look(self):
        """One look at the repository, as the backend does every 30 or 150 s."""
        with srv.site_lock:
            srv.ci_cache.update(repo="o/r", info=gh.latest_run("o/r"), at=time.time(), busy=False)
        return srv._ci_snapshot()

    def test_lookups_every_30_s_while_a_result_is_awaited_and_every_150_s_otherwise(self):
        now = 10_000.0
        idle, running, ok = None, {"state": "running"}, {"state": "success"}
        srv.last_message_at = 0
        self.assertEqual(srv._ci_ttl(ok, now), 150, "nothing awaited")
        self.assertEqual(srv._ci_ttl(idle, now), 150)
        self.assertEqual(srv._ci_ttl(running, now), 30, "a run is going")
        srv.last_message_at = int(now) - 60
        self.assertEqual(srv._ci_ttl(ok, now), 30, "a message was sent a minute ago: its result is awaited")
        srv.last_message_at = int(now) - 601
        self.assertEqual(srv._ci_ttl(ok, now), 150, "after 10 minutes it is not awaited any more")

    def test_never_more_than_50_lookups_an_hour_whatever_the_rhythm(self):
        now = 50_000.0
        with srv.site_lock:
            srv.ci_lookups[:] = [now - 10 * i for i in range(50)]      # 50 lookups in the last 500 s
            self.assertFalse(srv._ci_lookup_allowed(now), "the budget is spent")
            srv.ci_lookups[:] = [now - 3600 - 5] + [now - 10 * i for i in range(49)]
            self.assertTrue(srv._ci_lookup_allowed(now), "one fell out of the hour: there is room again")
            self.assertEqual(len(srv.ci_lookups), 49)
            srv.ci_lookups.clear()

    def test_the_runs_of_one_commit_are_one_verdict(self):
        FakeGithub.runs = [run(3, "S2", name="Pages", status="in_progress"), run(2, "S2", name="Build"), run(1, "S1")]
        self.assertEqual(gh.latest_run("o/r")["state"], "running")
        FakeGithub.runs = [run(3, "S2", name="Pages"), run(2, "S2", name="Build"), run(1, "S1")]
        verdict = gh.latest_run("o/r")
        self.assertEqual((verdict["state"], verdict["sha"]), ("success", "S2"))
        self.assertIn("Pages", verdict["detail"])

    def test_a_failed_run_of_the_commit_is_the_verdict_and_the_link(self):
        FakeGithub.runs = [run(3, "S2", name="Pages"), run(2, "S2", name="Build", conclusion="failure"), run(1, "S1")]
        verdict = gh.latest_run("o/r")
        self.assertEqual(verdict["state"], "failure")
        self.assertTrue(verdict["url"].endswith("/2"))

    def test_skipped_runs_do_not_spoil_a_green_commit(self):
        FakeGithub.runs = [run(2, "S2", conclusion="skipped"), run(1, "S2")]
        self.assertEqual(gh.latest_run("o/r")["state"], "success")

    def test_scheduled_and_dynamic_runs_are_not_news(self):
        FakeGithub.runs = [run(9, "X", event="schedule", name="Regenerate"), run(8, "Y", event="dynamic", name="pages build and deployment"), run(7, "S1")]
        self.assertEqual(gh.latest_run("o/r")["sha"], "S1")
        FakeGithub.runs = [run(9, "X", event="schedule")]
        self.assertEqual(gh.latest_run("o/r"), {"state": "none", "url": None})

    def test_an_unreadable_answer_is_not_a_verdict(self):
        FakeGithub.status = 500
        self.assertIsNone(gh.latest_run("o/r"))

    def test_the_first_commit_ever_seen_is_not_news(self):
        FakeGithub.runs = [run(1, "S1")]
        self.assertFalse(self.look()["fresh"])
        self.assertEqual(srv.ci_seen["o/r"], "S1")

    def test_a_new_green_commit_is_news_until_the_user_looked(self):
        FakeGithub.runs = [run(1, "S1")]
        self.look()
        FakeGithub.runs = [run(2, "S2"), run(1, "S1")]
        self.assertTrue(self.look()["fresh"])
        srv._ci_acknowledge()
        self.assertFalse(srv._ci_snapshot()["fresh"])

    def test_a_second_workflow_of_the_same_commit_is_not_news_again(self):
        FakeGithub.runs = [run(1, "S1")]
        self.look()
        FakeGithub.runs = [run(3, "S2", name="Pages", status="in_progress"), run(2, "S2", name="Build")]
        self.assertFalse(self.look()["fresh"])  # still running: not yet
        FakeGithub.runs = [run(3, "S2", name="Pages"), run(2, "S2", name="Build")]
        self.assertTrue(self.look()["fresh"])
        srv._ci_acknowledge()
        FakeGithub.runs = [run(4, "S2", name="Another", event="workflow_dispatch"), run(3, "S2", name="Pages"), run(2, "S2", name="Build")]
        self.assertFalse(self.look()["fresh"])  # the same commit: already looked at

    def test_a_red_commit_is_no_news_and_the_next_green_one_is(self):
        FakeGithub.runs = [run(1, "S1")]
        self.look()
        FakeGithub.runs = [run(2, "S2", conclusion="failure"), run(1, "S1")]
        self.assertFalse(self.look()["fresh"])
        FakeGithub.runs = [run(3, "S3"), run(2, "S2", conclusion="failure"), run(1, "S1")]
        self.assertTrue(self.look()["fresh"])

    def test_runs_finished_before_the_users_last_message_are_not_news(self):
        FakeGithub.runs = [run(1, "S1")]
        self.look()
        srv.last_message_at = gh._epoch("2026-09-30T06:00:00Z")
        FakeGithub.runs = [run(2, "S2", updated="2026-09-30T05:00:00Z"), run(1, "S1")]
        self.assertFalse(self.look()["fresh"])
        FakeGithub.runs = [run(3, "S3", updated="2026-09-30T06:30:00Z"), run(2, "S2"), run(1, "S1")]
        self.assertTrue(self.look()["fresh"])

    def test_a_run_id_kept_by_an_older_version_does_not_make_a_false_news(self):
        Path(srv.STATE_FILE).write_text(json.dumps({"repo": "o/r", "ci_seen": {"o/r": 123456, "x/y": "abc"}}))
        srv._load_state()
        self.assertEqual(srv.ci_seen, {"x/y": "abc"})
        FakeGithub.runs = [run(1, "S1")]
        self.assertFalse(self.look()["fresh"])

    def test_the_detail_says_which_run_it_is(self):
        FakeGithub.runs = [run(1, "S1", name="Build Aiwa APK", title="Store instruction", event="push", actor="theodoreyong9")]
        self.assertEqual(gh.latest_run("o/r")["detail"], "Build Aiwa APK — Store instruction (push, theodoreyong9)")


class RepoListTests(unittest.TestCase):
    """The repository picker's list, and a repository created a moment ago ("Créer un dépôt GitHub") showing up in it."""

    def setUp(self):
        gh._owner_cache.clear()
        with srv.site_lock:
            srv.ci_lookups.clear()
        self.reads = []
        self.listing = [{"full_name": "me/old"}]

        class Reply:
            def __init__(inner, data):
                inner.data = data

            def __enter__(inner):
                return inner

            def __exit__(inner, *args):
                return False

            def read(inner, *args):
                return json.dumps(inner.data).encode()

        def fake_urlopen(request, timeout=None):
            self.reads.append(request.full_url)
            return Reply(self.listing)

        self.patch = unittest.mock.patch.object(gh.urllib.request, "urlopen", fake_urlopen)
        self.patch.start()

    def tearDown(self):
        self.patch.stop()
        gh._owner_cache.clear()
        with srv.site_lock:
            srv.ci_lookups.clear()

    def test_a_new_repository_is_in_the_list_when_the_picker_is_opened_again(self):
        self.assertEqual([r["name"] for r in srv._owner_repos_current("me")], ["me/old"])
        self.listing = [{"full_name": "me/new"}, {"full_name": "me/old"}]
        self.assertEqual([r["name"] for r in srv._owner_repos_current("me")], ["me/old"], "opened twice within 30 s: no new request")
        self.assertEqual(len(self.reads), 1)
        gh._owner_cache["me"] = (time.time() - 31, gh._owner_cache["me"][1])
        self.assertEqual([r["name"] for r in srv._owner_repos_current("me")], ["me/new", "me/old"], "30 s later it is read again")
        self.assertEqual(len(self.reads), 2)

    def test_the_re_reads_count_in_the_hourly_budget_and_stop_when_it_is_spent(self):
        srv._owner_repos_current("me")
        self.assertEqual(len(srv.ci_lookups), 1, "a read is a request of the unauthenticated API: counted")
        now = time.time()
        with srv.site_lock:
            srv.ci_lookups[:] = [now - 10 * i for i in range(srv.CI_LOOKUPS_PER_HOUR)]
        gh._owner_cache["me"] = (now - 60, gh._owner_cache["me"][1])
        self.listing = [{"full_name": "me/new"}]
        self.assertEqual([r["name"] for r in srv._owner_repos_current("me")], ["me/old"], "out of budget: the list already held is used")
        self.assertEqual(len(self.reads), 1)


class HttpTests(Base):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.api = http.server.ThreadingHTTPServer(("127.0.0.1", 0), srv.Handler)
        threading.Thread(target=cls.api.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.api.shutdown()
        super().tearDownClass()

    def call(self, path, body=None):
        url = f"http://127.0.0.1:{self.api.server_address[1]}{path}"
        request = urllib.request.Request(url, data=None if body is None else body.encode(), method="GET" if body is None else "POST")
        with urllib.request.urlopen(request, timeout=60) as reply:
            return json.loads(reply.read())

    def test_status_carries_the_new_fields(self):
        status = self.call("/api/status")
        self.assertEqual(status["version"], srv.BACKEND_VERSION)
        for key in ("claude_login", "relay_cloud", "sent_app"):
            self.assertIn(key, status)
        self.assertEqual(status["relay_cloud"], "untested")

    def test_status_says_a_send_is_running_so_the_widget_can_keep_saying_so(self):
        # The app that sent may be gone (closed, restarted) while the backend still works on the message: the widget reads this, not its own memory.
        started, release = threading.Event(), threading.Event()
        original, session = srv._queue_followup, srv.current_cloud

        def slow_followup(session_id, text):
            if not text.startswith("un message"):      # a background /rename of an earlier test is not the send under test
                return {"ok": True, "url": None, "error": None}
            started.set()
            release.wait(30)
            return {"ok": True, "url": None, "error": None}

        srv._queue_followup, srv.current_cloud = slow_followup, "session_TEST123abc"
        try:
            self.assertFalse(self.call("/api/status")["sending"])
            self.assertIsNone(self.call("/api/status")["sending_since"])
            self.assertIsNone(self.call("/api/status")["sending_note"])
            results = []
            sender = threading.Thread(target=lambda: results.append(srv.cloud_send("un message")), daemon=True)
            sender.start()
            self.assertTrue(started.wait(10))
            self.assertTrue(self.call("/api/status")["sending"])
            self.assertAlmostEqual(self.call("/api/status")["sending_since"], time.time(), delta=30)
            self.assertEqual(srv.cloud_send("un deuxième")["error"], "busy", "a second message is refused while the first is on its way")
            release.set()
            sender.join(10)
            self.assertTrue(results and results[0]["ok"], results)
            self.assertFalse(self.call("/api/status")["sending"])
            self.assertIsNone(self.call("/api/status")["sending_since"])
        finally:
            release.set()
            srv._queue_followup, srv.current_cloud = original, session

    def test_the_status_says_which_repository_has_no_claude_app_and_where_to_install_it(self):
        original = srv.repo_access_missing, srv.current_repo
        self.addCleanup(lambda: setattr(srv, "repo_access_missing", original[0]) or setattr(srv, "current_repo", original[1]))
        srv.current_repo = "o/r"
        srv.repo_access_missing = "o/r"
        status = self.call("/api/status")
        self.assertEqual(status["repo_access_missing"], "o/r")
        self.assertTrue(status["github_app_url"].startswith("https://github.com/apps/claude"))
        srv.current_repo = "p/q"                                   # another repository is chosen: it is not about that one
        self.assertIsNone(self.call("/api/status")["repo_access_missing"])

    def test_repositories_of_inspiration_are_added_removed_and_told_to_claude_as_read_only(self):
        original = list(srv.source_repos)
        self.addCleanup(lambda: srv.source_repos.__setitem__(slice(None), original))
        srv.source_repos.clear()
        added = self.call("/api/github/sources", "https://github.com/someone-else/cool-app/tree/main/src")
        self.assertEqual((added["accepted"], added["added"], added["source_repos"]), (True, True, ["someone-else/cool-app"]))
        self.assertEqual(self.call("/api/status")["source_repos"], ["someone-else/cool-app"])
        text = srv._compose({}, "o/r", "aiwa/x", "main", True)[0]
        self.assertIn("someone-else/cool-app", text)
        self.assertIn("LECTURE SEULE", text)
        self.assertIn("ne les modifie jamais", text)
        refused = self.call("/api/github/sources", "pas un dépôt du tout !")
        self.assertFalse(refused["accepted"])
        removed = self.call("/api/github/sources", "someone-else/cool-app")
        self.assertEqual((removed["added"], removed["source_repos"]), (False, []))
        self.assertNotIn("inspiration", srv._compose({}, "o/r", "aiwa/x", "main", True)[0])
        self.call("/api/github/sources", "a/b")
        self.call("/api/github/sources", "")
        self.assertEqual(srv.source_repos, [], "an empty text empties the list")

    def forget_sessions(self):
        Path(srv.CLOUD_STORE).unlink(missing_ok=True)
        srv.current_cloud = srv.last_cloud = None

    def add_by_branch(self, text, found_id="session_TEST123abc", repos=("o/r",)):
        found = [(name, found_id) for name in repos]
        with unittest.mock.patch.object(gh, "find_branch_session", lambda candidates, branch: (found, [(name, "found") for name in repos])), \
             unittest.mock.patch.object(gh, "default_branch", lambda repo: "main"):
            return self.call("/api/cloud/add", text)

    def test_a_session_is_added_by_its_branch_and_not_by_its_link(self):
        self.addCleanup(self.forget_sessions)
        self.forget_sessions()
        refused = self.call("/api/cloud/add", "https://claude.ai/code/session_01ABCDEFGHIJKLMNOPQR")
        self.assertFalse(refused["accepted"])
        self.assertIn("branche", refused["reason"])
        self.assertEqual(self.call("/api/cloud/sessions"), [], "a link alone adds nothing")

        answer = self.add_by_branch("claude/fix-widget-AbC12")
        self.assertTrue(answer["accepted"], answer)
        entry = srv._session_entry("session_TEST123abc")
        self.assertEqual((entry["repo"], entry["work"], entry["base"]), ("o/r", "claude/fix-widget-AbC12", "main"))
        status = self.call("/api/status")
        self.assertEqual((status["cloud_session"], status["repo"]), ("session_TEST123abc", "o/r"))

    def test_a_branch_found_in_several_repositories_gets_none_of_them_until_the_person_chooses(self):
        # A session can work on several repositories with the same branch name: taking the first that answers was a guess about where
        # Claude pushes. No repository then: the widget asks for one (the red dot), and the session is told which when it is chosen.
        self.addCleanup(self.forget_sessions)
        self.addCleanup(setattr, srv, "current_repo", srv.current_repo)
        self.forget_sessions()
        answer = self.add_by_branch("claude/github-repos-audit-scc738", repos=("a/one", "b/two", "c/three"))
        self.assertTrue(answer["accepted"], answer)
        entry = srv._session_entry("session_TEST123abc")
        self.assertIsNone(entry.get("repo"))
        self.assertEqual(entry["work"], "claude/github-repos-audit-scc738")
        status = self.call("/api/status")
        self.assertEqual((status["cloud_session"], status["repo"]), ("session_TEST123abc", None))
        with unittest.mock.patch.object(gh, "default_branch", lambda repo: "main"):
            self.assertTrue(self.call("/api/repo", "b/two")["accepted"])
        entry = srv._session_entry("session_TEST123abc")
        self.assertEqual((entry["repo"], entry["work"]), ("b/two", "claude/github-repos-audit-scc738"), "its own branch is kept")
        instructions = srv._compose(entry, "b/two", entry["work"], entry["base"], True, entry.get("origin"))[0]
        self.assertIn("c'est sur b/two que tu travailles", instructions)
        self.assertNotIn("a démarré sur", instructions, "where an imported session started is not known, so it is not said")

    def test_a_branch_found_in_one_repository_is_that_repository(self):
        self.addCleanup(self.forget_sessions)
        self.forget_sessions()
        self.assertTrue(self.add_by_branch("claude/x-1", repos=("only/one",))["accepted"])
        self.assertEqual(srv._session_entry("session_TEST123abc")["repo"], "only/one")

    def test_a_branch_with_the_link_of_the_session_uses_that_session(self):
        self.addCleanup(self.forget_sessions)
        self.forget_sessions()
        answer = self.add_by_branch("claude/x-1 https://claude.ai/code/session_OWN999zzz", found_id=None)
        self.assertTrue(answer["accepted"], answer)
        self.assertEqual(answer["cloud_session"], "session_OWN999zzz")
        self.assertEqual(srv._session_entry("session_OWN999zzz")["work"], "claude/x-1")

    def test_a_branch_whose_commits_do_not_name_the_session_is_not_added_alone(self):
        self.addCleanup(self.forget_sessions)
        self.forget_sessions()
        refused = self.add_by_branch("claude/x-1", found_id=None)
        self.assertFalse(refused["accepted"])
        self.assertIn("lien de la session", refused["reason"])
        self.assertEqual(self.call("/api/cloud/sessions"), [])

    def test_the_list_of_sessions_can_be_emptied_and_nothing_else_is(self):
        self.addCleanup(self.forget_sessions)
        self.assertTrue(self.add_by_branch("claude/fix-widget-AbC12")["accepted"])
        self.assertEqual(len(self.call("/api/cloud/sessions")), 1)
        srv.cloud_busy = True
        try:
            self.assertFalse(self.call("/api/cloud/clear", "")["accepted"], "not while a message is on its way")
        finally:
            srv.cloud_busy = False
        self.assertEqual(len(self.call("/api/cloud/sessions")), 1)
        self.assertTrue(self.call("/api/cloud/clear", "")["accepted"])
        self.assertEqual(self.call("/api/cloud/sessions"), [])
        status = self.call("/api/status")
        self.assertIsNone(status["cloud_session"])
        self.assertIsNone(status["last_session"])
        self.assertEqual(status["repo"], "o/r", "the repository chosen stays")

    def test_changing_repository_keeps_the_session_and_tells_it_with_the_next_message(self):
        # "In the same session I only want to change the instruction": like a new push mode or a new model, a new repository does not
        # throw the conversation away. The session is told, once, that it now works there; going back is told too.
        self.addCleanup(self.forget_sessions)
        self.addCleanup(setattr, srv, "current_repo", srv.current_repo)
        self.forget_sessions()
        srv._save_cloud_session("session_MOVE12345", "titre", None, repo="o/r", work="aiwa/1", base="main", direct=True)
        srv.current_cloud, srv.current_repo = "session_MOVE12345", "o/r"
        sent = []
        original = srv._queue_followup
        srv._queue_followup = lambda session_id, text: (sent.append(text), {"ok": True, "url": None, "error": None})[1]
        self.addCleanup(setattr, srv, "_queue_followup", original)
        with unittest.mock.patch.object(gh, "default_branch", lambda repo: "trunk"):
            self.assertTrue(srv.cloud_send("premier")["ok"])
            self.assertIn("Ton répertoire de travail doit être ce dépôt GitHub", sent[-1], "the first message tells the session where it is")
            self.assertTrue(self.call("/api/repo", "p/q")["accepted"])
            status = self.call("/api/status")
            self.assertEqual(status["repo"], "p/q")
            self.assertEqual(status["cloud_session"], "session_MOVE12345", "the session is still the one in progress")
            self.assertTrue(srv.cloud_send("deuxième")["ok"])
        self.assertIn("consignes mises à jour", sent[-1])
        self.assertIn("Ta session a démarré sur o/r", sent[-1])
        self.assertIn("c'est sur p/q que tu travailles", sent[-1])
        entry = srv._session_entry("session_MOVE12345")
        self.assertEqual((entry["repo"], entry["origin"], entry["base"], entry["work"]), ("p/q", "o/r", "trunk", "aiwa/1"))
        # nothing changed since: nothing is repeated
        self.assertTrue(srv.cloud_send("troisième")["ok"])
        self.assertNotIn("consignes", sent[-1])
        # back on the repository it started on: told again, and it is no longer "moved"
        with unittest.mock.patch.object(gh, "default_branch", lambda repo: "main"):
            self.assertTrue(self.call("/api/repo", "o/r")["accepted"])
        self.assertTrue(srv.cloud_send("quatrième")["ok"])
        self.assertIn("Ton répertoire de travail doit être ce dépôt GitHub", sent[-1])
        self.assertNotIn("Ta session a démarré sur", sent[-1])
        self.assertIsNone(srv._session_entry("session_MOVE12345")["origin"])

    def test_with_no_session_in_progress_changing_repository_only_sets_where_the_next_one_starts(self):
        self.addCleanup(setattr, srv, "current_repo", srv.current_repo)
        self.forget_sessions()
        self.assertTrue(self.call("/api/repo", "p/q")["accepted"])
        status = self.call("/api/status")
        self.assertEqual((status["repo"], status["cloud_session"]), ("p/q", None))

    def test_the_repository_cannot_be_unchosen(self):
        self.assertTrue(self.call("/api/repo", "o/r")["accepted"])
        refused = self.call("/api/repo", "")
        self.assertFalse(refused["accepted"])
        self.assertEqual(self.call("/api/status")["repo"], "o/r")

    def test_the_app_reaches_the_widget_app_and_is_marked_seen(self):
        self.assertIsNone(self.call("/api/sent-app")["sent_app"])
        self.assertFalse(self.call("/api/sent-app/code")["ok"])
        srv._app_received(self.app_event())
        self.assertEqual(self.call("/api/status")["sent_app"]["name"], "demo-app.app.html")
        self.assertEqual(self.call("/api/sent-app/code")["code"], APP)
        self.call("/api/sent-app/seen", "")
        self.assertTrue(self.call("/api/status")["sent_app"]["seen"])

    def test_deploy_mode_aiwa_is_accepted_like_store(self):
        self.call("/api/options", json.dumps({"deploy": "aiwa"}))
        self.assertEqual(self.call("/api/status")["deploy"], "aiwa")
        self.assertEqual(self.call("/api/status")["site"]["kind"], "store", "the widget treats both as an app to open in the Store")
        self.call("/api/options", json.dumps({"deploy": "none"}))

    def test_deploy_mode_store_is_accepted_and_the_old_ones_are_not(self):
        self.call("/api/options", json.dumps({"deploy": "store"}))
        self.assertEqual(self.call("/api/status")["deploy"], "store")
        self.assertEqual(self.call("/api/status")["site"]["kind"], "store")
        self.call("/api/options", json.dumps({"deploy": "sphere"}))
        self.assertEqual(self.call("/api/status")["deploy"], "store", "a mode that does not exist changes nothing")
        self.call("/api/options", json.dumps({"deploy": "none"}))

    def test_retest_without_a_session_says_so_and_asks_nothing(self):
        answer = self.call("/api/relay/retest", "")
        self.assertFalse(answer["accepted"])
        self.assertIn("aucune session", answer["reason"])
        self.assertEqual(self.call("/api/status")["relay_cloud"], "untested")

    def test_login_over_http(self):
        started = self.call("/api/claude/login/start", "")
        self.assertEqual(started["phase"], "url")
        self.assertEqual(self.call("/api/claude/login")["phase"], "url")
        done = self.call("/api/claude/login/code", "good#state")
        self.assertEqual(done["phase"], "done")
        self.assertEqual(self.call("/api/claude/check", "")["claude_login"], "ok")


if __name__ == "__main__":
    unittest.main()


class AllowedToolsTests(unittest.TestCase):
    def test_the_tools_the_instructions_ask_for_are_allowed_from_the_start(self):
        import aiwa_server as srv
        args = srv.CLOUD_ALLOWED_TOOLS_ARGS
        self.assertEqual(args[0], "--allowedTools")
        self.assertIn("mcp__Claude_Code_Remote__add_repo", args[1].split(","))
        # only what attaching a repository needs: no other tool of that server, no shell
        self.assertEqual(set(args[1].split(",")), {"mcp__Claude_Code_Remote__add_repo", "mcp__Claude_Code_Remote__register_repo_root"})


class DefaultBranchTests(unittest.TestCase):
    def test_an_unreadable_default_branch_is_none_and_the_old_helper_still_says_main(self):
        with unittest.mock.patch.object(gh, "_git", side_effect=gh.GithubError("offline")):
            self.assertIsNone(gh.default_branch_or_none("o/r"))
            self.assertEqual(gh.default_branch("o/r"), "main")

    def test_the_default_branch_is_read_from_the_remote_head(self):
        with unittest.mock.patch.object(gh, "_git", return_value="ref: refs/heads/aiwa/2026\tHEAD\nabc\tHEAD\n"):
            self.assertEqual(gh.default_branch_or_none("o/r"), "aiwa/2026")

    def test_the_push_instruction_forbids_asking_for_repository_settings(self):
        text = dict(srv._instruction_lines("o/r", "aiwa/x", "main", True))["push"]
        self.assertIn("Ne me demande jamais de changer un réglage du dépôt", text)


class PagesHintTests(unittest.TestCase):
    def test_a_branch_the_environment_refuses_is_said_with_its_steps_and_its_page(self):
        import aiwa_github as gh
        hint = gh.pages_hint(['Branch "main" is not allowed to deploy to github-pages due to environment protection rules.'], "o/r")
        self.assertEqual(hint["code"], "pages_branch")
        self.assertEqual(hint["url"], "https://github.com/o/r/settings/environments")
        self.assertGreaterEqual(len(hint["steps"]), 3)

    def test_pages_not_turned_on_is_said_with_the_settings_page(self):
        import aiwa_github as gh
        hint = gh.pages_hint(["Get Pages site failed. Please verify that the repository has Pages enabled and configured to build using GitHub Actions"], "o/r")
        self.assertEqual(hint["code"], "pages_off")
        self.assertEqual(hint["url"], "https://github.com/o/r/settings/pages")

    def test_any_other_failure_has_no_hint(self):
        import aiwa_github as gh
        self.assertIsNone(gh.pages_hint(["Process completed with exit code 1."], "o/r"))
        self.assertIsNone(gh.pages_hint([], "o/r"))

    def test_the_hint_goes_with_a_failed_run(self):
        import aiwa_github as gh
        from unittest import mock
        runs = {"workflow_runs": [{"id": 7, "event": "push", "status": "completed", "conclusion": "failure", "head_sha": "abc", "name": "Deploy Pages",
                                   "display_title": "t", "html_url": "u", "actor": {"login": "x"}, "updated_at": "2026-01-01T00:00:00Z"}]}
        def fake(path):
            if "/actions/runs?" in path: return runs
            if path.endswith("/jobs"): return {"jobs": [{"id": 9, "conclusion": "failure"}]}
            return [{"message": 'Branch "main" is not allowed to deploy to github-pages due to environment protection rules.'}]
        gh._hints.clear()
        with mock.patch.object(gh, "_get_json", side_effect=fake), mock.patch("urllib.request.urlopen") as opened:
            opened.return_value.__enter__.return_value.read.return_value = json.dumps(runs).encode()
            with mock.patch.object(gh.json, "load", return_value=runs):
                result = gh.latest_run("o/r")
        self.assertEqual(result["state"], "failure")
        self.assertEqual(result["hint"]["code"], "pages_branch")


class CiWatchAfterFailureTests(unittest.TestCase):
    def test_lookups_stay_quick_for_a_while_after_a_failure_then_go_back_to_the_idle_pace(self):
        import aiwa_server as srv
        now = 10_000.0
        recent = {"state": "failure", "failure_since": now - 60}
        old = {"state": "failure", "failure_since": now - srv.CI_FAILURE_WATCH_SECONDS - 1}
        with unittest.mock.patch.object(srv, "last_message_at", 0):
            self.assertEqual(srv._ci_ttl(recent, now), srv.CI_POLL_FAST)
            self.assertEqual(srv._ci_ttl(old, now), srv.CI_POLL_IDLE)
            self.assertEqual(srv._ci_ttl({"state": "success"}, now), srv.CI_POLL_IDLE)
