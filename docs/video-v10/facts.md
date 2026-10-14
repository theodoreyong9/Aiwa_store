# Facts (stage 0): what is true and can be shown

Every claim of the film comes from a line below. Nothing else is said.

| Claim in the film | Where it is true |
|---|---|
| GitHub, Claude Code, Android; open source | the repository itself (MIT licence), `.github/workflows`, `android/` |
| The brain is updated live, by everyone; the body in one click | the backend updates itself from the repository; any proposal is a signed event (docs/ANDROID.md); the widget's update button installs a downloaded build (`AppUpdater.kt`) |
| Works with the screen off | foreground microphone service, wake word by an on-phone model (`WakeWordService.kt`). **Not yet tried on a phone**: said in the last scene |
| Call your agent; instructions and commands; the widget confirms | `VoiceDictation.kt`, `VoiceCommands.kt`; the widget shows « C'est prêt » when the production is done |
| Pages, videos, apps, sandbox; with your account, no API key; Claude does git and CI | the widget's modes (Pages, Android, Store, Aiwa, Média); sessions run on the person's Claude Code account; instructions in `aiwa_server.py` |
| Sessions talk to each other in loops: one builds, one criticises | `aiwa_loops.py` (builder / critic) |
| Campaigns: documents, targeting, personal productions, controlled sends (mail, WhatsApp, Telegram) | `aiwa-campagne/`, `ProposalsActivity.kt`: the person sends each message from their own apps |
| The super agent follows agents, productions, repositories; proposes; « vas-y, dis-lui », read back, confirmed | `aiwa_agent.py`, `WakeWordService.kt`. **Not yet tried with a real session** |
| Voice creation | the whole widget |
| The system develops itself; anyone can take part; it holds by its founding documents | the loop with its critic reads `aiwa-papers/` at every turn; comments are signed with a wallet |
| Wallet: burn SOL (once), production of AIWA, claim or not | `docs/YELLOWPAPER.md` §9 to §10.3; the wallet screen shown is the real one (apps/web) |
| Production: `b · q^α / ln(…)^γ`, `q` resets at each burn or claim, `q_tot` never | §10 and §10.1 |
| A burn pays the previous position first, as a claim | §10.1 « the previous position is paid first » |
| The curve slows with age (1 h 0.130, 1 day 1.84, 7 days 8.19, 30 days 26.9, 1 year 231 AIWA for 1 SOL at T = 0.2) | §10.2 table, a computed example, not a result |
| Ranking by what was mined, not by what is held | the registry orders by `score / laps` (registry/, store-ui.js) |
| Pay in a session, without a network | offline bundle in `docs/CONTRACTS.md` |
| Rules in JavaScript, replayed by each holder | `defineContract` (docs/CONTRACTS.md) |
| Earth and Mars: each keeps its own history; a reception links them, nothing is rewritten | §13.1 |
| Games: a file, a site, a verifiable contract, or Godot + Nakama in real time | `docs/GAME-FOUNDATION.md`: a standard to build to, no capacity measured |
| You host yourself; the code stays in your repository | Pages and releases of the person's account; link apps read from their own `aiwa-apps`; no server of ours (ntfy is a third-party relay: said as « aucun serveur à nous ») |
| Glasses | `docs/VISION-SOCIETE-AUGMENTEE.md`: a vision, no hardware demonstrated |

**Not claimed:** « sans double dépense » as a result (the protocol is designed for it; not proven on devices), the AIWA's value, any number of players, any speed of the agent.

**Material:** the wallet and Papers screens are real captures of the web app (`real/`, a throwaway wallet whose words are cropped out). The widget is drawn after the real one. No footage of a phone.
