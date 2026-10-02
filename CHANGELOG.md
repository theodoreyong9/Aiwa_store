# Changelog

## Unreleased

- Initial import of the protocol (`packages/core`), the distribution layer (`packages/platform`) and the wallet API (`packages/lib`) into one workspace, with their tests (core 410, platform 85, lib 95). One lock file; the packages depend on each other through the workspace, not through pinned commits.
- Plain-words explanation in English and French.
- The creator fee: a deployment may set `rewardParams.creatorFee = { address, rateOfT }`. A fixed part of the T share of every burn then goes to that address in the same transaction; readers count it and refuse a commitment at T > 0 whose burn did not pay it. Core 418 tests, lib 100.
- Yellow paper rewritten as a standalone specification (version 1.0), with the creator fee (§7.3).
- `registry`: app packages signed by their author (`aiwa-app/1`), validation of submissions by pull request with `assessSubmission` (the burns confirmed by the registry itself, the creator fee included), the existing `score / laps` ranking, the permission ratio, refresh; a workflow that reads the submission as data and writes `store/`. 13 tests.
- `apps/web`: the store (ranked list, search, apps in `<iframe sandbox="allow-scripts">`, offline), the wallet (the burn shows the creator fee before signing, send/receive, recovery), publishing (a signed package with the mining evidence, ready for a pull request). Tested in Chromium.
- `android`: the dictation widget and its Termux backend, now an optional module; one mode, `store` (Claude writes one self-contained HTML app, which opens in the Store's Publish tab), replaces the two modes tied to other projects; the Store runs in a WebView served from the APK's assets; the module starts only once the widget is added or its screen opened. CI builds the APK. Backend 68 tests.
- `docs/BUSINESS.md`.
