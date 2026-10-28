/* Aiwa home runtime: fills the SLOTS of a page with the buttons of the application (the widget's buttons), whoever wrote the page.
 *  <div data-aiwa-slot="build"></div>            all the buttons of a group: domain | files | build | voir      (several groups: "build voir"; "all": every group)
 *  <button data-aiwa-button="docs"></button>     one button (any id of the catalogue below)
 *  data-aiwa-only="docs,media"  data-aiwa-skip="mcp"  keep or drop buttons in a slot;  data-aiwa-look="icons|labels|both"  how a slot draws them.
 * The buttons ARE the application's, never the page's: they act on the account of whoever looks (the visitor on a profile that is not hers). The page styles them
 * with plain CSS (class .aiwa-btn, attributes data-tone, data-on, data-id; variables --aiwa-*); this file brings a base look that any rule of the page beats.
 * The native side listens on AiwaHomeHost (a press is a message {cmd:"press", id}) and calls aiwa.setState({id:{label,tone,on,found,icon}}) every few seconds to say what each
 * button shows now; without it (a preview in a browser) a press raises the event "aiwa-press" on the document. */
(function () {
  if (window.__aiwaHome) { if (window.aiwa) window.aiwa.render(); return; }
  window.__aiwaHome = true;
  var ICONS = {"project": "<path d=\"M4 20h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.93a2 2 0 0 1-1.66-.9l-.82-1.2A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13c0 1.1.9 2 2 2Z\" /> <path d=\"M8 10v4\" /> <path d=\"M12 10v2\" /> <path d=\"M16 10v6\" />", "loops": "<path d=\"m17 2 4 4-4 4\" /> <path d=\"M3 11v-1a4 4 0 0 1 4-4h14\" /> <path d=\"m7 22-4-4 4-4\" /> <path d=\"M21 13v1a4 4 0 0 1-4 4H3\" />", "wallet": "<path d=\"M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1\" /> <path d=\"M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4\" />", "settings": "<path d=\"M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z\" /> <circle cx=\"12\" cy=\"12\" r=\"3\" />", "mcp": "<path d=\"M12 22v-5\" /> <path d=\"M9 8V2\" /> <path d=\"M15 8V2\" /> <path d=\"M18 8v5a4 4 0 0 1-4 4h-4a4 4 0 0 1-4-4V8Z\" />", "cast": "<path d=\"M2 8V6a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-6\" /> <path d=\"M2 12a9 9 0 0 1 8 8\" /> <path d=\"M2 16a5 5 0 0 1 4 4\" /> <line x1=\"2\" x2=\"2.01\" y1=\"20\" y2=\"20\" />", "campaigns": "<path d=\"m3 11 18-5v12L3 14v-3z\" /> <path d=\"M11.6 16.8a3 3 0 1 1-5.8-1.6\" />", "docs": "<path d=\"M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z\" /> <path d=\"M14 2v4a2 2 0 0 0 2 2h4\" /> <path d=\"M10 9H8\" /> <path d=\"M16 13H8\" /> <path d=\"M16 17H8\" />", "media": "<rect width=\"18\" height=\"18\" x=\"3\" y=\"3\" rx=\"2\" /> <path d=\"M7 3v18\" /> <path d=\"M3 7.5h4\" /> <path d=\"M3 12h18\" /> <path d=\"M3 16.5h4\" /> <path d=\"M17 3v18\" /> <path d=\"M17 7.5h4\" /> <path d=\"M17 16.5h4\" />", "app": "<rect width=\"14\" height=\"20\" x=\"5\" y=\"2\" rx=\"2\" ry=\"2\" /> <path d=\"M12 18h.01\" />", "page": "<circle cx=\"12\" cy=\"12\" r=\"10\" /> <path d=\"M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20\" /> <path d=\"M2 12h20\" />", "contract": "<path d=\"M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z\" /> <path d=\"M14 2v4a2 2 0 0 0 2 2h4\" /> <path d=\"m9 15 2 2 4-4\" />", "agent": "<path d=\"M12 8V4H8\" /> <rect width=\"16\" height=\"12\" x=\"4\" y=\"8\" rx=\"2\" /> <path d=\"M2 14h2\" /> <path d=\"M20 14h2\" /> <path d=\"M15 13v2\" /> <path d=\"M9 13v2\" />", "claude": "<path d=\"M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z\" /> <path d=\"M20 3v4\" /> <path d=\"M22 5h-4\" /> <path d=\"M4 17v2\" /> <path d=\"M5 18H3\" />", "listen": "<path d=\"M6 8.5a6.5 6.5 0 1 1 13 0c0 6-6 6-6 10a3.5 3.5 0 1 1-7 0\" /> <path d=\"M15 8.5a2.5 2.5 0 0 0-5 0v1a2 2 0 1 1 0 4\" />", "plus": "<path d=\"M5 12h14\" /> <path d=\"M12 5v14\" />", "scenario": "<rect width=\"8\" height=\"8\" x=\"3\" y=\"3\" rx=\"2\" /> <path d=\"M7 11v4a2 2 0 0 0 2 2h4\" /> <rect width=\"8\" height=\"8\" x=\"13\" y=\"13\" rx=\"2\" />", "visible": "<path d=\"M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0\" /> <circle cx=\"12\" cy=\"12\" r=\"3\" />", "hidden": "<path d=\"M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49\" /> <path d=\"M14.084 14.158a3 3 0 0 1-4.242-4.242\" /> <path d=\"M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143\" /> <path d=\"m2 2 20 20\" />", "nearby": "<path d=\"M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0\" /> <circle cx=\"12\" cy=\"10\" r=\"3\" />", "link": "<path d=\"M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71\" /> <path d=\"M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71\" />", "qr": "<rect width=\"5\" height=\"5\" x=\"3\" y=\"3\" rx=\"1\" /> <rect width=\"5\" height=\"5\" x=\"16\" y=\"3\" rx=\"1\" /> <rect width=\"5\" height=\"5\" x=\"3\" y=\"16\" rx=\"1\" /> <path d=\"M21 16h-3a2 2 0 0 0-2 2v3\" /> <path d=\"M21 21v.01\" /> <path d=\"M12 7v3a2 2 0 0 1-2 2H7\" /> <path d=\"M3 12h.01\" /> <path d=\"M12 3h.01\" /> <path d=\"M12 16v.01\" /> <path d=\"M16 12h1\" /> <path d=\"M21 12v.01\" /> <path d=\"M12 21v-1\" />", "favorites": "<path d=\"M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z\" />"};
  var CATALOG = [
    ['domain', [['project', 'Projet', 'grey'], ['loops', 'Boucles', 'blue'], ['wallet', 'Wallet', 'grey'], ['settings', 'Réglages', 'grey'], ['mcp', 'MCP', 'grey'], ['cast', 'Cast TV', 'grey']]],
    ['files', [['campaigns', 'Campagnes', 'green'], ['docs', 'Docs', 'green'], ['media', 'Médias', 'green'], ['app', 'App', 'green'], ['page', 'Page', 'yellow'], ['contract', 'Contrat', 'yellow']]],
    ['build', [['agent', 'Super agent', 'orange'], ['claude', 'Claude', 'grey'], ['listen', 'Écoute', 'grey'], ['plus', 'Ajouter', 'grey'], ['scenario', 'Scénario', 'grey']]],
    ['voir', [['visible', 'Caché', 'grey'], ['nearby', 'Proximité', 'violet'], ['link', 'Lien', 'grey'], ['qr', 'QR code', 'grey'], ['favorites', 'Favoris', 'grey']]]
  ];
  var BY = {}, GROUPS = {};
  CATALOG.forEach(function (g) { GROUPS[g[0]] = g[1].map(function (b) { return b[0]; }); g[1].forEach(function (b) { BY[b[0]] = { id: b[0], group: g[0], label: b[1], tone: b[2] }; }); });
  var STATE = {};
  var BASE = '.aiwa-btn{all:unset;box-sizing:border-box;display:inline-flex;align-items:center;gap:.55em;padding:.7em 1em;cursor:pointer;font:inherit;color:var(--aiwa-fg,#fff);' +
    'background:var(--aiwa-bg,rgba(255,255,255,.12));border:1px solid var(--aiwa-edge,rgba(255,255,255,.28));border-radius:var(--aiwa-radius,14px);-webkit-tap-highlight-color:transparent;transition:transform .15s,background .2s}' +
    '.aiwa-btn:active{transform:scale(.96)}.aiwa-btn svg{width:1.25em;height:1.25em;flex:none}.aiwa-btn[data-on="1"]{background:var(--aiwa-bg-on,rgba(120,255,190,.22))}' +
    '.aiwa-btn[data-tone="red"]{--aiwa-bg:rgba(255,90,90,.25)}.aiwa-btn[data-tone="green"][data-found="1"],.aiwa-btn[data-tone="green"]:not([data-found]){}' +
    '.aiwa-btn[data-found="0"]{opacity:.4}.aiwa-slot{display:flex;flex-wrap:wrap;gap:var(--aiwa-gap,8px)}.aiwa-btn[data-look="icons"] span{display:none}.aiwa-btn[data-look="labels"] svg{display:none}' +
    '.aiwa-dock{position:fixed;right:14px;bottom:14px;z-index:2147483000;font:15px system-ui,sans-serif;color:#fff}.aiwa-dock>button{all:unset;cursor:pointer;width:52px;height:52px;border-radius:50%;display:grid;place-items:center;background:#3a2fa8;box-shadow:0 6px 24px #0008;font-weight:700}' +
    '.aiwa-dock>div{display:none;position:absolute;right:0;bottom:62px;width:min(86vw,340px);max-height:70vh;overflow:auto;padding:12px;border-radius:18px;background:rgba(14,16,40,.92);backdrop-filter:blur(14px);flex-direction:column;gap:10px}.aiwa-dock[data-open="1"]>div{display:flex}';
  function svg(id) { return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + (ICONS[id] || '') + '</svg>'; }
  function press(id) {
    if (window.AiwaHomeHost && window.AiwaHomeHost.postMessage) { try { window.AiwaHomeHost.postMessage(JSON.stringify({ cmd: 'press', id: String(id) })); return; } catch (e) { } }
    document.dispatchEvent(new CustomEvent('aiwa-press', { detail: { id: id } }));
  }
  function ids(slot) {
    var spec = (slot.getAttribute('data-aiwa-slot') || '').split(/[\s,]+/).filter(Boolean), out = [];
    spec.forEach(function (g) { (g === 'all' ? Object.keys(BY) : (GROUPS[g] || [])).forEach(function (i) { if (out.indexOf(i) < 0) out.push(i); }); });
    var only = (slot.getAttribute('data-aiwa-only') || '').split(/[\s,]+/).filter(Boolean), skip = (slot.getAttribute('data-aiwa-skip') || '').split(/[\s,]+/).filter(Boolean);
    if (only.length) out = out.filter(function (i) { return only.indexOf(i) >= 0; });
    return out.filter(function (i) { return skip.indexOf(i) < 0; });
  }
  function make(id, look) {
    var def = BY[id], st = STATE[id] || {}, label = st.label || def.label;
    var b = document.createElement('button'); b.type = 'button'; b.className = 'aiwa-btn'; b.setAttribute('data-id', id); b.setAttribute('data-group', def.group);
    b.setAttribute('data-tone', st.tone || def.tone); if (st.on != null) b.setAttribute('data-on', st.on ? '1' : '0'); if (st.found != null) b.setAttribute('data-found', st.found ? '1' : '0');
    if (look) b.setAttribute('data-look', look); b.title = label; b.setAttribute('aria-label', label);
    b.innerHTML = svg(st.icon || id) + '<span>' + label.replace(/[<&]/g, '') + '</span>';
    b.addEventListener('click', function (ev) { ev.preventDefault(); press(id); });
    return b;
  }
  function fill(slot) {
    var look = slot.getAttribute('data-aiwa-look') || '';
    if (slot.hasAttribute('data-aiwa-button')) { var id = slot.getAttribute('data-aiwa-button'); if (!BY[id]) return; slot.textContent = ''; slot.appendChild(make(id, look)); return; }
    slot.classList.add('aiwa-slot'); slot.textContent = '';
    ids(slot).forEach(function (id) { slot.appendChild(make(id, look)); });
  }
  function slots() { return [].slice.call(document.querySelectorAll('[data-aiwa-slot],[data-aiwa-button]')).filter(function (n) { return n.tagName !== 'BUTTON' || n.hasAttribute('data-aiwa-slot'); }); }
  var dock = null;
  function render() {
    if (!document.getElementById('aiwa-home-base')) { var st = document.createElement('style'); st.id = 'aiwa-home-base'; st.textContent = BASE.replace(/(^|\})([^{}]+)\{/g, function (m, a, sel) { return a + sel.split(',').map(function (s) { return ':where(' + s.trim() + ')'; }).join(',') + '{'; }); (document.head || document.documentElement).appendChild(st); }
    var list = slots();
    list.forEach(fill);
    // a page with no slot at all still gives access to everything: a round button opens the whole catalogue (a page may say it does not want it: <meta name="aiwa-home" content="none">)
    var none = document.querySelector('meta[name="aiwa-home"][content="none"]');
    if (!list.length && !none) {
      if (!dock) { dock = document.createElement('div'); dock.className = 'aiwa-dock'; dock.innerHTML = '<button type="button" aria-label="Aiwa">A</button><div></div>'; dock.firstChild.addEventListener('click', function () { dock.setAttribute('data-open', dock.getAttribute('data-open') === '1' ? '0' : '1'); }); document.body.appendChild(dock); }
      var panel = dock.lastChild; panel.textContent = ''; CATALOG.forEach(function (g) { var row = document.createElement('div'); row.className = 'aiwa-slot'; g[1].forEach(function (b) { row.appendChild(make(b[0], '')); }); panel.appendChild(row); });
    } else if (dock) { dock.remove(); dock = null; }
  }
  function setState(next) { if (!next || typeof next !== 'object') return; if (JSON.stringify(next) !== JSON.stringify(STATE)) { STATE = next; render(); } }
  window.aiwa = { press: press, render: render, setState: setState, catalog: BY, groups: GROUPS };
  var queued = false;
  new MutationObserver(function (muts) {
    if (queued) return;
    // what the runtime itself draws inside a slot does not start another drawing
    if (muts.every(function (m) { return m.target.nodeType === 1 && m.target.closest && m.target.closest('[data-aiwa-slot],[data-aiwa-button],.aiwa-dock'); })) return;
    queued = true; setTimeout(function () { queued = false; render(); }, 60);
  }).observe(document.documentElement, { childList: true, subtree: true });
  render();
})();
