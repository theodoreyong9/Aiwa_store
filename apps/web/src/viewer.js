// Where an app runs. The app is untrusted code: it goes into an iframe with sandbox="allow-scripts" and NEVER
// allow-same-origin, so the browser gives it an opaque origin — no access to this page's storage, DOM or wallet
// (yellow paper, §17.2). It can run and use the network; it cannot reach what opened it, unless it says it uses the wallet:
// then a banner says so and app-door.js answers its requests (it pays only once the player has allowed it).
import { $ } from './ui.js';
import { attachDoor, declaresWallet } from './app-door.js';

let frame = null;
let detachDoor = null;

export function openViewer({ name, meta = '', html }) {
  closeViewer();
  frame = document.createElement('iframe');
  frame.setAttribute('sandbox', 'allow-scripts');
  frame.setAttribute('referrerpolicy', 'no-referrer');
  frame.setAttribute('title', `${name}, sandboxed`);
  frame.srcdoc = html;
  $('viewer-name').textContent = name;
  $('viewer-meta').textContent = meta;
  const usesWallet = declaresWallet(html);
  $('viewer-flag').hidden = !usesWallet;
  detachDoor = usesWallet ? attachDoor(frame, { name }) : null;
  $('viewer-frame-host').replaceChildren(frame);
  $('viewer').hidden = false;
}

export function closeViewer() {
  detachDoor?.();
  detachDoor = null;
  $('viewer-flag').hidden = true;
  $('viewer').hidden = true;
  $('viewer-frame-host').replaceChildren();
  frame = null;
}

$('viewer-close').addEventListener('click', closeViewer);
document.addEventListener('keydown', (event) => { if (event.key === 'Escape' && !$('viewer').hidden) closeViewer(); });
