// The store's ranking: score / laps — the ranking this project's apps have always been ordered by, taken over as it is: the figure an author's mining
// state gives (aiwa-core's rankingFigure): `score` is what the author can claim, `laps` the epochs since the last
// action, both frozen at the moment the registry accepted the submission. No editorial override, no other term.

/** score / laps (laps is at least 1). */
export function ratioOf({ score, laps }) {
  return (Number(score) || 0) / Math.max(1, Number(laps) || 1);
}

/** The apps best first: higher score / laps; on a tie the one published first, then the id. Does not change `apps`. */
export function rankApps(apps) {
  return [...apps].sort((a, b) =>
    ratioOf(b) - ratioOf(a)
    || (a.publishedAt ?? 0) - (b.publishedAt ?? 0)
    || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
}
