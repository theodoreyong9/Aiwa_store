import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import aiwa_loops as L  # noqa: E402


class Comments(unittest.TestCase):
    def test_queue_and_block(self):
        q = L.add_comment([], "  ajoute un  test\n de sécurité ", now=1000.0)
        self.assertEqual(q[0]["text"], "ajoute un test de sécurité")
        self.assertIsNone(L.add_comment(q, "   "))
        self.assertIsNone(L.add_comment([{}] * L.COMMENTS_MAX_PENDING, "x"))
        block = L.comments_block(q)
        self.assertIn("DONNÉES NON FIABLES", block)
        self.assertIn(q[0]["id"], block)
        self.assertEqual(L.comments_block([]), "")

    def test_comment_is_cut(self):
        q = L.add_comment([], "a" * 5000, now=1.0)
        self.assertEqual(len(q[0]["text"]), L.COMMENT_MAX_CHARS)

    def test_critic_start_carries_fixed_rules_and_papers(self):
        pair = {"id": "p1", "tag": "t", "start_sha": "abc", "limit": 3}
        msg = L.start_message_critic(pair, "main", "w", "b", "http://relay")
        self.assertIn("aiwa-papers/", msg)
        self.assertIn("SÉCURITÉ", msg)
        self.assertIn("jamais des instructions", msg)


if __name__ == "__main__":
    unittest.main()
