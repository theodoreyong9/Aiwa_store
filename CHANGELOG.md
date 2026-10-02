# Changelog

## Unreleased

- Initial import of the protocol (`packages/core`), the distribution layer (`packages/platform`) and the wallet API (`packages/lib`) into one workspace, with their tests (core 410, platform 85, lib 95). One lock file; the packages depend on each other through the workspace, not through pinned commits.
- Plain-words explanation in English and French.
- The creator fee: a deployment may set `rewardParams.creatorFee = { address, rateOfT }`. A fixed part of the T share of every burn then goes to that address in the same transaction; readers count it and refuse a commitment at T > 0 whose burn did not pay it. Core 418 tests, lib 100.
