import os, sys, tempfile, unittest, wave, io
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import aiwa_voice as v


class VoiceTest(unittest.TestCase):
    def test_wav_is_a_16_bit_mono_file_with_the_right_length(self):
        data = v.to_wav([0.0, 0.5, -0.5, 2.0, -2.0], 22050)
        w = wave.open(io.BytesIO(data))
        self.assertEqual((w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()), (1, 2, 22050, 5))
        frames = w.readframes(5)
        self.assertEqual(int.from_bytes(frames[6:8], "little", signed=True), 32767)    # clipped, not wrapped
        self.assertEqual(int.from_bytes(frames[8:10], "little", signed=True), -32767)

    def test_nothing_is_said_when_the_voice_is_not_installed(self):
        with tempfile.TemporaryDirectory() as home:
            voice = v.Voice(home)
            self.assertEqual(voice.state, "none")
            self.assertIsNone(voice.say("Bonjour"))
            self.assertFalse(v.model_present(home))


if __name__ == "__main__":
    unittest.main()
