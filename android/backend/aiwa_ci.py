"""The workflows that failed in the person's repositories, for the report of the super agent: what the latest run of each workflow says, the step that failed, and whether it looks like the infrastructure
(an install, a download, a login: it can be run again) or a real problem (a test, a build). Pure functions; the requests are made by the server."""
import re
import time

INFRA = re.compile(r"install|set ?up|checkout|cache|download|apt|playwright|login|auth|docker|runner|network|upload|artifact|restore|pull|fetch|dependenc|--with-deps", re.I)


def kind(step, job=""):
    """"infra" when the step that failed is one of those that prepare the machine, else "real"."""
    if step:
        return "infra" if INFRA.search(step) else "real"
    return "infra" if INFRA.search(job or "") else "real"


def failed_step(jobs):
    """(job name, step name) of the first failed step of a run's jobs (the GitHub API answer), or ("", "")."""
    for job in (jobs or {}).get("jobs", []) if isinstance(jobs, dict) else []:
        if job.get("conclusion") != "failure":
            continue
        for step in job.get("steps", []) or []:
            if step.get("conclusion") == "failure":
                return str(job.get("name") or ""), str(step.get("name") or "")
        return str(job.get("name") or ""), ""
    return "", ""


def latest_failures(runs, now=None):
    """The runs whose LATEST verdict (per workflow and branch) is a failure: [{"workflow", "branch", "sha", "id", "age_min"}]. Older failures that a later run fixed are not problems any more."""
    now = now or time.time()
    seen, out = set(), []
    for run in (runs or {}).get("workflow_runs", []) if isinstance(runs, dict) else []:
        key = (run.get("name"), run.get("head_branch"))
        if key in seen:
            continue
        seen.add(key)
        if run.get("conclusion") != "failure":
            continue
        made = run.get("created_at") or ""
        try:
            age = max(0, int((now - time.mktime(time.strptime(made, "%Y-%m-%dT%H:%M:%SZ")) + time.timezone) // 60))
        except ValueError:
            age = None
        out.append({"workflow": str(run.get("name") or "?"), "branch": str(run.get("head_branch") or "?"), "sha": str(run.get("head_sha") or "")[:7], "id": run.get("id"), "age_min": age, "attempt": int(run.get("run_attempt") or 1)})
    return out


def digest(items, skipped=()):
    """The block added to the point: failed workflows by repository, or that none failed, and the repositories that could not be read."""
    lines = []
    for it in items:
        age = it.get("age_min")
        when = "" if age is None else (f", il y a {age} min" if age < 120 else f", il y a {age // 60} h")
        why = "probablement l'infrastructure (relançable)" if it.get("kind") == "infra" else "probablement un vrai problème (test ou build)"
        if it.get("kind") == "infra" and (it.get("attempt") or 1) > 1:
            why = "l'infrastructure, mais ça a déjà échoué après relance : ce n'est pas un hasard (l'environnement a peut-être changé, par exemple la version du système du runner)"
        step = f"étape « {it['step']} » du job « {it['job']} »" if it.get("step") else (f"job « {it['job']} »" if it.get("job") else "étape inconnue")
        lines.append(f"- {it['repo']} : {it['workflow']} sur {it['branch']} (commit {it['sha']}{when}) : {step} ; {why}")
    head = "Workflows en échec (dernier verdict de chaque workflow) :" if lines else "Aucun workflow en échec dans tes dépôts lisibles."
    out = [head, *lines]
    if skipped:
        out.append("Dépôts non lus (privés, hors ligne ou limite de GitHub atteinte) : " + ", ".join(skipped))
    return "\n".join(out)
