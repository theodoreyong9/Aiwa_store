"""The natural voice of the liaison agent: the Piper voice "siwis medium" (French) through sherpa-onnx, on the CPU of the phone's Linux (Termux). No account, no key, no network once installed
(about 80 MB, installed once from PyPI and the sherpa-onnx releases). The app asks for a sentence and gets a WAV; if anything is missing it falls back on the phone's own voice.
Pure standard library apart from sherpa_onnx itself, which is imported only when a sentence is made."""
import os
import subprocess
import sys
import tarfile
import threading
import urllib.request
import wave

NAME = "vits-piper-fr_FR-siwis-medium"
URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/" + NAME + ".tar.bz2"
MAX_CHARS = 600


def model_dir(home=None):
    return os.path.join(home or os.path.expanduser("~"), ".cache", "aiwa-video", NAME)


def model_present(home=None):
    return os.path.exists(os.path.join(model_dir(home), "fr_FR-siwis-medium.onnx"))


def to_wav(samples, rate):
    """Floats in [-1, 1] as a 16-bit mono WAV (bytes)."""
    import io
    pcm = bytearray()
    for x in samples:
        v = int(max(-1.0, min(1.0, float(x))) * 32767)
        pcm += v.to_bytes(2, "little", signed=True)
    out = io.BytesIO()
    with wave.open(out, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(int(rate))
        w.writeframes(bytes(pcm))
    return out.getvalue()


class Voice:
    """state: none (not installed) / installing / ready / error."""

    def __init__(self, home=None):
        self.home = home
        self.state = "ready" if model_present(home) and self._library() else "none"
        self.error = None
        self._tts = None
        self._lock = threading.Lock()

    @staticmethod
    def _library():
        try:
            import sherpa_onnx  # noqa: F401
            return True
        except Exception:
            return False

    def install(self):
        """Starts the installation in the background (once); the state tells where it is."""
        with self._lock:
            if self.state in ("installing", "ready"):
                return
            self.state, self.error = "installing", None
        threading.Thread(target=self._install, daemon=True).start()

    def _install(self):
        try:
            if not self._library():
                subprocess.run([sys.executable, "-m", "pip", "install", "-q", "sherpa-onnx"], check=True, capture_output=True, timeout=1500)
            if not model_present(self.home):
                target = os.path.dirname(model_dir(self.home))
                os.makedirs(target, exist_ok=True)
                archive = os.path.join(target, NAME + ".tar.bz2")
                urllib.request.urlretrieve(URL, archive)
                with tarfile.open(archive, "r:bz2") as tar:
                    for member in tar.getmembers():   # nothing may land outside the folder
                        if os.path.isabs(member.name) or ".." in member.name.split("/"):
                            raise ValueError("archive refusée : " + member.name)
                    tar.extractall(target)
                os.remove(archive)
            if not (model_present(self.home) and self._library()):
                raise RuntimeError("installation incomplète")
            self.state = "ready"
        except Exception as err:
            detail = getattr(err, "stderr", b"") or b""
            self.state, self.error = "error", (str(err) + " " + detail.decode("utf-8", "replace")[-200:]).strip()

    def say(self, text):
        """The sentence as WAV bytes, or None when the voice is not ready or fails."""
        text = " ".join(str(text or "").split())[:MAX_CHARS]
        if not text or self.state != "ready":
            return None
        try:
            with self._lock:
                if self._tts is None:
                    import sherpa_onnx as so
                    d = model_dir(self.home)
                    cfg = so.OfflineTtsConfig(model=so.OfflineTtsModelConfig(vits=so.OfflineTtsVitsModelConfig(
                        model=os.path.join(d, "fr_FR-siwis-medium.onnx"), tokens=os.path.join(d, "tokens.txt"), data_dir=os.path.join(d, "espeak-ng-data")), num_threads=2), max_num_sentences=1)
                    self._tts = so.OfflineTts(cfg)
                audio = self._tts.generate(text, sid=0, speed=1.0)
            return to_wav(audio.samples, audio.sample_rate)
        except Exception as err:
            self.state, self.error = "error", str(err)
            return None
