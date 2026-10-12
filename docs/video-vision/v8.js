// The global film, v8: the film recomposed from the notes of the person who watches it (docs/video-vision/README.md): a first line that says what is needed, the CV match as an
// EXAMPLE, a shorter « Dis-le. C'est fait. », then the story of the functions in their order (an agent that is powerful; the video and its three qualities; a campaign from a file,
// semi-automatic: the list, the render, the execution; the validation one by one), a store that looks like a store, the production of AIWA with its disinflationary curve, agents that
// make their own communities, five guarantees, an architecture centred on the agent (in French), the vision with the glasses on the right, a peer-to-peer application instead of the chess game.
// Everything here is an illustration of the interface or of the idea; the names of targets and of apps are invented, and the film says so where it shows them.
(function () {
  if (window.SHOTS.length < 20) return;                        // the ?screen= modes keep their own list
  const S = window.SHOTS.slice();
  const text = (shot, start) => shot.layers.find((l) => l.type === 'text' && String(l.text).startsWith(start));
  const GAP = 'font-family:"Liberation Sans",Arial,sans-serif';
  const ease = (M) => M.ease.outBack;
  const pop = (el, p, dx = 0, dy = 40) => { el.style.opacity = Math.max(0, Math.min(1, p)); el.style.transform = `translate(${(1 - Math.min(1, p)) * dx}px,${(1 - Math.min(1, p)) * dy}px)`; };

  // ---- the first line: what is needed (Claude Code is said once) ----
  text(S[0], 'Un widget Android').text = 'Un widget Android.\nIl faut Android et Claude Code.';
  S[2].seconds = 5.8;                                           // « Dis-le. C'est fait. »: shorter (the chips go faster, see widgetTick)
  S[3].layers.push({ type: 'text', text: 'Un exemple de ce que crée Aiwa', size: [3.2, 2.5], weight: 700, color: TEA, x: [50, 24], y: [5, 26], w: [90, 40], effect: 'fade', at: 0.5 });
  S[3].layers.find((l) => l.type === 'text' && String(l.text).startsWith('Un match CV')).y = [13, 44];

  // ---- the video and its three qualities ----
  const QCARDS = [['Simple', 'quelques minutes', 'pages animées, rythme et voix de synthèse', TEA], ['Moyen', 'le standard', 'vraie musique, voix, un tour de critique', VIO], ['Extra · cinéma', 'plusieurs heures', 'plans 3D, catalogues, critique poussée', ORA]];
  const VIDQ = `<div class="v" style="width:1000px;height:640px">${QCARDS.map(([n, tm, sub, c], i) => `<div class="row" id="vq${i}" style="top:${i * 200}px;height:176px;gap:28px;padding:0 36px"><div style="width:14px;height:110px;border-radius:7px;background:${c};flex:none"></div><div style="flex:1"><b style="font-size:54px">${n}</b><span style="display:block;font-size:30px;margin-top:6px">${sub}</span></div><div class="pill" id="vt${i}" style="position:relative;height:70px;padding:0 26px;font-size:30px;border-color:${c}">${tm}</div></div>`).join('')}
<div id="vq-note" style="position:absolute;left:0;top:604px;font-size:28px;color:#a4a4bc;opacity:0">choisie au moment où tu lances la création</div></div>`;
  const vidqTick = (root, t, M) => {
    QCARDS.forEach((_, i) => { const el = root.querySelector('#vq' + i), p = M.prog(t, 0.3 + i * 0.4, 0.5, ease(M)); pop(el, p, 70, 0); const sel = t < 2.6 ? -1 : t < 3.4 ? 0 : t < 4.2 ? 1 : 2; el.style.borderColor = i === sel ? QCARDS[i][3] : '#2d2d42'; el.style.background = i === sel ? '#1f1f2e' : '#171722'; el.style.transform += ` scale(${i === sel ? 1.03 : 1})`; });
    root.querySelector('#vq-note').style.opacity = M.prog(t, 4.4, 0.4);
  };
  const SH_VIDQ = { seconds: 6.4, transition: { type: 'push', seconds: 0.55 }, bg: { type: 'blobs', colors: ['rgba(204,120,92,.30)', 'rgba(123,97,255,.35)', 'rgba(30,30,60,.5)'], base: '#090912' }, layers: [
    { type: 'text', text: 'Une vidéo.\nTu choisis\nla qualité.', mode: 'lines', effect: 'mask', size: [10, 7.6], x: [50, 22], y: [15, 36], w: [90, 34], at: 0.25, stagger: 0.28, accent: { 'qualité.': TEA } },
    { type: 'html', id: 'vidq', css: VCSS, markup: VIDQ, design: [1000, 640], w: [92, 56], x: [50, 70], y: [56, 52], at: 0.15, tick: vidqTick },
    { type: 'text', text: 'Plus la qualité monte, plus c\'est long.', size: [3.8, 3], weight: 600, color: '#c9c9d8', x: [50, 22], y: [88, 66], w: [90, 34], effect: 'rise', mode: 'words', stagger: 0.06, at: 3.6 },
  ] };

  // ---- a campaign from a file ----
  const FILEC = `<div class="v" style="width:1000px;height:640px">
<div class="pill" id="fc0" style="left:0;top:0;width:470px;height:100px;font-size:40px;justify-content:flex-start;padding-left:34px;gap:18px"><span style="color:${ORA}">▤</span>argumentaire.md</div>
<svg width="1000" height="130" viewBox="0 0 1000 130" style="position:absolute;left:0;top:0;overflow:visible"><path id="fc-a" d="M490 50 H570" stroke="${GRN}" stroke-width="6" fill="none" stroke-linecap="round" stroke-dasharray="90" stroke-dashoffset="90"/><path id="fc-h" d="M552 28 L580 50 L552 72" stroke="${GRN}" stroke-width="6" fill="none" stroke-linecap="round" stroke-linejoin="round" opacity="0"/></svg>
<div class="pill" id="fc1" style="left:600px;top:0;width:400px;height:100px;font-size:36px">une liste de cibles</div>
<div id="fc-q" style="position:absolute;left:0;top:170px;width:1000px;height:170px;border-radius:30px;background:#171722;border:2px solid #2d2d42;padding:24px 34px;box-sizing:border-box;opacity:0"><div style="font-size:26px;letter-spacing:2px;color:#8c8ca0">À QUI ?</div><div id="fc-t" style="font-size:38px;margin-top:10px;line-height:1.25"></div></div>
<div id="fc-g" style="position:absolute;left:0;top:380px;font-size:34px;color:#a4a4bc;opacity:0"><span id="fc-b" style="display:inline-block;width:38px;height:38px;border-radius:9px;border:3px solid #4a4a60;vertical-align:-8px;margin-right:16px;text-align:center;line-height:34px;font-size:30px;color:#090912"></span>Accessoirement : mes réalisations GitHub (publiques)</div>
<div id="fc-n" style="position:absolute;left:0;top:470px;font-size:28px;color:#7c7c92;opacity:0">décoché : le fichier seul compte</div></div>`;
  const PROMPT = '« des cafés de Lausanne dont le site est dépassé »';
  const filecTick = (root, t, M) => {
    const $ = (id) => root.querySelector('#' + id);
    pop($('fc0'), M.prog(t, 0.2, 0.45, ease(M)), -60, 0);
    const a = M.prog(t, 0.9, 0.5); $('fc-a').style.strokeDashoffset = String(90 * (1 - a)); $('fc-h').style.opacity = a > 0.9 ? 1 : 0;
    pop($('fc1'), M.prog(t, 1.3, 0.45, ease(M)), 60, 0);
    $('fc-q').style.opacity = M.prog(t, 1.9, 0.4);
    $('fc-t').textContent = PROMPT.slice(0, Math.floor(M.clamp((t - 2.1) / 1.8) * PROMPT.length));
    $('fc-g').style.opacity = M.prog(t, 4.0, 0.4); const on = t > 4.6; $('fc-b').style.background = on ? GRN : 'transparent'; $('fc-b').style.borderColor = on ? GRN : '#4a4a60'; $('fc-b').textContent = on ? '✓' : '';
    $('fc-n').style.opacity = on ? 0 : M.prog(t, 4.3, 0.4);
  };
  const SH_FILEC = { seconds: 6.6, transition: { type: 'wipe', seconds: 0.55 }, bg: { type: 'gradient', colors: ['#14102a', '#090912'], angle: 175, drift: true }, layers: [
    { type: 'text', text: 'Un fichier.\nUne campagne.', mode: 'lines', effect: 'mask', size: [10.6, 8.2], x: [50, 22], y: [15, 36], w: [90, 34], at: 0.25, stagger: 0.28, accent: { 'campagne.': GRN } },
    { type: 'html', id: 'filec', css: VCSS, markup: FILEC, design: [1000, 640], w: [92, 56], x: [50, 70], y: [56, 52], at: 0.15, tick: filecTick },
    { type: 'text', text: 'CV, argumentaire, brochure :\nle type du document est reconnu.', mode: 'lines', effect: 'rise', size: [3.8, 3.1], weight: 600, color: '#c9c9d8', x: [50, 22], y: [88, 66], w: [90, 34], at: 2.0, stagger: 0.3 },
  ] };
  const STEPS = [['TOI', 'La liste', 'L\'agent la propose : tu la valides.', ORA], ['AGENT', 'Le rendu', 'Maquette, vidéo et prix, pour chaque cible.', TEA], ['TOI', 'L\'exécution', 'Tu valides chaque proposition et tu l\'envoies.', ORA]];
  const STEPSM = `<div class="v" style="width:1000px;height:640px">${STEPS.map(([who, n, sub, c], i) => `<div class="row" id="st${i}" style="top:${i * 210}px;height:186px;gap:26px;padding:0 34px"><div class="dot" style="width:70px;height:70px;background:${c};font-size:36px">${i + 1}</div><div style="flex:1"><b style="font-size:50px">${n}</b><span style="display:block;font-size:30px;margin-top:6px">${sub}</span></div><div class="pill" style="position:relative;height:60px;padding:0 24px;font-size:26px;letter-spacing:2px;border-color:${c};color:${c}">${who}</div></div>`).join('')}</div>`;
  const stepsTick = (root, t, M) => { STEPS.forEach((_, i) => { const el = root.querySelector('#st' + i), p = M.prog(t, 0.3 + i * 1.5, 0.55, ease(M)); pop(el, p, 80, 0); const on = t >= 0.3 + i * 1.5 + 0.2 && (i === 2 || t < 0.3 + (i + 1) * 1.5); el.style.borderColor = on ? STEPS[i][3] : '#2d2d42'; }); };
  const SH_STEPS = { seconds: 6.8, transition: { type: 'wipe', seconds: 0.55 }, bg: { type: 'blobs', colors: ['rgba(38,198,176,.30)', 'rgba(123,97,255,.35)', 'rgba(30,30,60,.5)'], base: '#090912' }, layers: [
    { type: 'text', text: 'Semi-\nautomatique.', mode: 'lines', effect: 'mask', size: [11, 8.6], x: [50, 22], y: [15, 36], w: [90, 34], at: 0.25, stagger: 0.28, accent: { 'automatique.': TEA } },
    { type: 'html', id: 'steps', css: VCSS, markup: STEPSM, design: [1000, 640], w: [92, 56], x: [50, 70], y: [56, 52], at: 0.15, tick: stepsTick },
    { type: 'text', text: 'L\'agent travaille. Tu gardes la main.', size: [3.8, 3.1], weight: 700, color: GRN, x: [50, 22], y: [88, 66], w: [90, 34], effect: 'blur', mode: 'words', stagger: 0.06, at: 4.6 },
  ] };

  // ---- a store that looks like a store ----
  const APPS = [['Chronos', 'agenda', VIO], ['Pixelle', 'retouche photo', TEA], ['Carnet', 'notes', ORA], ['Lumen', 'lampe', GRN]];
  const STOREM = `<div class="v" style="width:1000px;height:700px"><div style="position:absolute;left:0;top:0;width:1000px;height:90px;border-radius:30px;background:#171722;border:2px solid #2d2d42;display:flex;align-items:center;padding:0 34px;gap:18px;font-size:34px;color:#8c8ca0;box-sizing:border-box">⌕&nbsp; Chercher une app</div>
${APPS.map(([n, d, c], i) => `<div class="row" id="sa${i}" style="top:${116 + i * 134}px;height:114px;gap:24px;padding:0 28px"><div style="width:76px;height:76px;border-radius:22px;background:${c};flex:none"></div><div style="flex:1"><b style="font-size:42px">${n}</b><span style="display:block;font-size:26px;margin-top:2px">${d} · par une personne, sans comité</span></div><div class="pill" style="position:relative;height:58px;padding:0 22px;font-size:28px;border-color:${i === 0 ? GRN : '#34344a'};color:${i === 0 ? GRN : '#f0f0f5'}">n° ${i + 1}</div></div>`).join('')}
<div id="sa-n" style="position:absolute;left:0;top:660px;font-size:26px;color:#8c8ca0;opacity:0">classé par la production d'AIWA de l'auteur · noms inventés · illustration</div></div>`;
  const storeTick = (root, t, M) => { APPS.forEach((_, i) => pop(root.querySelector('#sa' + i), M.prog(t, 0.3 + i * 0.3, 0.5, ease(M)), 70, 0)); root.querySelector('#sa-n').style.opacity = M.prog(t, 2.6, 0.5); };
  const SH_STORE = Object.assign({}, S[9], { seconds: 6.4, layers: [
    { type: 'text', text: 'Un store\nsans permission.', mode: 'lines', effect: 'mask', size: [10.6, 8.2], x: [50, 22], y: [14, 36], w: [90, 34], at: 0.25, stagger: 0.28, accent: { 'permission.': TEA } },
    { type: 'html', id: 'store', css: VCSS, markup: STOREM, design: [1000, 700], w: [92, 56], x: [50, 70], y: [56, 52], at: 0.15, tick: storeTick },
    { type: 'text', text: 'Aucun comité ne valide.', size: [4, 3.2], weight: 700, color: GRN, x: [50, 22], y: [88, 66], w: [90, 34], effect: 'blur', mode: 'words', stagger: 0.06, at: 3.4 },
  ] });

  // ---- the production of AIWA, with its curve (the real numbers of docs/EXPLAINED.md section 3.4, patience 20 %) ----
  // the claimable AIWA after 1 h, 1 day, 7 days, 30 days, 1 year (one burn of 1 SOL, patience 20 %, one epoch every 30 s): 0.13, 1.84, 8.19, 26.9, 231. What it produces EACH DAY falls:
  // 1.84, then (8.19-1.84)/6 = 1.06, then (26.9-8.19)/23 = 0.81, then (231-26.9)/335 = 0.61 : the issuance is disinflationary. The chart shows that rate; the counter shows the total.
  const TICKS = ['jour 1', 'jours 1 à 7', 'jours 7 à 30', 'jours 30 à 365'], RATE = [1.84, 1.06, 0.81, 0.61], TOTAL = [1.84, 8.19, 26.9, 231];
  const CX = (k) => 130 + k * 250, CY = (v) => 330 - (v / 2.1) * 270;
  const CURVE = RATE.map((v, k) => (k ? 'L' : 'M') + CX(k) + ' ' + CY(v).toFixed(1)).join(' ');
  const PRODSTEP = (i, icon, name, sub, badge) => `<div class="cd" id="pc${i}" style="left:0;top:${i * 156}px;width:1000px;height:140px;gap:24px;padding:18px 30px"><div class="ib" style="width:76px;height:76px;border-radius:24px;background:#262636">${ic(icon, 40)}</div><div style="flex:1"><b style="font-size:40px">${name}</b><span style="font-size:26px;margin-top:2px">${sub}</span></div>${badge}</div>`;
  const BADGE = (txt, col) => `<span style="background:${col};color:#fff;border-radius:30px;padding:8px 20px;font-size:28px;font-weight:700;display:inline-block;margin:0">${txt}</span>`;
  const PROD2 = PRODSTEP(0, LOCK, 'Engagement d\'origine', 'Tu t\'engages au départ. Sans retour.', '') + PRODSTEP(1, HOUR, 'Taux de patience', 'Moins tu prends tout de suite, plus tu reçois à la longue.', BADGE('−20 %', VIO)) + PRODSTEP(2, BOLT, 'Production immédiate', 'Le reste produit dès l\'engagement.', BADGE('80 %', '#2e7d5a')) + PRODSTEP(3, INF, 'Production perpétuelle', 'Sans date de fin, tant que l\'appli tourne.', '')
    + `<div id="pcv" style="position:absolute;left:0;top:640px;width:1000px;height:480px;opacity:0;${GAP};color:#f0f0f5"><div style="position:absolute;left:60px;top:0;font-size:28px;color:#a4a4bc;letter-spacing:2px">AIWA PRODUITS PAR JOUR</div><svg viewBox="0 0 1000 420" width="1000" height="420" style="position:absolute;left:0;top:34px"><line x1="60" y1="330" x2="940" y2="330" stroke="#3a3a4c" stroke-width="3"/><path id="pcp" d="${CURVE}" fill="none" stroke="${GRN}" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/>${RATE.map((v, k) => `<circle cx="${CX(k)}" cy="${CY(v).toFixed(1)}" r="9" fill="#090912" stroke="${GRN}" stroke-width="4"/><text x="${CX(k)}" y="${(CY(v) - 24).toFixed(1)}" text-anchor="middle" font-size="30" font-weight="700" fill="#f0f0f5">${String(v).replace('.', ',')}</text><text x="${CX(k)}" y="368" text-anchor="middle" font-size="22" fill="#a4a4bc">${TICKS[k]}</text>`).join('')}<circle id="pcd" r="13" fill="${GRN}" stroke="#fff" stroke-width="4"/></svg>
      <div id="pcn" style="position:absolute;right:60px;top:0;font-size:50px;font-weight:800;color:${GRN}">0 AIWA</div>
      <div id="pcr" style="position:absolute;left:0;top:440px;width:1000px;text-align:center;font-size:30px;color:#c9c9d8;opacity:0">chaque jour produit moins que le précédent : <b style="color:${GRN}">désinflationniste</b></div></div>`;
  const prod2Tick = (root, t, M) => {
    for (let i = 0; i < 4; i++) pop(root.querySelector('#pc' + i), M.prog(t, 0.3 + i * 0.4, 0.55, ease(M)), 80, 0);
    const show = M.prog(t, 2.3, 0.5), end = M.prog(t, 3.2, 3.4, M.ease.inOutCubic);
    root.querySelector('#pcv').style.opacity = show;
    const path = root.querySelector('#pcp'), len = path.getTotalLength(); path.style.strokeDasharray = String(len); path.style.strokeDashoffset = String(len * (1 - end));
    const pt = path.getPointAtLength(len * end); root.querySelector('#pcd').setAttribute('cx', pt.x); root.querySelector('#pcd').setAttribute('cy', pt.y);
    const k = Math.min(2.999, end * 3), k0 = Math.floor(k), f = k - k0, v = end <= 0 ? 0 : TOTAL[k0] + (TOTAL[k0 + 1] - TOTAL[k0]) * f;
    root.querySelector('#pcn').textContent = (v < 10 ? v.toFixed(2).replace('.', ',') : Math.round(v)) + ' AIWA';
    root.querySelector('#pcr').style.opacity = M.prog(t, 6.4, 0.5);
  };
  Object.assign(S[10].layers.find((l) => l.id === 'prod'), { markup: PROD2, tick: prod2Tick, design: [1000, 1130] });

  // ---- agents that make their own communities ----
  const GROUPS = [[VIO, 230, 205, 135, [[170, 150], [260, 130], [320, 200], [230, 230], [160, 260], [280, 280]], 'sa communauté'], [TEA, 715, 200, 135, [[640, 120], [730, 150], [790, 220], [690, 230], [630, 280]], 'ses règles'], [ORA, 545, 470, 150, [[430, 420], [520, 450], [590, 400], [470, 520], [560, 540], [670, 480]], 'sa propre voie']];
  const AGM = `<svg viewBox="0 0 1000 600" width="1000" height="600" style="overflow:visible">${GROUPS.map(([c, cx, cy, r, pts, lab], g) => `<circle class="gc" id="gc${g}" cx="${cx}" cy="${cy}" r="${r}" fill="${c}" fill-opacity=".06" stroke="${c}" stroke-width="3" stroke-dasharray="10 10" style="opacity:0"/>${pts.map(([x, y], i) => `<line class="gl g${g}" x1="${x}" y1="${y}" x2="${pts[(i + 1) % pts.length][0]}" y2="${pts[(i + 1) % pts.length][1]}" stroke="${c}" stroke-width="3" style="opacity:0"/>`).join('')}${pts.map(([x, y]) => `<circle class="ag g${g}" cx="${x}" cy="${y}" r="20" fill="#fff" stroke="${c}" stroke-width="9" style="opacity:0"/>`).join('')}<text id="gt${g}" x="${cx}" y="${cy + r + 38}" text-anchor="middle" font-size="30" font-weight="700" fill="${c}" font-family="Liberation Sans,Arial" style="opacity:0">${lab}</text>`).join('')}</svg>`;
  const agentsTick = (root, t, M) => {
    GROUPS.forEach(([c, cx, cy, r, pts], g) => {
      const t0 = 0.2 + g * 1.5;
      root.querySelectorAll('.ag.g' + g).forEach((d, i) => { const p = M.prog(t, t0 + i * 0.09, 0.35, ease(M)); d.style.opacity = M.clamp(p); d.style.transformOrigin = `${d.getAttribute('cx')}px ${d.getAttribute('cy')}px`; d.style.transform = `scale(${0.4 + 0.6 * Math.min(1, p)})`; });
      root.querySelectorAll('.gl.g' + g).forEach((l, i) => { l.style.opacity = M.prog(t, t0 + 0.7 + i * 0.07, 0.3) * 0.7; });
      root.querySelector('#gc' + g).style.opacity = M.prog(t, t0 + 1.0, 0.5); root.querySelector('#gt' + g).style.opacity = M.prog(t, t0 + 1.2, 0.4);
    });
  };
  const SH_AG = S[15]; SH_AG.seconds = 7.2;
  Object.assign(SH_AG.layers.find((l) => l.id === 'agents'), { markup: AGM, tick: agentsTick });
  text(SH_AG, 'Une clé par agent').text = 'Une clé par agent.\nChacun fonde sa propre communauté,\navec ses propres règles.';

  // ---- five guarantees ----
  const CHK5 = (i, title, sub) => `<div id="ck${i}" style="position:absolute;left:0;top:${i * 218}px;width:1000px;height:196px;display:flex;align-items:center;gap:30px;${GAP};color:#f0f0f5"><div style="width:84px;height:84px;border-radius:50%;background:${GRN};flex:none;display:flex;align-items:center;justify-content:center"><svg viewBox="0 0 24 24" width="52" height="52" fill="none" stroke="#0c3d28" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l5 5 9-10"/></svg></div><div><div style="font-size:46px;font-weight:800;letter-spacing:-1px;line-height:1.05">${title}</div><div style="font-size:28px;color:#c0c0d2;margin-top:8px;line-height:1.25">${sub}</div></div></div>`;
  const CHECKS5 = CHK5(0, 'Une valeur ne se compte jamais deux fois.', 'Même gagnant pour tous les lecteurs. La seconde dépense est refusée, preuve à l\'appui.')
    + CHK5(1, 'Sybil amorti.', 'Chaque identité doit brûler pour produire de la valeur : les faux profils ont un coût.')
    + CHK5(2, '51 % : pas de chaîne à renverser.', 'Pas de quorum. Le protocole ne suppose aucune majorité honnête.')
    + CHK5(3, 'Des règles en JavaScript.', 'Un contrat est du code que chacun rejoue : même règle, même résultat.')
    + CHK5(4, 'Un contrat figé.', 'Publié, il ne change plus en douce : GitHub n\'en garde qu\'un pointeur immuable.');
  const checks5Tick = (root, t, M) => { for (let i = 0; i < 5; i++) { const el = root.querySelector('#ck' + i), p = M.prog(t, 0.3 + i * 1.5, 0.6, ease(M)), q = M.prog(t, 1.4 + i * 1.5, 0.4); pop(el, p, 0, 40); el.style.opacity = M.clamp(p) * (i < 4 ? 1 - q * 0.6 : 1); } };
  S[16].seconds = 9.4;
  Object.assign(S[16].layers.find((l) => l.id === 'checks'), { markup: CHECKS5, tick: checks5Tick, design: [1000, 1090], w: [90, 52] });

  // ---- an architecture centred on the agent (in French) ----
  const ARCH = [['Son identité', 'une clé, qui lui appartient.', VIO], ['Son histoire', 'des événements qu\'il signe lui-même.', TEA], ['Ses règles', 'du code que chacun rejoue.', ORA]];
  const ARCHM = `<div class="v" style="width:1000px;height:560px">${ARCH.map(([n, sub, c], i) => `<div class="row" id="ar${i}" style="top:${i * 180}px;height:156px;gap:26px;padding:0 34px"><div style="width:14px;height:96px;border-radius:7px;background:${c};flex:none"></div><div><b style="font-size:48px">${n}</b><span style="display:block;font-size:32px;margin-top:6px">${sub}</span></div></div>`).join('')}</div>`;
  const archTick = (root, t, M) => ARCH.forEach((_, i) => pop(root.querySelector('#ar' + i), M.prog(t, 0.9 + i * 0.7, 0.55, ease(M)), 80, 0));
  const SH_ARCH = { seconds: 6.4, transition: { type: 'fade', seconds: 0.7 }, bg: S[17].bg, layers: [
    { type: 'text', text: 'Une architecture\ncentrée sur l\'agent.', mode: 'lines', effect: 'mask', size: [8.6, 6.8], x: [50, 22], y: [18, 38], w: [90, 36], at: 0.3, stagger: 0.4, accent: { 'l\'agent.': ORA } },
    { type: 'html', id: 'arch', css: VCSS, markup: ARCHM, design: [1000, 560], w: [90, 54], x: [50, 70], y: [56, 52], at: 0.2, tick: archTick },
    { type: 'text', text: 'Le réseau, c\'est ce que chacun vérifie.', size: [3.8, 3], weight: 700, color: GRN, x: [50, 22], y: [88, 66], w: [90, 36], effect: 'blur', mode: 'words', stagger: 0.06, at: 3.6 },
  ] };
  text(S[19], 'Reality').text = 'La réalité\nest le réseau.';

  // ---- a peer-to-peer application instead of a chess game ----
  const playShot = S[23];
  text(playShot, 'Un intérêt commun').text = 'Une application\nen pair à pair.';
  text(playShot, 'Sans entrer').text = 'Créée et déployée avec notre système.\nUn jeu d\'échecs, par exemple.';
  playShot.layers.find((l) => l.id === 'play').markup = playShot.layers.find((l) => l.id === 'play').markup.replace('♟ Échecs en commun', '♟ Échecs · pair à pair');

  // ---- the credit is in the picture, under everything, very small: it is added by the assembly (a clip), not by the shots ----
  S[27].layers = S[27].layers.filter((l) => !(l.type === 'text' && String(l.text).startsWith('Musique')));

  window.SHOTS = [S[0], S[1], S[2], S[3], S[4], S[8], SH_VIDQ, SH_FILEC, SH_STEPS, S[7], SH_STORE, S[10], S[11], S[12], S[13], S[14], SH_AG, S[16], SH_ARCH, S[19], S[20], S[21], playShot, S[26], S[27]];
})();
