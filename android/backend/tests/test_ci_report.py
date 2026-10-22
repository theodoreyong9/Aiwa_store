"""The report checks the workflows that failed in the person's repositories: only the latest verdict of each workflow counts, the failed step is named, and the infrastructure is told apart."""
import sys
import time
import unittest
import unittest.mock
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import aiwa_ci as ci  # noqa: E402

NOW = time.mktime(time.strptime("2026-10-22T10:00:00", "%Y-%m-%dT%H:%M:%S")) - time.timezone


def run(i, name, branch, conclusion, made="2026-10-22T09:00:00Z"):
    return {"id": i, "name": name, "head_branch": branch, "head_sha": "abcdef1234567", "conclusion": conclusion, "created_at": made}


class CiReportTests(unittest.TestCase):
    def test_only_the_latest_verdict_of_each_workflow_counts(self):
        runs = {"workflow_runs": [run(3, "CI", "clean", "success"), run(2, "CI", "clean", "failure"), run(1, "Android", "clean", "failure")]}
        fails = ci.latest_failures(runs, now=NOW)
        self.assertEqual([f["workflow"] for f in fails], ["Android"])
        self.assertEqual(fails[0]["age_min"], 60)

    def test_the_failed_step_is_named_and_the_install_is_the_infrastructure(self):
        jobs = {"jobs": [{"name": "test", "conclusion": "failure", "steps": [{"name": "Checkout", "conclusion": "success"}, {"name": "Run npx playwright install --with-deps chromium", "conclusion": "failure"}]}]}
        job, step = ci.failed_step(jobs)
        self.assertEqual((job, ci.kind(step, job)), ("test", "infra"))
        self.assertEqual(ci.kind("Run unit tests", "test"), "real")

    def test_the_digest_says_what_failed_or_that_nothing_did(self):
        item = {"repo": "me/app", "workflow": "CI", "branch": "clean", "sha": "abcdef1", "age_min": 30, "job": "test", "step": "Run npx playwright install", "kind": "infra"}
        text = ci.digest([item], skipped=["me/private"])
        self.assertIn("étape « Run npx playwright install » du job « test »", text)
        self.assertIn("l'infrastructure (relançable)", text)
        self.assertIn("me/private", text)
        self.assertIn("Aucun workflow en échec", ci.digest([]))

    def test_the_report_request_asks_for_it(self):
        import aiwa_agent
        self.assertIn("workflows en échec", aiwa_agent.REPORT_REQUEST)


if __name__ == "__main__":
    unittest.main()
