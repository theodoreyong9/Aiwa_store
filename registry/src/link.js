// A link: an app of the Store that LIVES IN ITS AUTHOR'S OWN GITHUB REPOSITORY (the Store mode without permission). The registry only lists a
// pointer to it; the Store reads the code from the repository when the app is opened, so the author edits it as they like. No wallet, no
// signature, no ranking: the frozen and ranked mode is the Aiwa one.
//
//   submission = { format: 'aiwa-submission/1', kind: 'link', id, name, description, repo: 'owner/name', path: 'dir/file.html', ref: 'main' }

export const LINK_LIMITS = {
  idPattern: /^[a-z0-9](?:[a-z0-9-]{0,38}[a-z0-9])?$/,
  nameChars: 60,
  descriptionChars: 280,
  repoPattern: /^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})\/[A-Za-z0-9._-]{1,100}$/,
  pathPattern: /^(?!.*\.\.)[A-Za-z0-9._\/-]{1,200}\.html$/,
  refPattern: /^(?!.*\.\.)[A-Za-z0-9._\/-]{1,100}$/,
};

/** Why a link submission is refused, or null. */
export function linkProblem(s) {
  if (!s || typeof s !== 'object') return 'The link is not an object';
  if (typeof s.id !== 'string' || !LINK_LIMITS.idPattern.test(s.id)) return 'The id is 1 to 40 characters: lowercase letters, digits and hyphens, not starting or ending with a hyphen';
  if (typeof s.name !== 'string' || s.name.trim().length === 0 || s.name.length > LINK_LIMITS.nameChars) return `The name is 1 to ${LINK_LIMITS.nameChars} characters`;
  if (s.description !== undefined && (typeof s.description !== 'string' || s.description.length > LINK_LIMITS.descriptionChars)) return `The description is at most ${LINK_LIMITS.descriptionChars} characters`;
  if (typeof s.repo !== 'string' || !LINK_LIMITS.repoPattern.test(s.repo)) return 'The repository is owner/name';
  if (typeof s.path !== 'string' || !LINK_LIMITS.pathPattern.test(s.path)) return 'The path is that of one .html file in the repository';
  if (typeof s.ref !== 'string' || !LINK_LIMITS.refPattern.test(s.ref)) return 'The ref is a branch or a tag';
  return null;
}
