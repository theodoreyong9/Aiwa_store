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
READ_MAX = 2_000_000        # bytes of the page the brain reads to check it (a limit of reading, not of making)


def instruction(repo_name=None):
    """What a session is told about the profile folder (and, when a repository is chosen, which one it is): what a profile is FOR, and the little it must have."""
    text = (
        f"Profil : le dossier `{FOLDER}/` d'un dépôt est la page de la personne, une production comme les autres, et la VITRINE d'Aiwa : en 10 secondes elle doit montrer tout ce qu'Aiwa sait faire de mieux en création de site ET de média. "
        f"Quand elle dit qu'on travaille sur son profil, tu écris `{FOLDER}/index.html` (médias et bibliothèques à côté) : une page ET une animation, pas un CV. "
        "Un profil est un FORMAT, comme un avatar : l'essentiel tient dans le premier écran (le nom, une phrase, les boutons à portée du pouce, un fond qui vit), mais ne bloque JAMAIS le défilement (pas d'`overflow:hidden` sur html ni body) : la personne et ses visiteurs font défiler si elles le veulent, et rien n'est coupé. "
        "Vérifie à 360×640, 390×844, 844×390, 1024×1366 et 1440×900 : aucun bouton hors de l'écran, rien de coupé, tout s'atteint en défilant. "
        "AUCUNE limite technique : tout ce qu'un site sait faire, tu peux le faire (WebGL, canvas, SVG, scènes 3D, bibliothèques comme Three.js ou GSAP, par CDN ou copiées dans le dépôt, scripts, vidéos lourdes, son, mouvement au défilement, au doigt, à l'inclinaison). "
        "Sers-toi de TOUT ce qu'Aiwa a : la recherche de design (consigne Design ci-dessus), la vidéo (Blender, boucles de qualité maximale), les images, les musiques libres. "
        "Le minimum qui ne se négocie pas : son nom en titre (`<h1>`), un `<title>`, la balise viewport, un fond qui bouge, des liens (les fichiers publics de la session en cours, et un moyen de la joindre que SEULE elle a donné, jamais inventé), aucune ressource en `http://`. "
        "Comme pour tout site : références mesurées avec `--format=site`, rien de recopié, captures regardées en 390 px et 1440 px (Playwright), défauts corrigés, `aiwa-design/references.md` écrit. "
        "Cette page est AUSSI l'ACCUEIL de l'application de la personne, qu'elle change à la voix (« boutons en cercle »). Les boutons d'Aiwa ne s'écrivent pas : pose des EMPLACEMENTS, l'application y met les vrais boutons du widget. "
        "`<span data-aiwa-button=\"widget\"></span>` : UN bouton Widget qui ouvre le widget (ses quatre onglets) ; ou `<div data-aiwa-slot=\"build\"></div>` (groupes `domain` `files` `build` `voir` ou `all`), ou un seul `data-aiwa-button=\"docs\"` (les noms du widget en minuscules : docs, media, agent, nearby, qr…) ; `data-aiwa-only` / `data-aiwa-skip` / `data-aiwa-look=\"icons\"` choisissent. "
        "Chaque bouton est un `<button class=\"aiwa-btn\" data-id data-tone data-on data-found>` (un `<svg>`, un `<span>`) que tu habilles en CSS (`--aiwa-bg --aiwa-fg --aiwa-edge --aiwa-radius`) : c'est l'UX de son application. Ne dessine JAMAIS un faux bouton d'Aiwa. Sur le profil d'un AUTRE, ces boutons sont ceux du VISITEUR ; ce que tu écris dans la page (nom, liens, fichiers, contacts) reste à toi. "
        "En HAUT de la page, ses SCÉNARIOS, que le visiteur lance chez lui : `Profile/scenarios.json` (une liste de `{id, name, word (4 lettres au moins), kind: \"double\", prompt, speak, words: [{word, does}]}`, comme les scénarios d'Aiwa) et `<div data-aiwa-scenarios></div>` : l'application dessine les cartes et le bouton Lancer ; chez un visiteur elle demande d'abord confirmation. "
        "La page affichée est celle de la branche par défaut (HEAD) : pousse ou fusionne là ; l'application suit le dernier commit de `Profile/` et recharge toute seule en quelques secondes. "
        "Garde la page belle sans script (vidéo, CSS) : sur le profil d'un autre, ses scripts ne tournent pas. "
        f"Pars du niveau de la page de référence ({TEMPLATE_URL}) et dépasse-la. Aiwa lit le fichier tel quel dans le dépôt (public)."
    )
    if repo_name:
        text += f" Son dépôt du profil est {repo_name} : c'est là, et seulement là, que va ce dossier ; tu travailles dans ce dépôt quand elle parle de son profil."
    return text


def check(html, media=None):
    """What a profile must have, and nothing more (no limit of weight, of script or of network: a profile does what a site does): its problems as plain sentences; an empty list when it is good."""
    problems = []
    low = html.lower()
    if "<title>" not in low:
        problems.append("Il manque le <title>.")
    if 'name="viewport"' not in low and "name='viewport'" not in low:
        problems.append("Il manque la balise viewport : la page ne se lira pas bien sur un téléphone.")
    if re.search(r"""(?:src|href|url)\s*[=(]\s*["']?http://""", low):
        problems.append("Une ressource est en http:// : le navigateur du téléphone la bloque, mets https ou un chemin relatif.")
    if "<h1" not in low:
        problems.append("Il manque le nom en titre (<h1>).")
    if not re.search(r"""href\s*=\s*["'](?:https://|mailto:|tel:)""", low):
        problems.append("Aucun lien : au moins les fichiers publics et un moyen de te joindre.")
    if "<video" not in low and "background" not in low and "<canvas" not in low:
        problems.append("Pas de fond : une vidéo, un canvas, un GIF ou une image derrière le texte.")
    return problems
