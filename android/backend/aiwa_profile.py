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


TEMPLATE_URL = "https://raw.githubusercontent.com/theodoreyong9/Aiwa_store/main/Profile/index.html"
HTML_MAX = 300_000          # bytes: the page itself
MEDIA_MAX = 8_000_000       # bytes: one video, GIF or picture
MEDIA_TOTAL = 15_000_000    # bytes: all of `Profile/media/`


def instruction(repo_name=None):
    """What a session is told about the profile folder (and, when a repository is chosen, which one it is): what it is, and the minimum of quality a profile must reach."""
    text = (
        f"Profil : le dossier `{FOLDER}/` d'un dépôt est la page de la personne, une production comme les autres. Quand elle dit qu'on travaille sur son profil, tu écris `{FOLDER}/index.html` : UN fichier HTML autonome, "
        "beau et travaillé. Niveau MINIMAL, que tu vérifies avant de dire que c'est fait : "
        "(1) le contenu : son nom, une phrase qui dit ce qu'elle fait, les liens publics de ses fichiers (ceux de la session en cours), au moins un moyen de la joindre (lien `mailto:`, `tel:` ou https) que SEULE elle a donné, jamais inventé ; "
        "(2) la forme : `<title>`, `<meta name=\"viewport\">`, une page qui se lit bien sur un téléphone (une colonne, texte de 16 px au moins, contraste fort), un fond animé SOMBRE (une vidéo `<video autoplay muted loop playsinline>` ou un GIF, avec `poster`) pour que le texte reste lisible, "
        f"du mouvement en CSS plutôt qu'en script ; (3) aucun `<script>` du tout (la page est lue sans script) et aucune ressource en `http://` ; "
        f"(4) le poids : la page {HTML_MAX // 1000} Ko au plus, chaque média {MEDIA_MAX // 1_000_000} Mo au plus, tous les médias {MEDIA_TOTAL // 1_000_000} Mo au plus, rangés dans `{FOLDER}/media/`, appelés par des chemins relatifs. "
        "C'est un site : suis donc la consigne Design ci-dessus comme pour tout site (références mesurées du catalogue avec `--format=site`, rien de recopié, la page ouverte en 390 px et en 1440 px avec Playwright et ses captures regardées, "
        "les débordements, contrastes faibles et erreurs de console corrigés, `aiwa-design/references.md` écrit dans le dépôt). Vise l'expérimental et le soigné : un profil ordinaire n'atteint pas le niveau. "
        f"Pars du niveau de la page de référence ({TEMPLATE_URL}) : lis-la d'abord. Le fichier n'est pas publié : Aiwa le lit tel quel dans le dépôt (public)."
    )
    if repo_name:
        text += f" Son dépôt du profil est {repo_name} : c'est là, et seulement là, que va ce dossier ; tu travailles dans ce dépôt quand elle parle de son profil."
    return text


def check(html, media=None):
    """The minimum of a profile: the problems found in the page (its text) and in the sizes of its media ({path: bytes}), as plain sentences; an empty list when it is good."""
    problems = []
    low = html.lower()
    media = media or {}
    if len(html.encode("utf-8")) > HTML_MAX:
        problems.append(f"La page pèse plus de {HTML_MAX // 1000} Ko.")
    if "<title>" not in low:
        problems.append("Il manque le <title>.")
    if 'name="viewport"' not in low and "name='viewport'" not in low:
        problems.append("Il manque la balise viewport : la page ne se lira pas bien sur un téléphone.")
    if "<script" in low:
        problems.append("La page contient un <script> : elle est lue sans script, retire-le.")
    if re.search(r"""(?:src|href|url)\s*[=(]\s*["']?http://""", low):
        problems.append("Une ressource est en http:// : tout doit être en https ou en chemin relatif.")
    if "<h1" not in low:
        problems.append("Il manque le nom en titre (<h1>).")
    if not re.search(r"""href\s*=\s*["'](?:https://|mailto:|tel:)""", low):
        problems.append("Aucun lien : au moins les fichiers publics et un moyen de te joindre.")
    if "<video" not in low and "background" not in low:
        problems.append("Pas de fond : une vidéo, un GIF ou une image sombre derrière le texte.")
    for path, size in media.items():
        if size > MEDIA_MAX:
            problems.append(f"{path} pèse plus de {MEDIA_MAX // 1_000_000} Mo.")
    if sum(media.values()) > MEDIA_TOTAL:
        problems.append(f"Les médias pèsent plus de {MEDIA_TOTAL // 1_000_000} Mo en tout.")
    return problems
