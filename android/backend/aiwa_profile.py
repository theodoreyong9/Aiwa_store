"""The profile: a page of the person (a photo, a text, a background, the links of her files and the ways to reach her), written as ONE html file in a `Profile/` folder of the repository she chose, next to the
background picture. The super agent changes it by voice (`do.profile`: the background, the text, the name, a contact); the phone's settings choose the repository and publish. Nothing here talks to the network
except `publish`, which uses the same `gh` as the rest of the brain (no token of Aiwa's own)."""
import html
import json
import re
import time
from pathlib import Path

PROFILE_FILE = Path.home() / ".aiwa_profile.json"
BG_DIR = Path(__file__).resolve().parent / "profile_bg"
FOLDER = "Profile"

# the backgrounds shipped with Aiwa (crops of Poly Haven panoramas, CC0): key -> (file, spoken name)
BACKGROUNDS = {"ville": ("ville.jpg", "la ville la nuit"), "neon": ("neon.jpg", "les néons"), "ciel": ("ciel.jpg", "le ciel étoilé"), "atelier": ("atelier.jpg", "l'atelier")}
BG_WORDS = {"ville": "ville", "ville la nuit": "ville", "nuit": "ville", "neon": "neon", "néon": "neon", "neons": "neon", "néons": "neon", "ciel": "ciel", "ciel étoilé": "ciel", "ciel etoile": "ciel", "étoiles": "ciel", "etoiles": "ciel",
            "desert": "ciel", "désert": "ciel", "atelier": "atelier", "usine": "atelier"}
MEDIA_RE = re.compile(r"https://[^\s\"'<>]{4,300}\.(?:jpe?g|png|webp|gif|mp4|webm)(?:\?[^\s\"'<>]{0,100})?", re.I)
VIDEO_RE = re.compile(r"\.(?:mp4|webm)(?:\?|$)", re.I)
REPO_RE = re.compile(r"[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}")

CONTACT_KINDS = {"mail": "Mail", "sms": "SMS", "appel": "Appel", "whatsapp": "WhatsApp", "telegram": "Telegram", "instagram": "Instagram", "tiktok": "TikTok", "linkedin": "LinkedIn", "twitter": "X"}
FILE_KINDS = {"docs": "Documents", "media": "Vidéos", "app": "App", "page": "Page", "contract": "Contrat"}


def clean(value, limit):
    return " ".join(str(value or "").split())[:limit]


def background_key(word):
    """What the person said or wrote for a background -> a key of BACKGROUNDS, an https address of a picture or a video, or None."""
    text = clean(word, 300)
    if MEDIA_RE.fullmatch(text):
        return text
    return BG_WORDS.get(text.lower().strip(" .!"))


def contact_link(kind, value):
    """The link a contact becomes (https, mailto or tel only), or None when the value does not look like what the kind needs."""
    value = clean(value, 120)
    if kind == "mail" and re.fullmatch(r"[^@\s<>,;]+@[^@\s<>,;]+\.[^@\s<>,;]+", value):
        return "mailto:" + value
    if kind in ("sms", "appel", "whatsapp"):
        number = re.sub(r"[ .\-()]", "", value)
        if re.fullmatch(r"\+?\d{3,15}", number):
            return {"sms": "sms:", "appel": "tel:"}.get(kind, "https://wa.me/") + (number.lstrip("+") if kind == "whatsapp" else number)
    handle = value.lstrip("@")
    if kind == "telegram" and re.fullmatch(r"[A-Za-z0-9_]{3,32}", handle):
        return "https://t.me/" + handle
    if kind == "instagram" and re.fullmatch(r"[A-Za-z0-9_.]{2,32}", handle):
        return "https://www.instagram.com/" + handle
    if kind == "tiktok" and re.fullmatch(r"[A-Za-z0-9_.]{2,32}", handle):
        return "https://www.tiktok.com/@" + handle
    if kind == "twitter" and re.fullmatch(r"[A-Za-z0-9_]{1,15}", handle):
        return "https://x.com/" + handle
    if kind == "linkedin":
        if re.fullmatch(r"https://[a-z.]*linkedin\.com/[A-Za-z0-9_.\-/%]{2,120}", value):
            return value
        if re.fullmatch(r"[A-Za-z0-9_.\-%]{2,100}", handle):
            return "https://www.linkedin.com/in/" + handle
    return None


def sanitize(patch):
    """The fields of a profile that a patch may carry, each cleaned; anything else is dropped."""
    out = {}
    if not isinstance(patch, dict):
        return out
    if "nom" in patch:
        out["nom"] = clean(patch["nom"], 60)
    if "texte" in patch:
        out["texte"] = " ".join(str(patch["texte"] or "").split())[:600]
    if "repo" in patch and REPO_RE.fullmatch(clean(patch["repo"], 200)):
        out["repo"] = clean(patch["repo"], 200)
    if "fond" in patch:
        key = background_key(patch["fond"])
        if key:
            out["fond"] = key
    if "photo" in patch:
        photo = clean(patch["photo"], 300)
        if not photo or re.fullmatch(r"https://[^\s\"'<>]{4,290}", photo):
            out["photo"] = photo
    if isinstance(patch.get("contacts"), dict):
        out["contacts"] = {k: clean(v, 120) for k, v in patch["contacts"].items() if k in CONTACT_KINDS and contact_link(k, v)}
    if isinstance(patch.get("liens"), list):
        out["liens"] = [{"kind": e["kind"], "url": e["url"]} for e in patch["liens"] if isinstance(e, dict) and e.get("kind") in FILE_KINDS and re.fullmatch(r"https://[^\s\"'<>]{4,300}", str(e.get("url") or ""))][:12]
    return out


def load():
    try:
        data = json.loads(PROFILE_FILE.read_text(encoding="utf-8"))
        return data if isinstance(data, dict) else {}
    except (OSError, ValueError):
        return {}


def save(profile):
    PROFILE_FILE.write_text(json.dumps(profile, ensure_ascii=False, indent=1), encoding="utf-8")


def update(patch):
    """Applies a patch to the stored profile (contacts are merged: a contact given again replaces the old one, an empty value removes it)."""
    profile = load()
    clean_patch = sanitize(patch)
    if isinstance(patch, dict) and isinstance(patch.get("contacts"), dict):
        merged = dict(profile.get("contacts") or {})
        for kind, value in patch["contacts"].items():
            if kind in CONTACT_KINDS and not clean(value, 120):
                merged.pop(kind, None)
        merged.update(clean_patch.get("contacts") or {})
        clean_patch["contacts"] = merged
    profile.update(clean_patch)
    save(profile)
    return profile


def background_file(profile):
    """(local file name, address) of the background: a shipped one is copied next to the page; an address is used as it is."""
    key = profile.get("fond") or "ville"
    if key in BACKGROUNDS:
        return BACKGROUNDS[key][0], None
    return None, key if MEDIA_RE.fullmatch(key) else None


def render(profile):
    """The page, ONE self-contained html file (no script, no external font): the background (picture or video, dark), the photo, the name and the text, the files, the contacts."""
    name = html.escape(profile.get("nom") or "Mon profil")
    text = html.escape(profile.get("texte") or "")
    photo = profile.get("photo") or ""
    local, remote = background_file(profile)
    bg_url = ("bg/" + local) if local else remote
    video = bool(remote and VIDEO_RE.search(remote))
    files = "".join(f'<a class="cell" href="{html.escape(e["url"], quote=True)}"><b>{html.escape(FILE_KINDS[e["kind"]])}</b><span>{html.escape(re.sub(r"^https://", "", e["url"])[:42])}</span></a>' for e in profile.get("liens") or [])
    contacts = "".join(f'<a class="chip" href="{html.escape(link, quote=True)}">{html.escape(CONTACT_KINDS[kind])}</a>'
                       for kind, value in (profile.get("contacts") or {}).items() if (link := contact_link(kind, value)))
    bg = (f'<video class="bg" src="{html.escape(bg_url, quote=True)}" autoplay muted loop playsinline></video>' if video else f'<div class="bg" style="background-image:url(\'{html.escape(bg_url or "", quote=True)}\')"></div>') if bg_url else '<div class="bg"></div>'
    photo_html = f'<img class="photo" src="{html.escape(photo, quote=True)}" alt="">' if photo else ""
    return f"""<!doctype html>
<html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>{name}</title>
<style>
:root{{color-scheme:dark}}*{{box-sizing:border-box}}body{{margin:0;min-height:100vh;font:16px/1.45 system-ui,sans-serif;color:#f4f4ff;background:#10132e}}
.bg{{position:fixed;inset:0;width:100%;height:100%;object-fit:cover;background-size:cover;background-position:center;z-index:0}}
.veil{{position:fixed;inset:0;background:linear-gradient(135deg,rgba(29,47,102,.62),rgba(42,20,80,.78));z-index:1}}
main{{position:relative;z-index:2;max-width:560px;margin:0 auto;padding:36px 18px 48px;display:flex;flex-direction:column;gap:22px}}
.photo{{width:112px;height:112px;border-radius:50%;object-fit:cover;border:2px solid rgba(255,255,255,.55);align-self:center}}
h1{{margin:0;text-align:center;font-size:28px}}p{{margin:0;text-align:center;color:#d8dbff;white-space:pre-wrap}}
.files{{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:8px}}
.cell{{display:flex;flex-direction:column;gap:2px;padding:14px 18px;color:#fff;text-decoration:none;background:linear-gradient(180deg,rgba(255,255,255,.26),rgba(255,255,255,.07));border:1px solid rgba(255,255,255,.35);clip-path:polygon(14px 0,100% 0,calc(100% - 14px) 100%,0 100%)}}
.cell b{{font-size:15px}}.cell span{{font-size:11px;color:#cfd3ff;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}}
.contacts{{display:flex;flex-wrap:wrap;gap:8px;justify-content:center}}
.chip{{padding:9px 18px;color:#fff;text-decoration:none;background:rgba(255,255,255,.16);border:1px solid rgba(255,255,255,.4);clip-path:polygon(10px 0,100% 0,calc(100% - 10px) 100%,0 100%)}}
h2{{margin:0;font-size:12px;letter-spacing:.12em;text-transform:uppercase;color:#c3c6f0;text-align:center}}
</style></head><body>{bg}<div class="veil"></div>
<main>{photo_html}<h1>{name}</h1>{f"<p>{text}</p>" if text else ""}
{f'<section><h2>Mes fichiers</h2><div class="files">{files}</div></section>' if files else ""}
{f'<section><h2>Me joindre</h2><div class="contacts">{contacts}</div></section>' if contacts else ""}
</main></body></html>
"""


def page_url(repo):
    owner, name = repo.split("/", 1)
    base = f"https://{owner}.github.io/" + ("" if name.lower() == f"{owner}.github.io".lower() else name + "/")
    return base + FOLDER + "/"


def publish(github, profile=None):
    """Writes Profile/index.html (and the shipped background next to it) in the repository chosen for the profile. Returns {"ok", "url", "error"}."""
    profile = profile or load()
    repo = profile.get("repo")
    if not repo or not REPO_RE.fullmatch(repo):
        return {"ok": False, "error": "Aucun dépôt choisi pour le profil (Réglages)."}
    local, _ = background_file(profile)
    try:
        if local:
            raw = (BG_DIR / local).read_bytes()
            github.put_file(repo, f"{FOLDER}/bg/{local}", raw, "Aiwa : fond du profil")
        github.put_file(repo, f"{FOLDER}/index.html", render(profile).encode("utf-8"), "Aiwa : profil")
    except (OSError, github.GithubError) as err:
        return {"ok": False, "error": str(err)[:300]}
    profile["published"] = int(time.time())
    save(profile)
    return {"ok": True, "url": page_url(repo)}
