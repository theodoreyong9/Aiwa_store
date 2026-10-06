// What the tabs share: the one wallet the page holds, and the list the store last read (the wallet shows its author's apps
// from it, the publish sheet numbers versions from it).
// starting: the wallet page is still deciding whether a wallet exists (until it says so, nobody should be told there is none)
export const session = { aiwa: null, starting: true };
const sessionListeners = new Set();
export const onSession = (fn) => { sessionListeners.add(fn); };
export const setSession = (aiwa) => { session.aiwa = aiwa; for (const fn of sessionListeners) fn(aiwa); };
export const connected = () => !!(session.aiwa && session.aiwa.identity);

export const catalog = { apps: [] };
const catalogListeners = new Set();
export const onCatalog = (fn) => { catalogListeners.add(fn); };
export const setCatalog = (apps) => { catalog.apps = apps; for (const fn of catalogListeners) fn(apps); };
