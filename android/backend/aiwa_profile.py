"""The profile: a folder `Profile/` of ONE repository the person chose in the settings. It is a production like the others (documents, media, page): the super agent hears « on travaille sur mon profil », the session
writes the page there (`Profile/index.html`, as complex as wanted: a video or a GIF behind, the files in front, the ways to reach her), and GitHub Pages publishes it. Aiwa writes nothing itself: it only remembers which
repository and tells the sessions what the folder is for. Nothing is published: the app reads `Profile/index.html` from the repository as it is (a public one), and shows it."""
import json
import re
from pathlib import Path

PROFILE_FILE = Path.home() / ".aiwa_profile.json"
FOLDER = "Profile"
REPO_RE = re.compile(r"[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}")


def load():
    try:
        data = json.loads(PROFILE_FILE.read_text(encoding="utf-8"))
        return data if isinstance(data, dict) else {}
    except (OSError, ValueError):
        return {}


def repo():
    value = load().get("repo")
    return value if isinstance(value, str) and REPO_RE.fullmatch(value) else None


def set_repo(value):
    """Remembers the repository of the profile; False when it is not « owner/name »."""
    value = " ".join(str(value or "").split())
    if not REPO_RE.fullmatch(value):
        return False
    data = load()
    data["repo"] = value
    PROFILE_FILE.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return True


def instruction(repo_name=None):
    """What a session is told about the profile folder (and, when a repository is chosen, which one it is)."""
    text = (
        f"Profil : le dossier `{FOLDER}/` d'un dépôt est la page de la personne, une production comme les autres. Quand elle dit qu'on travaille sur son profil, tu écris `{FOLDER}/index.html` : UN fichier HTML autonome "
        f"(HTML et CSS, pas de script venu d'ailleurs), beau et travaillé ; en fond une vidéo ou un GIF de la meilleure qualité possible rangé dans `{FOLDER}/media/` (ou une image), sombre pour que le texte reste lisible ; "
        "au premier plan sa photo, son texte, et les fichiers de la session en cours (leurs liens publics) présentés avec style ; ses contacts renseignés en liens (mailto:, tel:, https:). Le fichier n'est pas publié : Aiwa le lit tel quel dans le dépôt (public), avec les médias rangés à côté, par des chemins relatifs."
    )
    if repo_name:
        text += f" Son dépôt du profil est {repo_name} : c'est là, et seulement là, que va ce dossier ; tu travailles dans ce dépôt quand elle parle de son profil."
    return text
