// What the Android app offers the page: a way to save a file and to open the dictation module's screen, and a back button
// that closes what is open before it leaves. In a browser none of it exists and the page does without.
//
// The channel is `window.AiwaHost`, added by the app to this page's own origin only: the sandboxed frame an app runs
// in has another origin and does not get it.
export const hostAvailable = () => typeof window.AiwaHost?.postMessage === 'function';

export function hostPost(command) {
  window.AiwaHost.postMessage(JSON.stringify(command));
}

let backHandler = () => false;
export function setBackHandler(fn) { backHandler = fn; }
// Called by the app on the back button: true when the page used it (closed something), false to let the app leave.
window.aiwaHostBack = () => backHandler();
