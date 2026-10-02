# aiwa-registry

What decides which apps the store lists, in what order, and who may publish them. No server: the registry is a set of
files in this repository (`store/`) and a GitHub workflow that writes them.

## What an app is

A little metadata — `id` (lowercase letters, digits, hyphens), `name`, `version` (`major.minor.patch`), `description` — and its code, in one of two forms, both submitted on GitHub:

- **`code`**: one self-contained HTML file (≤ 512 KB; it loads what it needs from the network itself), carried by the package.
- **`aiwa`**: no code, a **pointer**: the id of a signed `bundle.manifest` event published through Aiwa (`aiwa-platform`'s bundle), which pins every file by hash. The submission carries the bundle's events (`src/bundle.js`): the registry has Aiwa itself verify them against the pinned manifest id — signed by the author's domain, naming exactly this app and version, listing every file, nothing else in the bundle, ≤ 40 files and 1 MB — and keeps them (`store/apps/<id>/<version>.bundle.json`). The Store checks them again before it runs anything, whoever served them: the code is immutable and is exactly what its author published. What a bundle's own dependencies load from the network (a CDN, the Aiwa SDK) is not pinned by the manifest.

What the author signs is not the content but its hash: the **package** (`src/app-package.js`) carries the content (or the pointer), the hash
(`sha256` of `[id, name, version, description, kind, html or manifestId]`) and an authorization — an `aiwa-lib` `signedAction` by the author's
key over `(publish-app, id, version, hash)`. The author's identity is their Solana address (the same key as their Aiwa
wallet). Anyone holding the package can check, offline, that what will run is exactly what the author published,
whoever hosts it. The store does exactly that before it opens an app.

## How an app gets in

1. The author's wallet (the Store's publish sheet) builds the package and the **evidence** of their mining
   (`wallet.submissionEvidence()`: only what the registry's baseline of them does not already hold, when it has one).
2. The sheet opens a pull request on the author's GitHub account that adds that one file as `submissions/<name>.json` (a browser,
   which cannot sign in to GitHub, hands the file over instead).
3. `.github/workflows/registry.yml` runs from `main`, reads the file as data (nothing in it is executed), and calls
   `bin/process.mjs`. If accepted it writes `store/index.json`, `store/apps/<id>/<version>.json` and its internal state,
   commits them to `main`, and closes the pull request with the verdict. A refused submission changes nothing.

## What is checked (`src/validate.js`)

| Check | Rule |
|---|---|
| Package | shape and size; the hash is that of the content; the signature is the author's, over this app, this version, this hash |
| Freshness | the signature is at most 24 hours old (a signature is not replayed later) |
| Ownership | an id belongs to the first author that published it; a new version must be higher |
| Mining evidence | `aiwa-core`'s `assessSubmission`: envelopes verified, progression proofs checked, **the burns confirmed against Solana by the registry itself** (the creator fee included: a commitment at T > 0 whose burn did not pay the creator counts no position), continued from the baseline the registry kept, witnesses other wallets hold required in the history shown |
| A new app | needs something claimable (`score > 0`); one new app per author every five minutes; at most 20 apps per author |
| The permission ratio | a new app is accepted only if the author's current `score / laps` is not below that of their last publication (a first publication is free of it). An update needs only ownership |

A **refresh** is a request, signed by the author, to re-read the ranking figure of one of their apps from fresh evidence.

## The ranking

`score / laps` (`src/rank.js`) — the ranking this project's apps have always been ordered by, taken over as it is. `score` is what the
author can claim and `laps` the epochs since their last action (both from `aiwa-core`'s `rankingFigure`), **frozen when
the registry accepted the submission** or last refreshed it. Ties go to the app published first. No editorial override, no
other term.

## What is not solved

- The figure is a snapshot the author chooses to refresh: a submission can leave out a burn made after the last epoch shown
  and followed by none (`aiwa-core/src/submission.js` says why: that needs a clock).
- Nothing says two authors are two people: the cost of publishing is the mining an author has to show, not an identity.
- Apps are sandboxed from the wallet but not from the network, and not reviewed. The store lists what the ranking lists.
- Not run here: the workflow itself on GitHub, and a burn against a real Solana (the tests use a stand-in that decodes the
  real transaction the wallet builds).

## Files

```
store/index.json                 what the store lists, best first (public, served with the web app)
store/apps/<id>/<version>.json   each package, never rewritten (public)
store/apps/<id>/<version>.bundle.json   an aiwa app's signed events (public)
store/baselines/<author>.json    what the registry derived from an author's events (public): to continue from, and what a wallet restores its mining from on a new phone
store/state/witnesses.json       what other wallets hold of each author (internal: kept in the repository, not served)
submissions/                     where a pull request puts its submission
deployment.json                  the parameters shared by the wallet and the registry (at the root)
```

`npm test -w aiwa-registry` runs the tests.
