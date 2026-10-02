// The one wallet the page holds, shared by the tabs.
export const session = { aiwa: null };
const listeners = new Set();
export const onSession = (fn) => { listeners.add(fn); };
export const setSession = (aiwa) => { session.aiwa = aiwa; for (const fn of listeners) fn(aiwa); };
export const connected = () => !!(session.aiwa && session.aiwa.identity);
