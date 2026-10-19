"""Writes docs/consignes/*.md (the fixed instructions of the sessions) from the code of the brain: `python3 android/backend/gen_consignes.py`.
The test `ConsignesTests` fails when the files are not the ones the code makes."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import aiwa_server  # noqa: E402

target = Path(__file__).resolve().parents[2] / "docs" / "consignes"
target.mkdir(parents=True, exist_ok=True)
for name, text in aiwa_server.consignes_documents().items():
    (target / name).write_text(text, encoding="utf-8")
    print("écrit", target / name)
