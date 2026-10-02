# submissions/

A pull request that publishes an app (or refreshes its ranking) adds **one** file here, `submissions/<name>.json`
(`<name>`: letters, digits, `.`, `_`, `-`). The Store's publish sheet makes it and opens the pull request itself (Android app); from a browser it hands the file over.

The file is data. The registry's workflow (`.github/workflows/registry.yml`) reads it from the pull request without
running anything in it, validates it with `registry/` and, if it is accepted, writes `store/` on `main` itself —
the pull request is never merged, and it is closed with the verdict. See [`registry/README.md`](../registry/README.md).
