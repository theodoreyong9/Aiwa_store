# Android

One APK, `com.aiwa.store`. Its front door is the **Store**: the web app (`apps/web`) in a WebView, served by the app itself at
`https://appassets.androidplatform.net/` — a real origin, so the wallet's journal (IndexedDB) and module scripts work. The wallet, the
ranked list and the sandbox apps run in are the web app's; the activity only hosts it (`StoreActivity.kt`).

## Where the page comes from

The page is the site's, not the APK's: the Store updates itself, and a new APK is only needed to change the app around it (the widget,
the permissions, the Keystore, this code). Two copies, served under the same origin so the wallet's storage is the same:

| Copy | Served at | When |
|---|---|---|
| inside the APK | `/assets/web/` | the first run, offline, and whenever it is newer than the other |
| downloaded from the site (`siteUrl` in `deployment.json`) | `/site/` | from the start after the download, if it is newer than the copy inside the APK |

The download is checked entirely before it is kept (`SiteRelease.kt`, the same checks as `apps/web/release.mjs`):

1. `release.json` lists every file of the page with its SHA-256, a version and a date. It is made by the *Pages* workflow, and by the
   Android build for the copy inside the APK.
2. Each file is read and compared with its hash, so the page is one whole release and never a mix of two deployments or a file cut short.
   A name that climbs out of the folder, a missing `index.html`, more than 400 files, 5 MB for a file or 24 MB in all are refused.
3. All of it goes to `pending/` as a whole, or nothing does. It replaces the page at the **next start**, never while the page runs
   (the Store loads some of its code late, and must find the files of its own release), with one exception: a release that comes in during the
   first 10 seconds after the page opened replaces it at once (the page is reloaded, nothing has been done on it yet).
4. A release older than the one held, or listing the very same files, is not an update. After the APK itself is updated, a downloaded
   copy older than the page inside it is dropped.

The app looks each time the Store opens or comes back in front (at most every two minutes), and the widget's service looks every ten minutes on its own, in the
background; a release found later than the first 10 seconds of a start is downloaded and put in place the next time the app comes back in front (the
page is reloaded then). The page is the same web app (`apps/web`) as on Pages: the app only keeps its own checked copy, so
that it opens offline and never runs a half-downloaded page. What differs is how soon a new deployment shows: the Pages workflow takes a minute or two,
then the phone has to fetch it (within ten minutes, or at once when the app is opened).

**Whose word it is.** There is no signature: what the site publishes is what the phones run, and the site is published by the repository's
GitHub account, so that account is the key. The page travels over HTTPS and the hashes only keep it whole. If one day the Store holds
real value and the account is a risk, a signature (a key kept off GitHub, checked by the APK) can be added in front of the same download.

Not run on a phone: the checks are unit-tested on a JVM (`SiteReleaseTest`, against a release written by the Node side); the download, the
swap and the serving from app storage have never run on a device.

**Install, step by step** (the Store and wallet, then the optional widget): [README → Install](../README.md#install-android).

## What the page may ask of the phone

One channel, `window.AiwaHost`, added by the app to the page's own origin only. The frame an app runs in has another (opaque)
origin: it does not get the channel (an object added with `addJavascriptInterface` would be injected in every frame, apps
included). The page sends requests with an id and the app answers `{ id, result }`, `{ id, error }` or `{ id, progress }`:

| Command | What it does |
|---|---|
| `secret-get` / `secret-set` / `secret-delete` | the wallet's 12 words and the GitHub token, encrypted with an AES key that lives in the Android Keystore (`SecretStore.kt`) |
| `github-login` | GitHub's device flow, run natively because GitHub's login endpoints send no CORS headers (`GitHubDeviceFlow.kt`): the code to type is sent as progress (it is also copied, and github.com opened), the token as the result. Scope `public_repo` only; the OAuth App is `deployment.json`'s `github.clientId`, with Device Flow enabled |
| `save` | a text file into Downloads |
| `dictation` | open the dictation module's screen |

The camera is not a command: the page reads QR codes with `getUserMedia` (a payment received, two phones pairing for an app), which the WebView asks of `StoreActivity` as a permission request. It is granted to the Store's own origin only, for video only, once Android's camera permission is given.

The back button closes what is open on top (the sheet, an app) before it leaves. Links out of the Store open in the browser.

**Backup.** Android's automatic backup is on: the WebView's storage, where the wallet's journal lives, follows its owner to a new phone
(Google Drive or a phone-to-phone transfer), without any server of ours. The secrets are excluded (`res/xml/data_extraction_rules.xml`): a
Keystore key does not move, so the 12 words are typed once on the new phone, and the journal is there. If the backup did not carry it,
the wallet asks the registry for the last state it validated of this author. Neither path has been tried on a real device.

## The voice grammar: one reference

What can be said to the pills is one table, `PillReference` (the bridge): the effort, the video quality and the project types with the words that say each, the push words, and the help of the menu d'Aiwa is generated from it. What cannot be known in advance is matched at the end of the dictation against the lists the phone holds then: the **repository** (the GitHub list, read when the dictation begins; exact name, part of a name, or the nearest name when it was heard badly, if clearly the nearest), the **session** (a part of its title, among the sessions the app knows) and the **model** (the list of the app). Nothing is guessed: a name that matches nothing, or several things, is reported and changes nothing. The dictation itself is free text from Android's recognizer, so a name is only as good as what was heard.

## The dictation module (optional)

A widget for the home screen to dictate to Claude Code (Claude's cloud sessions, `claude --cloud`), not specific to Aiwa. It needs
**Termux** (the local backend, `android/backend/aiwa_server.py`, runs there) and the user's own Claude login; nothing about it is
needed for the Store or the wallet. It runs only once it has been turned on — by adding the widget. Until then there is no service, no permission request and no notification.

Install the backend in Termux (one command, also for updates):

```sh
curl -fsSL https://raw.githubusercontent.com/theodoreyong9/Aiwa_store/main/android/backend/bootstrap.sh | bash
```

**Why the person pastes it.** An app can neither install another app nor write into it, and Termux refuses commands from other apps until
`allow-external-apps=true` is set in it (the line does that too). So the widget can start the backend once it is there, never install it.
When it is not there, the widget says what is missing instead of "starting":

| What the widget shows | What it means | What a tap does |
|---|---|---|
| "Termux manquant" | Termux is not on the phone (the app checks: Android itself says nothing when asked to start an app that is absent) | copies the line, opens the F-Droid page of Termux |
| "Installation manquante" | Termux was asked twice to start the backend and it never answered: it is not installed in it (or Termux refuses) | copies the line, opens Termux |
| "Dernier envoi échoué" | the backend answers but the last send went wrong; the reason was only a toast | opens the app, which shows it |

While it is missing the app stops restarting it; once the line has been pasted and the backend answers, the widget goes back to normal
by itself. (`BackendHealth.kt`, tested on a JVM; the widget and the activity are compiled in CI, never run on a phone.)

### The widget: what each part is

Four bands, from the top: the title and status (the logo opens the app), the **repository**, **model** and **push** chips, the **project type** chip with its round buttons, and the voice row. Everything it does is an instruction sent into the conversation with Claude Code; the widget itself builds, signs and publishes nothing.

| Part | What it is | Source |
|---|---|---|
| Voice button (before the mic) | the **menu d'Aiwa**: wake word, state of Aiwa (Claude, alerts, permissions), *update the brain* (Termux), *update the application*, the always-on listening, the voice commands | `Pickers.kt` (`VoiceHelpActivity`) |
| Project type chip | what Claude must produce, from the next message: *Pages*, *Android*, *Store*, *Aiwa*, *Campaign* (see below) | `Pickers.kt` (`DeployPicker`), `aiwa_server.py` (`DEPLOY_MODES`, `_instruction_lines`) |
| Site / APK button | Pages and Android modes (the address of the site or of the APK); hidden in the campaign mode | `AiwaWidget.kt` |
| Proposals button (envelope) | **in the campaign mode, instead of the site button**: the proposals, to be sent from the person's mail application | `ProposalsActivity.kt` |
| ▦ and `</>` | Store and Aiwa modes: open the Store on the app Claude sent (to read, try, add) and on its code | `OpenStoreActivity.kt`, `StoreActivity.kt` |
| Video button (at the left of the mode chip) | the promotional videos of the creation, **played in the app** (a player of its own, with a button to hand the file to the browser); the **quality** (simple, medium, extra) is chosen where a video is asked for | `VideosActivity.kt` |
| GitHub Actions button | the last run of the repository: the build (Android), the deployment (Pages, campaign), the check of the app (Store), the registry's check of the pull request (Aiwa) | `OpenActionsActivity.kt`, `aiwa_server.py` (`_ci_snapshot`) |
| Create from a file | menu d'Aiwa: files become a video **or a campaign**: several files ticked among those of the folder `aiwa-campagne/` of the chosen repository, and/or a text file of the phone (kept there by the session); GitHub work is an accessory (off by default) | `DocumentCreation.kt` |

The repository chip and the **project-type chip** are always there: the Store, Aiwa and campaign modes need no repository, so the type is chosen first. The push chip, the site button, the video button and the GitHub Actions button need a repository and appear once one is chosen.

**The five modes.** A repository can have all of them: a mode is only the instruction of the next message, and each writes in its own place (the site, the release, `aiwa-apps/`, `aiwa-proposals/`).

- `pages`: a site published by GitHub Pages. `android`: the APK built by GitHub Actions and published to the rolling release.
- `store` (**without permission**): ONE app, a self-contained `nom.app.html` ([`store-app-example.html`](store-app-example.html)). Claude puts it in the person's **own public repository**, `aiwa-apps/nom.app.html`, with a small check workflow (`.github/workflows/aiwa-app-check.yml`). There is **no registry, no wallet, no signature, no ranking**: the Store's *Library* lists the GitHub accounts the person follows (`login` → its repository `aiwa-apps`, `login/repo` → the `aiwa-apps/` folder) and reads the code from GitHub as it is, so its author edits it freely (`apps/web/src/library.js`). ▦ adds the account to that list.
- `aiwa`: the same kind of file, `nom.aiwa.html`, whose logic is an Aiwa **contract** ([`aiwa-app-example.html`](aiwa-app-example.html)): frozen, signed, pinned by hash, published through the registry (a package that is only a pointer to the signed manifest, and the signed bundle), **ranked by mining**. It is the only mode that uses the wallet. The GitHub Actions button follows the registry's check of the pull request.
- `campaign`: from what the request names (a file of the phone or of `aiwa-campagne/`, the creation and its videos, the ticked GitHub work: no file is required), « classique » or « innovante » channel by channel (the style of the last send on each channel, or something else), the agent proposes the list of targets and waits for the answer, then prepares for each a mock-up site (a tree published by Pages, `aiwa-proposals/<target>/`), a video and a price, and writes `aiwa-proposals/proposals.json`; the person validates and sends each proposal from their own mail application, or opens it in WhatsApp, SMS or Telegram (the address each publishes, with the message ready; no account, no key, `ProposalShare` in the bridge), prepares a post on X (the person's own wall), or hands the video to the phone's sharing sheet (YouTube, Instagram…; `VideoSharing`). Nothing is sent by Aiwa and no reply is followed.

The globe of the Store's own repository opens the app, not a browser: the site is the page the app serves, and a browser would be a second copy of it with a wallet of its own.

**Two updates, two entries of the menu.** *The brain* is the backend in Termux (`bootstrap.sh`, a `git pull` and a restart). *The application* (`AppUpdater.kt`) reads the rolling release `android-latest`, compares its commit with the installed `0.1.<commit>`, downloads the APK and opens the system installer: the person still taps « Installer » (Android never installs silently), after allowing Aiwa to install applications once. The first APK with this entry must be installed by hand.

While a message is on its way (creating a session takes a while) the widget says how long it has lasted and what it is doing (a step of the backend's, then the last line the CLI printed, such as the progress of an upload), and the settings (repository, model,
Push, Deploy, the session name) and the mic are dimmed and say why when tapped; what only looks (the site, the app, the code, Actions, Claude)
stays alive. A creation is given up when the CLI has printed nothing for 150 seconds or after 10 minutes in all, not at a fixed 3 minutes (an upload from the phone, when the CLI decides to send the folder, can be slow). A creation whose CLI stops to ask something (such as trusting a folder it has never seen) is reported after 30 seconds with what it
asks and what to do, instead of after the 3 minutes of the timeout; a timeout says at which second the CLI began and what it printed. The whole run
is in `~/aiwa_cloud_last.log`.

**Red dot, and repositories of inspiration.** A small red dot sits on the widget's site button (or on the Actions button when there is no site) when the repository has a new green run that finished after the last message sent; opening the site or Actions takes it away (those two buttons open a no-UI page of the app, `SiteButtonActivity` and `OpenActionsActivity`, which decide what to open at the moment of the tap). The repository list also keeps, apart from the repository Claude works on and the ones it may also act on, up to ten *repositories of inspiration*: addresses copied from GitHub that every message names to Claude as read-only (it is told never to change them nor push to them, and how to read a file of one without attaching it). A copy of a repository into the person's account is GitHub's own fork page, opened for the copied address: Aiwa holds no GitHub token, so it cannot clone for the person. What is tested is the backend (the list, the refusal at 11, the sentence in the instructions); the widget is compiled in CI, not tried on a phone.

**Changing repository in the middle of a session.** The session stays: like a new push mode or a new model, the new repository is an instruction. From the next message the session is told once that it works there now (it attaches it with `add_repo`, and the branch, push, deploy and verification instructions apply to it), and told again if the person goes back to the repository it started on. Whether Claude's platform lets a session attach a repository it was not started on is not something Aiwa can check; when it is refused, Claude says so. Tested on the backend (what is sent, once, and what is kept); not tried with a real session.

A session added by its branch gets a repository only when the branch is found in exactly one of the candidate repositories; found in several (a session can work on several with the same branch name), it gets none, and the widget asks (a red dot on the repository chip, a line in the status band) rather than guess where Claude pushes.

**Voice instructions to Aiwa.** In a dictation, what comes before *« instruction Aiwa »* is the message for Claude; after it, the pills' keywords are commands (*modèle*, *push*, *déploiement*, *session*, *dépôt*), chained freely, applied when *« c'est bon vas-y »* ends the dictation, and the rest of the words stay text for Claude. A repository is only changed after the phone asks aloud and hears *« c'est bon vas-y »* again. A name that matches nothing, or several things, changes nothing. The phone also says *« C'est prêt »* when a new build ends green. *« Non c'est pas bon, arrête »* (or *« annule »*) at the end of what was said drops the dictation; *« stop »* / *« arrête Claude »* after *instruction*, or *stop Claude* alone as a wake phrase, sends the session a message asking it to stop (read at its next step: not a hard interruption). The parser is tested on a JVM (`VoiceCommandsTest`); the voice and the mic are compiled in CI and were never run on a phone, and how the phone's recognizer writes *Aiwa* is not known.

**Listening all the time, and « t'es sur quoi ».** The widget's voice button (before the mic) lists the commands and holds the switch of the listening; it is green while it listens. A switch on the app's screen (off by default) starts a service that listens for two phrases, *« mon agent »* and *« instruction »*, with a small speech model that runs on the phone (Vosk, French, about 41 MB downloaded once): nothing the microphone hears leaves the phone. Hearing one opens a dictation with the words after it read as instructions to Aiwa from the start; *« mon agent »* only wakes it; and since nobody looks at a screen, **before anything is applied or sent, the phone reads back what it will do and every pill as it will be (repository, model, effort, push, mode, video quality, session), and waits for « c'est bon vas-y »**; « non » or silence applies and sends nothing and copies the message. *« t'es sur quoi »*, *« rapport »*, *« statut »* or *« bilan »* has the phone read the pills aloud. Limits: the word *Aiwa* is not in the model's vocabulary, so the wake phrases are French ones; Android shows its green microphone dot for as long as it listens and does not let the service start by itself after a restart of the phone (open the app); the battery goes down faster. Compiled in CI, never run on a phone; the model archive's checksum is not pinned.

**How what Claude says reaches the phone.** An app, the "Claude waits" alert and the relay test all go to a public relay (ntfy.sh) with one
`curl`. That needs the cloud environment to allow `ntfy.sh`, a setting only claude.ai can change (a new environment is on the default list,
which does not have it; *État d'Aiwa* then says the cloud alerts are blocked). There is a second road that needs no setting, because a
cloud session always has GitHub: **when the session was started on a repository** (the widget's repository chip, chosen *before* the session
starts), the instructions add *if the command fails, write one line in `aiwa-out/SIGNAL`, commit it on your own branch and push it*:
`app <name> <time>` (with the app file next to it), `attend <time>` (only when Claude waits for an answer or a decision, not after every
reply, to keep the branch quiet) or `check <time>` (the relay test, which makes the "alerts blocked" notice go away by itself). For 45 minutes
after each message the backend reads that file from `raw.githubusercontent.com` every 20 s (a new address each time, because the CDN keeps a
file for minutes) and acts on a signal once, if it answers the last message.

Conditions: a repository chosen when the session starts (every new session starts on one: the widget asks for it before anything can be
sent, and a session begun earlier without one keeps working but has no road to GitHub; a session made elsewhere is added by the name of its
branch, `claude/…`, which gives the repository and the branch, and a link alone is refused), **public** (nothing is read from a private one, then the alert does not come and Claude pastes the app), and a push to its branch
allowed. The fallback files (`aiwa-out/`) stay on that branch, never on the main one. Tested against fakes of the three servers (`MailboxTests`); not tried with a
real cloud session.

### The Android mode

Claude builds the repository's APK with GitHub Actions and publishes it to the rolling release; the widget's download button opens that address. An APK
whose app needs Termux (a backend to start there, like this one) cannot be told apart from another one by looking at it, and the line to paste is
the app's own: so Claude is asked to write `aiwa-android.json` at the root of the repository when the app needs Termux, `{"termux": "<the line>"}` (one line,
600 characters at most), and that line does everything, the APK's download included, as this project's own does (`bootstrap.sh`, `install-apk.sh`). The phone
reads it from `raw.githubusercontent.com` (a public repository only, every 2 minutes), and the download button then copies the line, shows it in a toast and opens
Termux (or the page it is downloaded from): nothing is run, the person pastes it and validates. No declaration, no line, and the button downloads the APK as before;
a private repository never shows one. This repository declares none: the Aiwa APK downloads like any other, and updates itself from the menu d'Aiwa (the brain in Termux is a separate update).

In the `aiwa` mode the ▦ button opens the Store's publish sheet with the app in it; the user reads it, tries it and presses **Publish**: the Store signs it
with the wallet and opens the pull request on the user's GitHub account. Nothing is signed or published by the widget. In the `store` mode ▦ only adds the
person's GitHub account to the Library: the app is already where it lives.

## Build

The APK is compiled in CI only (`.github/workflows/android.yml`: the web app, the backend tests, the bridge's unit tests, then
`gradle :app:assembleDebug`), signed with the fixed debug key so that it installs over the previous one, and published as one file
of a rolling release, `android-latest`. Locally: `npm run build -w aiwa-store-web`, then `gradle -p android :app:assembleDebug`
(the build copies `apps/web/dist` into the assets).

## Not verified

The WebView host, the camera for scanning codes and the Termux backend on a real device; the keystore and the GitHub login on a real device (the device flow's logic is
unit-tested against a stand-in of GitHub, not against GitHub); the automatic backup; the widget on real launchers; Google Play policy (the APK
is meant to be installed by hand). No Android SDK is available where this was written: the Kotlin that is not plain JVM is compiled in CI only.
