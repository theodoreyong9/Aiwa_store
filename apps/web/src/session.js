// What the tabs share: the one wallet the page holds, and the list the store last read (the wallet shows its author's apps
// from it, the publish sheet numbers versions from it).
export const session = { aiwa: null };
const sessionListeners = new Set();
export const onSession = (fn) => { sessionListeners.add(fn); };
export const setSession = (aiwa) => { session.aiwa = aiwa; for (const fn of sessionListeners) fn(aiwa); };
export const connected = () => !!(session.aiwa && session.aiwa.identity);

export const catalog = { apps: [] };
const catalogListeners = new Set();
export const onCatalog = (fn) => { catalogListeners.add(fn); };
export const setCatalog = (apps) => { catalog.apps = apps; for (const fn of catalogListeners) fn(apps); };
