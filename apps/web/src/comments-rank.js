// The comments of a registry index, ranked. Kept apart from the page (no DOM) so that it can be tested.
const PATH_OK = /^comments\/[A-Za-z0-9._-]{1,120}\.json$/;

/** The valid comments of a registry index, best ranked first (the author's score per lap is the weight). */
export function rankedComments(index) {
  const list = Array.isArray(index?.comments) ? index.comments : [];
  return list
    .filter((e) => e && e.kind === 'comment' && typeof e.id === 'string' && typeof e.author === 'string' && typeof e.path === 'string' && PATH_OK.test(e.path)
      && Number.isFinite(Number(e.score)) && Number.isFinite(Number(e.laps)))
    .map((e) => ({ ...e, weight: Number(e.score) / Math.max(1, Number(e.laps)) }))
    .sort((a, b) => b.weight - a.weight || (Number(b.updatedAt) || 0) - (Number(a.updatedAt) || 0));
}

