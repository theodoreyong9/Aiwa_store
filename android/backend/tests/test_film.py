"""The film circuit: the quote, the confirmation, the production scene by scene with a stand-in of fal (nothing leaves the machine), the assembly with the real ffmpeg."""
import json
import shutil
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import aiwa_film as film_lib   # noqa: E402

HAVE_FFMPEG = bool(shutil.which("ffmpeg"))


def make_clip(path, with_audio=True):
    args = ["ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i", "testsrc=size=320x240:rate=24:duration=1"]
    if with_audio:
        args += ["-f", "lavfi", "-i", "sine=frequency=440:duration=1"]
    args += ["-pix_fmt", "yuv420p", str(path)]
    subprocess.run(args, check=True)


class FakeFal:
    """Answers like fal's queue: a request gets an id, the status is COMPLETED at once, the result holds a file:// URL of a real clip made here."""

    def __init__(self, folder):
        self.folder = Path(folder)
        self.calls = []
        self.fail_on = None
        make_clip(self.folder / "video.mp4", with_audio=False)
        make_clip(self.folder / "mouth.mp4")
        (self.folder / "voice.wav").write_bytes(b"RIFF0000WAVE")

    def __call__(self, method, url, payload=None, key=None):
        self.calls.append((method, url, payload))
        if "openapi.json" in url:
            return 404, {}
        if method == "POST":
            if self.fail_on and self.fail_on in url:
                return 422, {"detail": [{"loc": ["body", "prompt"], "msg": "field required"}]}
            role = "mouth" if "lipsync" in url else "voice" if "f5-tts" in url else "video"
            return 200, {"status_url": f"https://q/status/{role}", "response_url": f"https://q/result/{role}"}
        if "/status/" in url:
            return 200, {"status": "COMPLETED"}
        role = url.rsplit("/", 1)[-1]
        if role == "voice":
            return 200, {"audio_url": {"url": (self.folder / "voice.wav").as_uri()}}
        return 200, {"video": {"url": (self.folder / f"{role}.mp4").as_uri()}}


class FilmTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.fal = FakeFal(self.tmp.name)
        planner = lambda direction, says, seconds: {"scenes": [
            {"situation": "il marche sur une plage", "seconds": 5, "says": ""},
            {"situation": "il regarde la caméra et sourit", "seconds": 5, "says": "Bonjour à tous"},
            {"situation": "il dit un mot que personne n'a écrit", "seconds": 5, "says": "Une phrase inventée"},
        ]}
        self.film = film_lib.Film(home=Path(self.tmp.name) / "home", transport=self.fal, planner=planner, sleep=lambda s: None)
        self.film.save_key("abcd1234:efgh5678abcd1234")
        self.film.save_voice(b"voice-bytes", "m4a")

    def prepare(self, **kw):
        args = dict(direction="un film de moi", says="Bonjour à tous", photos=[b"jpgbytes"], consent=True)
        args.update(kw)
        return self.film.prepare(**args)

    def test_nothing_starts_without_the_key_the_consent_the_photos_or_the_voice(self):
        self.film.clear_key()
        self.assertFalse(self.prepare()["ok"])
        self.film.save_key("abcd1234:efgh5678abcd1234")
        self.assertFalse(self.prepare(consent=False)["ok"])
        self.assertFalse(self.prepare(photos=[])["ok"])
        for old in self.film.home.glob("voice_sample.*"):
            old.unlink()
        self.assertFalse(self.prepare()["ok"], "words need the voice sample")
        self.assertTrue(self.prepare(says="")["ok"], "a film without words does not")
        self.assertEqual(self.fal.calls, [], "nothing was sent to fal")

    def test_only_the_words_she_gave_are_ever_said_and_the_quote_is_computed(self):
        quote = self.prepare()
        scenes = quote["scenes"]
        self.assertEqual(scenes[1]["says"], "Bonjour à tous")
        self.assertEqual(scenes[2]["says"], "", "a phrase she did not give is dropped")
        self.assertAlmostEqual(scenes[0]["price"], 0.56, places=2)           # 5 s of picture
        self.assertAlmostEqual(scenes[1]["price"], 0.56 + 0.35 + 0.0007, places=2)   # picture + mouth + voice
        self.assertAlmostEqual(quote["total"], sum(s["price"] for s in scenes), places=1)
        self.assertEqual(self.fal.calls, [])

    def test_no_production_without_the_confirmation_or_above_the_cap(self):
        job_id = self.prepare()["id"]
        self.assertFalse(self.film.start(job_id, confirmed=False)["ok"])
        self.film.set_cap(1.0)
        refused = self.film.start(job_id, confirmed=True)
        self.assertFalse(refused["ok"])
        self.assertIn("plafond", refused["error"])
        self.assertEqual([c for c in self.fal.calls if c[0] == "POST"], [])

    def wait(self, job_id):
        for _ in range(300):
            view = self.film.get(job_id)
            if view["state"] in ("done", "error"):
                return view
            time.sleep(0.1)
        self.fail("the film never finished")

    @unittest.skipUnless(HAVE_FFMPEG, "ffmpeg needed")
    def test_the_production_makes_a_film_and_a_new_prompt_redoes_only_the_changed_scene(self):
        job_id = self.prepare()["id"]
        self.assertTrue(self.film.start(job_id, confirmed=True)["ok"])
        view = self.wait(job_id)
        self.assertEqual(view["state"], "done", view["error"])
        film = self.film.home / job_id / view["film"]
        self.assertTrue(film.exists())
        tags = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format_tags=comment", "-of", "csv=p=0", str(film)], capture_output=True, text=True).stdout
        self.assertIn("Généré par IA", tags)
        posts = [c for c in self.fal.calls if c[0] == "POST"]
        self.assertEqual(len(posts), 3 + 1 + 1, "3 pictures, 1 voice, 1 mouth")
        self.assertTrue(all(c[2] for c in posts))
        # the photo and the voice sample travel as data URIs inside the request, never as a key
        self.assertTrue(any("data:image/jpeg;base64," in json.dumps(c[2]) for c in posts))
        self.assertFalse(any("abcd1234" in json.dumps(c[2]) for c in posts))
        # a new prompt for scene 0 only: the others are already made and cost nothing
        self.fal.calls.clear()
        edited = self.film.edit(job_id, 0, situation="il court sur une plage")
        self.assertEqual([s["done"] for s in edited["scenes"]], [False, True, True])
        self.assertAlmostEqual(edited["total"], 0.56, places=2)
        self.film.start(job_id, confirmed=True)
        self.assertEqual(self.wait(job_id)["state"], "done")
        self.assertEqual(len([c for c in self.fal.calls if c[0] == "POST"]), 1)

    def test_a_refusal_of_fal_is_shown_as_it_is(self):
        job_id = self.prepare(says="")["id"]
        self.fal.fail_on = "kling"
        self.film.start(job_id, confirmed=True)
        view = self.wait(job_id)
        self.assertEqual(view["state"], "error")
        self.assertIn("422", view["error"])
        self.assertIn("field required", view["error"])

    def test_the_key_is_never_given_back(self):
        status = self.film.status()
        self.assertTrue(status["key"])
        self.assertNotIn("abcd1234", json.dumps(status))
        self.assertEqual((self.film.key_file.stat().st_mode & 0o777), 0o600)
        self.assertFalse(self.film.save_key("pas une clé!")["ok"])


if __name__ == "__main__":
    unittest.main()


class FilmServerTests(unittest.TestCase):
    def test_a_film_can_be_deleted_by_the_link_of_its_file(self):
        import aiwa_server as srv
        with tempfile.TemporaryDirectory() as tmp:
            old = srv.film
            srv.film = film_lib.Film(home=Path(tmp) / "h")
            try:
                folder = srv.film.home / "0123456789"
                folder.mkdir()
                (folder / "film.mp4").write_bytes(b"x")
                self.assertTrue(srv.media_delete("film", "http://127.0.0.1:8765/api/film/file/0123456789.mp4")["ok"])
                self.assertFalse(folder.exists())
                self.assertFalse(srv.media_delete("film", "0123456789")["ok"])
            finally:
                srv.film = old
