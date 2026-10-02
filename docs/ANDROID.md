# Android

One APK, `com.aiwa.store`. Its front door is the **Store**: the web app (`apps/web`) in a WebView, served from the APK's own
assets at `https://appassets.androidplatform.net/assets/web/` — a real origin, so the wallet's journal (IndexedDB) and module
scripts work. The wallet, the ranked list and the sandbox apps run in are the web app's; the activity only hosts it
(`StoreActivity.kt`).

## What the page may ask of the phone

One channel, `window.AiwaHost`, added by the app to the page's own origin only. The frame an app runs in has another (opaque)
origin: it does not get the channel (an object added with `addJavascriptInterface` would be injected in every frame, apps
included). Commands: `save` (a text file into Downloads: the submission to put in a pull request) and `dictation` (open the
dictation module's screen). The back button closes what is open on top (an app) before it leaves. Links out of the Store open in
the browser.

`allowBackup` is off: the wallet's journal goes nowhere unless the user exports a backup from the Recovery panel.

## The dictation module (optional)

A widget for the home screen to dictate to Claude Code (Claude's cloud sessions, `claude --cloud`), not specific to Aiwa. It needs
**Termux** (the local backend, `android/backend/aiwa_server.py`, runs there) and the user's own Claude login; nothing about it is
needed for the Store or the wallet. It runs only once it has been turned on — by adding the widget, or by opening its screen from
the Store's footer ("Dictation (optional)"). Until then there is no service, no permission request and no notification.

Install the backend in Termux (one command, also for updates):

```sh
curl -fsSL https://raw.githubusercontent.com/theodoreyong9/Aiwa_store/main/android/backend/bootstrap.sh | bash
```

### The Store mode

The widget's Deploy chip offers `store`: Claude is asked for ONE app for the Store, a self-contained `nom.app.html` (the
constraints are in the instruction and in [`store-app-example.html`](store-app-example.html)), checks it, and sends it to the
phone through a public relay (ntfy.sh). The ▦ button opens the Store's Publish tab with the code in the field; the user reads it,
tries it and signs it. Nothing is signed or published by the widget. Not tied to a GitHub repository, nothing is pushed.

## Build

The APK is compiled in CI only (`.github/workflows/android.yml`: the web app, the backend tests, the bridge's unit tests, then
`gradle :app:assembleDebug`), signed with the fixed debug key so that it installs over the previous one, and published as one file
of a rolling release, `android-latest`. Locally: `npm run build -w aiwa-store-web`, then `gradle -p android :app:assembleDebug`
(the build copies `apps/web/dist` into the assets).

## Not verified

The WebView host and the Termux backend on a real device; the widget on real launchers; Google Play policy (the APK is meant to be
installed by hand). No Android SDK is available where this was written: the Kotlin was never compiled before CI.
