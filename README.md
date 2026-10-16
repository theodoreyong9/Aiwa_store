# Aiwa Store

<p align="center">
  <a href="https://github.com/theodoreyong9/Aiwa_store/actions/workflows/ci.yml"><img src="https://github.com/theodoreyong9/Aiwa_store/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://github.com/theodoreyong9/Aiwa_store/actions/workflows/android.yml"><img src="https://github.com/theodoreyong9/Aiwa_store/actions/workflows/android.yml/badge.svg" alt="Android build"></a>
  <a href="https://github.com/theodoreyong9/Aiwa_store/actions/workflows/pages.yml"><img src="https://github.com/theodoreyong9/Aiwa_store/actions/workflows/pages.yml/badge.svg" alt="Pages"></a>
  <a href="https://github.com/theodoreyong9/Aiwa_store/actions/workflows/registry.yml"><img src="https://github.com/theodoreyong9/Aiwa_store/actions/workflows/registry.yml/badge.svg" alt="Registry"></a>
  <a href="https://github.com/theodoreyong9/Aiwa_store/blob/main/LICENSE"><img src="https://img.shields.io/github/license/theodoreyong9/Aiwa_store" alt="License: MIT"></a>
  <a href="https://github.com/theodoreyong9/Aiwa_store/releases/tag/android-latest"><img src="https://img.shields.io/github/downloads/theodoreyong9/Aiwa_store/android-latest/total?label=APK&logo=android" alt="APK downloads"></a>
  <img src="https://img.shields.io/badge/statut-b%C3%AAta-orange" alt="Statut : bêta">
</p>

# Ton univers. Ta volonté.

**Aiwa : un système d'élevage d'agents personnels avec GitHub et Claude Code, sur Android.** Tu parles (même écran verrouillé) ; des sessions Claude Code
créent, *dans ton propre dépôt*, des documents, des vidéos, des jeux, des sites, des applications Android et des smart contracts. Tu gardes tout :
rien ne passe par un serveur à nous.

▶ **Le film** (55 à 65 s, fait avec les outils de ce dépôt) : [`docs/videos/aiwa-v18-16x9.mp4`](docs/videos/aiwa-v18-16x9.mp4)
· l'onglet *Papers* de l'application le lit.

> **Où ça en est, sans détour.** Le dépôt est testé en CI (protocole, registre, appli web dans un vrai Chromium, cerveau Python, tests Kotlin du
> pont), et l'APK **compile** en CI. **Rien de ce qui suit n'a été fait tourner de bout en bout sur un vrai téléphone**, ni sur le vrai réseau Solana,
> ni sur de vraies lunettes connectées. Chaque section dit ce qui est *livré*, *écrit mais non essayé sur téléphone*, ou *vision*.
> Détail : [Ce qui est vérifié, ce qui ne l'est pas](#12-ce-qui-est-vérifié-ce-qui-ne-lest-pas).

---

## Sommaire

1. [Ce qu'il te faut](#1-ce-quil-te-faut)
2. [Installer (Android)](#2-installer-android)
3. [Le widget](#3-le-widget) — formes *light* et *glasses*
4. [Ce que Claude écrit, et où](#4-ce-que-claude-écrit-et-où) — la structure de ton dépôt
5. [La voix](#5-la-voix) — micro, « instruction », les 11 mots, écoute permanente
6. [Le super agent](#6-le-super-agent)
7. [Chaînes et boucles d'agents](#7-chaînes-et-boucles-dagents) — producteur, critique, soutien critique
8. [Vidéos, cast TV, campagnes](#8-vidéos-cast-tv-campagnes)
9. [Le cerveau (Termux)](#9-le-cerveau-termux) — installation, mises à jour, sécurité
10. [Le Store, le registre, le wallet, le token](#10-le-store-le-registre-le-wallet-le-token)
11. [Développer sur ce dépôt](#11-développer-sur-ce-dépôt)
12. [Ce qui est vérifié, ce qui ne l'est pas](#12-ce-qui-est-vérifié-ce-qui-ne-lest-pas)
13. [Vision (pas démontrée)](#13-vision-pas-démontrée)
14. [Licences et crédits](#14-licences-et-crédits)

---

## 1. Ce qu'il te faut

| | |
|---|---|
| Un téléphone **Android** | pour l'appli, le widget et le micro |
| **Termux** (depuis **F-Droid** ou ses releases GitHub) | le « cerveau » d'Aiwa y tourne. La version du Play Store est une copie ancienne, non officielle : elle n'a pas l'appel `RUN_COMMAND` que le widget utilise |
| Un compte **Claude** (Claude Code dans le cloud) | c'est lui qui écrit : les sessions vivent chez Claude, pas sur ton téléphone |
| Un compte **GitHub** et au moins un dépôt | tout ce qui est produit y est rangé. L'application GitHub *Claude* doit avoir accès à tes dépôts (`github.com/settings/installations` → **Claude** → *All repositories*, une fois) |
| *(facultatif)* une **clé Anthropic** | pour parler au super agent en langage naturel (§6). Elle reste dans Termux, chez toi |

**Aiwa ne te demande jamais de jeton GitHub.** Ce qu'il lit sur GitHub, il le lit sans connexion (dépôts publics) ; ce qui est écrit est écrit par les
sessions Claude, avec leur propre accès.

## 2. Installer (Android)

1. **L'APK.** Télécharge `Aiwa_store.apk` sur la [release `android-latest`](https://github.com/theodoreyong9/Aiwa_store/releases/tag/android-latest)
   (reconstruite à chaque build réussi, depuis `clean` comme depuis `main`) et installe-le (Android demande une fois d'autoriser l'installation).
   Une nouvelle version s'installe par-dessus et garde tes données.
2. **Le cerveau.** Dans Termux, une seule ligne, aussi pour les mises à jour :
   ```sh
   curl -fsSL https://raw.githubusercontent.com/theodoreyong9/Aiwa_store/main/android/backend/bootstrap.sh | bash
   ```
   Elle installe le cerveau dans `~/aiwa_store`. Si Termux ou le cerveau manque, le widget le dit (« Termux manquant », « Installation manquante ») et un
   appui copie la ligne.
3. **Le widget.** L'appli propose de l'ajouter à l'écran d'accueil (un appui sur *Ajouter*) ; sinon appui long → *Widgets* → *Aiwa Store*. Android
   ouvre une courte configuration : autoriser Aiwa à lancer des commandes Termux, et les notifications.
4. **Connecter Claude** (bouton du widget) : une page s'ouvre, tu te connectes avec ton compte, le code revient dans la même fenêtre.
5. **Choisis un dépôt** (pastille *dépôt* du widget). Tout est prêt : dicte.

La première fois, Claude Code est installé dans une couche Linux (proot) de Termux : le script la marque lui-même comme expérimentale.

## 3. Le widget

Deux formes, au choix dans *Réglages → Forme du widget*. Passer de l'une à l'autre redessine le widget (une seule mise à jour à la fois ; si c'est lent,
un message dit combien de secondes a pris Aiwa lui-même).

### Forme **Light** (par défaut)

De haut en bas :

1. **Session ▾ · ⎇ dépôt ▾** : la session de travail et le dépôt. *Une session appartient à un dépôt* ; changer de dépôt ne change pas de « mode ».
2. **Docs · Vidéo · Page · App · Aiwa** : les cinq productions du dépôt. Un bouton est **gris** tant que le dépôt n'en contient pas, **coloré** quand il y
   en a une ; un appui l'ouvre (Docs et Vidéo s'ouvrent toujours : c'est la fenêtre pour en demander une). Voir §4 pour ce qui est détecté.
3. **Dicter** (le micro), **Claude** (ouvre la conversation ; rouge quand Claude attend ou se tait trop longtemps), **?** (quand Claude te pose une
   question), **↻** (mise à jour d'Aiwa prête : n'apparaît que quand un APK plus récent est téléchargé).
4. En bas : **Aiwa** (l'application), **super agent** (§6), **Réglages**.

### Forme **Glasses**

Deux verres. À gauche : la colonne Docs/Vidéo/Page/App/Aiwa, une rangée workflow · boucles · campagne, les réglages, *Dicter* et le super agent ; à droite :
Store, Wallet, une silhouette (chercher un identifiant ou un compte), *own*, *fave*, *inviter*. C'est une **forme de widget pour des lunettes
connectées** ; **aucune lunette réelle n'a été essayée**.

### Réglages

*Session, dépôt, modèle, push (direct sur main ou branche), clé Anthropic, super agent (réglages, **détruire et recommencer**), « Mes mots » (§5),
état d'Aiwa, version (appli, cerveau, **date de la dernière mise à jour du cerveau**), commentaire pour le critique, « c'est prêt » à voix haute,
« je suis disponible ? », écoute permanente, forme du widget.* Les entrées qui répètent le widget Light (session, dépôt, boucles…) n'apparaissent que
sur la forme Glasses.

## 4. Ce que Claude écrit, et où

Aiwa impose une structure à **tous** les dépôts, pour retrouver ce qui a été produit sans rien configurer :

| Production | Où | Comment Aiwa la détecte (sans jeton) |
|---|---|---|
| **Documents** | `docs/` (`.md`, `.pdf`, `.docx`, `.xlsx`, `.pptx`, `.txt`, `.csv`…) | fichiers de `docs/` |
| **Vidéos** | `docs/videos/` (étapes de fabrication dans `docs/videos/<nom>/`) | fichiers vidéo de `docs/videos/` (et de la racine de `docs/`) |
| **Campagnes** | `docs/campagnes/<campagne>/` (`campaign.json`, `proposals.json`…) | dossier de campagne |
| **Page** (site) | GitHub Pages du dépôt | `https://<compte>.github.io/<dépôt>/` répond 200 |
| **App** (Android) | release du dépôt avec un `.apk` | une release contient un `.apk` |
| **Aiwa** (apps) | `aiwa-apps/` | `*.aiwa.html` (contrats) ; `*.app.html` pour les apps de bibliothèque |
| Signaux de repli | `aiwa-out/SIGNAL` sur la branche de la session | lu si le relais public est bloqué |
| Référence du critique | `aiwa-papers/` | lu à chaque tour d'une boucle |

Le bouton **Vidéo** surveille `docs/videos/` toutes les ~45 s (une requête conditionnelle : GitHub répond 304, ce qui ne compte pas dans sa limite). Les
autres boutons sont revérifiés environ toutes les 10 minutes ; un appui force une vérification.

**Modes = instructions écrites.** Docs, Vidéo, Page, App, Aiwa, campagne : choisir l'un d'eux ajoute des *consignes écrites* au prochain message
envoyé à la session (ce que Claude doit produire, où, avec quels contrôles). Les modes **ne se disent pas** : ils se règlent à la main (§5).

### Deux sortes d'apps

- **App de bibliothèque** (`aiwa-apps/nom.app.html`) : un seul fichier HTML, dans **ton** dépôt public, modifiable quand tu veux. Pas de registre, pas de
  wallet, pas de score. C'est le **brouillon / le test**. Le Store la lit en suivant ton compte GitHub (onglet *Library*).
- **Contrat Aiwa** (`nom.aiwa.html`) : la même sorte de fichier, mais dont la logique est un **contrat** que n'importe qui peut rejouer à partir des
  événements signés. Publié dans le registre, **immuable** (signé, identifié par son hash), classé, avec brûlage de SOL. Voir §10 et
  [`docs/CONTRACTS.md`](docs/CONTRACTS.md).

En mode *Aiwa*, Claude livre **toujours d'abord un brouillon** dans ta bibliothèque, **avec son test écrit en premier** (`nom.aiwa.test.mjs`, lancé avec
`node`) ; il ne publie rien dans le registre. Dans *Mes apps*, le bouton **⇪** d'une app de bibliothèque ouvre la feuille de publication avec ton
wallet.

## 5. La voix

Le micro du widget est le **mode agent**. Un parseur (sur le téléphone, sans réseau) lit ce qui est dit :

- **« instruction … »** : règle **uniquement les pastilles session et dépôt** (« instruction session X dépôt Y »). Le téléphone **relit** ce qu'il a
  compris, attend « **c'est bon vas-y** » (ou une correction), applique, puis envoie à la session l'information de pastille.
- **Tout le reste** : une *commande* envoyée telle quelle à la session en cours.

Le modèle, l'effort, le push, la qualité et les modes ne se disent plus : tout cela est manuel (écrit).

### L'écoute permanente

Un petit modèle local (**Vosk**, ~41 Mo, rien n'est envoyé) écoute les mots de réveil, **même écran verrouillé** (service au premier plan : le point vert du
micro reste affiché, la batterie baisse plus vite). Elle s'allume avec le bouton du super agent (qui demande la permission du micro).

### Les onze mots personnalisables (*Réglages → Mes mots*)

Chacun se **dit** (le téléphone retient comment le modèle l'entend) et/ou s'**écrit**. Chaque changement est transmis au petit modèle, au parseur et au
super agent.

| # | Mot (par défaut) | Effet |
|---|---|---|
| 1 | Réveil (« mon agent ») | réveille l'écoute : tu dictes |
| 2 | Super agent (« mon super agent ») | ouvre le super agent |
| 3 | Instruction | ce qui suit règle session et dépôt |
| 4 | Pastille session | « session … » |
| 5 | Pastille dépôt (« dépôt », « repo ») | « dépôt … » |
| 6 | Validation (« c'est bon vas-y ») | valide ce que le téléphone vient de relire |
| 7 | Stop (« stop Claude », « arrête Claude ») | arrête la production — **toujours confirmé** — et fait oublier à la dernière session son dernier commentaire |
| 8 | Éteins | éteint l'écoute permanente et le super agent |
| 9 | Renvoi (« renvoie les instructions ») | la session reçoit de nouveau les instructions générales et celles des pastilles |
| 10 | **T'es où là** | le téléphone lit à voix haute les pastilles du widget (dépôt, session, modèle, push) |
| 11 | **Fais ton rapport** | (super agent ouvert) son rapport d'activité et **une idée** |

Annonces : « c'est prêt » à chaque production captée (réglable) ; quand le super agent est réglé mais l'écoute coupée, il demande « je suis
disponible ? » par une notification (un appui, le micro ne peut pas s'ouvrir en arrière-plan).

Limites : la reconnaissance du texte libre est celle d'Android ; l'écoute de « t'es où là » par le petit modèle français n'a pas été essayée.

## 6. Le super agent

Une **deuxième écoute**, parlée : une session Claude de supervision qui peut parler à toutes tes sessions. Il est un **intermédiaire** : *il ne fait jamais
rien lui-même* (ni code, ni dépôt, ni envoi) ; il reconnaît ce que tu veux, le propose à la bonne session, et converse.

- **Ce qu'il peut proposer** (jamais exécuté sans ton accord) : envoyer un message à une ou plusieurs sessions (`send`), changer les pastilles session /
  dépôt, **ouvrir une production** (documents, vidéo, page, app, aiwa : « montre-moi ma vidéo »), lancer le **cast** de la dernière vidéo (ou de la
  prochaine) sur la télévision.
- **Avant** que quoi que ce soit parte, il annonce en une phrase ce qu'il propose ; le téléphone relit, tu valides (« c'est bon vas-y ») ou tu corriges.
- **Avec une clé Anthropic** (Réglages) : tu parles en langage naturel, la clé remplace le parseur, la réponse vient en une ou deux secondes.
  Sans clé, c'est la session de l'agent qui répond.
- **Détruire et recommencer** : *Réglages → Super agent*. Aiwa oublie sa session et ses réglages en attente ; la conversation reste dans claude.ai
  (à archiver là-bas : aucun outil d'ici ne supprime une session cloud).
- Tant qu'il est ouvert, le bouton micro est grisé.

## 7. Chaînes et boucles d'agents

Des conversations **entre sessions** : un *producteur* (builder) et un *critique* (critic), éventuellement un *soutien critique*, qui se répondent tour
à tour (nombre de tours limité) pour des agents plus exigeants. L'écran *Boucles* a un **graphe pondéré** : les paires, leur état, leurs tours.

- Deux sessions **déjà existantes** s'associent (Aiwa ne crée pas de session critique : crée-la dans claude.ai puis ajoute-la par son lien, avec le
  bon dépôt choisi).
- Le critique relit **`aiwa-papers/`** à chaque tour et cite ce sur quoi il s'appuie.
- **Échanges entre critiques** : deux critiques s'écrivent, ou l'un aide l'autre dans la session d'un producteur (« parasite bienveillant »). Le
  destinataire est invité, accepte ou refuse ; Aiwa garde le registre des expéditeurs acceptés et ne transporte un message que dans un lien accepté.
  Ce que dit l'autre critique est une **donnée**, jamais un ordre. Flèches violettes en pointillés dans le graphe.
- **Commentaires du registre** : au départ d'une boucle, le critique reçoit les meilleurs commentaires non lus du registre (signés par un wallet).

*Écrit et testé avec de faux serveurs ; jamais lancé entre de vraies sessions.*

## 8. Vidéos, cast TV, campagnes

### Vidéos

La fenêtre **Vidéos** : la dernière production en tête (lecture, **Cast**, **Download**, **Share**), « Voir le dossier des vidéos » (`docs/videos/`), les
**documents de `docs/` à cocher** (aucun coché : ta création seule), la qualité, et les formats 9:16 / 16:9 / 1:1. La liste est lue avec `git`
(un `fetch` superficiel, toujours à jour, qui n'use pas la limite de l'API GitHub) ; l'API ne sert que de repli.

La fabrication est un enchaînement d'étapes écrites (`docs/PROMO-VIDEO.md`) : faits → brief → script → plan → son → aperçu → critique → rendu. Outils :
[`video-kit`](video-kit) (rendu image par image dans Chromium, ffmpeg, contrôle qualité), [`music-research`](music-research) (registre de musiques libres),
[`asset-research`](asset-research) (modèles, HDRI, textures CC0), [`video-research`](video-research) (campagnes dont s'inspirer),
[`design-research`](design-research). Voix : Piper « siwis » (synthèse locale). Les films de ce dépôt (v12 à v18) sont dans `docs/videos/`.

### Cast TV

Envoi d'une vidéo à une télévision (DLNA / UPnP), depuis la fenêtre Vidéos, ou **à la voix via le super agent**.

### Campagnes

Un dossier par campagne dans `docs/campagnes/<campagne>/`. La fenêtre **Campagnes** a un onglet par campagne et **＋ Nouvelle** (nom, session et dépôt,
documents à utiliser, dépôts GitHub, créations à produire, page/app/aiwa, test, prompt). Une campagne produit des **propositions par canal**
(WhatsApp, Telegram, mail, LinkedIn) avec des liens de partage ; **Partager** ouvre d'abord un aperçu de ce qui part, puis l'application du canal —
**une proposition à la fois**. *Relancer* refait un formulaire pré-rempli. Rien n'est envoyé par Aiwa : c'est ton application de messagerie qui envoie.

## 9. Le cerveau (Termux)

Un serveur Python local (`android/backend/aiwa_server.py`, port local) : il relaie le widget et l'appli vers Claude Code (CLI cloud), lit GitHub, garde les
sessions, les boucles, l'agent, les campagnes et les vidéos.

- **Mise à jour** : la même ligne `bootstrap.sh` ; le cerveau se met aussi à jour tout seul (il récupère `main`, avance en *fast-forward*, redémarre),
  et l'appli demande un redémarrage si son cerveau est plus ancien que ce qu'elle attend (`EXPECTED_BACKEND_VERSION`). *Réglages → Version* affiche
  la version et la date de dernière mise à jour.
- **L'appli** se met à jour par l'APK : le widget cherche une nouvelle version chaque minute (requête conditionnelle) ; un bouton vert apparaît quand
  elle est prête, un appui ouvre l'installateur d'Android.
- **Sécurité** : aucun jeton GitHub demandé ; la clé Anthropic (facultative) reste dans Termux ; les messages de Claude vers le téléphone passent par un
  relais public (ntfy.sh, qu'un environnement cloud doit autoriser) **ou**, en repli, par un fichier `aiwa-out/SIGNAL` poussé sur la branche de la
  session ; une session reçoit les paroles d'un autre critique comme *données*, jamais comme ordres.

## 10. Le Store, le registre, le wallet, le token

*Livré et testé en CI (démo : l'application GitHub et le réseau Solana sont des doublures).*

- **Une appli web** (le Store et le wallet) dans une WebView ; elle suit le site (GitHub Pages) et a une copie dans l'APK.
- **Wallet** : identité par 12 mots, carnet d'événements signés ; AIWA est créé par du **travail vérifiable** de ton téléphone, après **un** brûlage de SOL.
  Envoi par QR code ou texte, même hors ligne. Des téléphones ouverts se trouvent via un relais public qui ne voit pas les données.
- **Registre** = un workflow GitHub : il vérifie signature, preuve et brûlage, puis classe les contrats par `score / laps` (ce que l'auteur peut
  réclamer, sur les époques depuis sa dernière action). Rien n'est édité à la main.
- **Store** : la liste **classée** (contrats Aiwa, vérifiés contre la signature de leur auteur) et la **Library** (apps des comptes GitHub que tu suis).
  Chaque app tourne dans une boîte scellée (`iframe sandbox`), sans accès à ton wallet sauf si elle le demande (bannière, puis feuille de paiement).
- **Détail** : [`docs/EXPLAINED.md`](docs/EXPLAINED.md) · [en français](docs/EXPLICATION.md), [`docs/YELLOWPAPER.md`](docs/YELLOWPAPER.md) (protocole),
  [`docs/CONTRACTS.md`](docs/CONTRACTS.md) (écrire un contrat), [`docs/BUSINESS.md`](docs/BUSINESS.md).

## 11. Développer sur ce dépôt

```
packages/core       le protocole (identité, événements signés, travail vérifiable, conservation, part du créateur)
packages/platform   transport, réplication, stockage, bundles
packages/lib        l'API du wallet et le SDK des contrats
registry/           ce qui décide des apps listées et de leur ordre
apps/web/           le Store et le wallet (une appli web)
android/app         l'APK : WebView + widget + voix + fenêtres (Kotlin, Jetpack Compose, Glance)
android/bridge      le pont Kotlin testé (parseur vocal, mots, pilules, API du cerveau)
android/backend     le cerveau Python (+ tests), les scripts Termux
docs/               documents ; docs/videos/ les films ; docs/campagnes/ les campagnes
video-kit/ music-research/ asset-research/ video-research/ design-research/   la chaîne vidéo
aiwa-papers/        ce que lit le critique à chaque tour
deployment.json     paramètres partagés par le wallet et le registre
access.json         liste d'accès (désactivée : `enforce: false`)
```

Commandes :

```sh
npm install
npm test                                       # protocole, registre, appli web (Chromium : npx -w aiwa-store-web playwright install chromium)
npm run build -w aiwa-store-web                # apps/web/dist
python3 -m unittest discover -s android/backend/tests    # le cerveau
gradle -p android :bridge:testDebugUnitTest    # le pont Kotlin
gradle -p android :app:assembleDebug           # l'APK (Android SDK requis ; la CI le fait)
node scripts/devnet-check.mjs --fake           # tout le chemin, contre un Solana de substitution
```

| Workflow | Quand | Fait quoi |
|---|---|---|
| **CI** | chaque push | toute la suite de tests, les diagrammes, le contrôle à blanc du réseau réel |
| **Android** | un push touchant l'appli (`main` et `clean`) | tests du cerveau et du pont, build de l'APK, publication de `android-latest` |
| **Registry** | une PR ajoutant `submissions/*.json` | valide, écrit `store/`, ferme la PR avec le verdict |
| **Pages** | push sur `main` ou après le registre | publie le site |
| **Devnet check** | à la main | un brûlage sur le vrai devnet (rouge tant que `DEVNET_PHRASE` n'existe pas) |

Règles de contribution : une ligne dans `CHANGELOG.md` (« Unreleased ») par changement ; si le cerveau change de façon visible pour l'appli, augmenter
`BACKEND_VERSION` (`aiwa_server.py`) **et** `EXPECTED_BACKEND_VERSION` (`AiwaState.kt`) ; ne fusionner dans `main` que CI et Android au vert.

Ce que seul le propriétaire du dépôt peut faire : activer GitHub Pages (*Source : GitHub Actions*), créer l'OAuth App *Aiwa Store* (Device Flow) pour
la connexion GitHub de la feuille de publication, déposer `DEVNET_PHRASE`, et faire le premier essai réel sur un téléphone.

## 12. Ce qui est vérifié, ce qui ne l'est pas

| Testé en CI | **Non vérifié** |
|---|---|
| Le protocole (identité, événements, preuves de travail, double dépense) avec un recoupement contre une implémentation Rust indépendante | Un vrai brûlage sur Solana |
| Le wallet, la part du créateur, la sauvegarde / restauration | L'appli et le widget sur un **vrai téléphone** (keystore, connexion GitHub, widget, mise à jour par le site) |
| Le registre (signatures, preuves, brûlage, classement) | Une vraie pull request par le workflow du registre |
| L'appli web dans Chromium (classement, bac à sable, falsification, hors ligne, publication, apps qui paient, duel entre deux pages) | La voix de bout en bout (mots de réveil sur écran verrouillé, « t'es où là », super agent parlé) |
| Le cerveau (les relais, sessions, boucles et liens, campagnes, agent, vidéos) contre de faux serveurs | De **vraies** sessions Claude Code cloud pilotées par le cerveau (boucles, agent, campagnes) |
| Le pont Kotlin (parseur, mots, pilules, campagnes) | Le widget **forme glasses** sur de vraies lunettes connectées |
| La chaîne vidéo (rendu, contrôle, voix, musique) | Le cast TV sur une vraie télévision |

Aucune critique n'est indépendante tant qu'elle est faite par la même chaîne qui produit.

## 13. Vision (pas démontrée)

Ce qui suit est **l'intention du projet**, pas ce qui tourne :

- **Mise à jour du corps par vote collectif** : décider des versions de l'application par un vote.
- **Registre interplanétaire**, store sans permission, nouveau genre de smart contract, **économie** gouvernée par le peuple, avec **brûlage de Solana**.
- **Preuve de Volonté** : nom donné dans le film à la preuve qui fonde le réseau. *Ce qui est spécifié aujourd'hui dans le yellow paper est la preuve
  de travail séquentiel (voir `docs/YELLOWPAPER.md`) ; « Preuve de Volonté » n'est pas encore définie dans les documents.*
- **Réseau social de découverte, d'engagement et de création de proximité** : les wallets ouverts se trouvent déjà et échangent ce qui leur manque ; le
  reste est à construire.
- **Code 100 % open en 2027.**

## 14. Licences et crédits

MIT, voir [`LICENSE`](LICENSE).

- Film : musique « *Killers* », **Kevin MacLeod** (incompetech.com), licence **CC BY 4.0** (créditée dans le film) ; voix : synthèse Piper ; polices Space
  Grotesk, Fraunces, JetBrains Mono (SIL OFL).
- Les catalogues de `music-research`, `asset-research` et `video-research` indiquent chacun la source et la licence de ce qu'ils référencent.
