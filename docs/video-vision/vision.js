// The second half of the global film: from Aiwa, the base, to the vision (docs/VISION-SOCIETE-AUGMENTEE.md). It adds its shots to the ones of the Aiwa film
// (video-aiwa/video.html, without its last shot), and uses the same colours and helpers. Everything drawn here is an ILLUSTRATION of the vision, marked as such
// on the picture: no function shown here has been demonstrated, and the figures are objectives to test, said so on screen.
const VCSS = `
.v{position:absolute;left:0;top:0;font-family:"Liberation Sans",Arial,sans-serif;color:#f0f0f5}
.v .tag{position:absolute;left:0;bottom:-6px;font-size:26px;letter-spacing:3px;color:#8c8ca0;text-transform:uppercase}
.v .pill{box-sizing:border-box;position:absolute;border-radius:60px;background:#1b1b26;border:2px solid #34344a;font-weight:700;display:flex;align-items:center;justify-content:center;white-space:nowrap}
.v .row{position:absolute;left:0;width:1000px;height:124px;border-radius:30px;background:#171722;border:2px solid #2d2d42;display:flex;align-items:center;gap:26px;padding:0 34px;box-sizing:border-box}
.v .row b{font-size:50px;letter-spacing:-.5px}.v .row span{font-size:32px;color:#a4a4bc}
.v .dot{width:56px;height:56px;border-radius:50%;flex:none;display:flex;align-items:center;justify-content:center;font-size:30px;font-weight:800;color:#090912}
`;
const person = (id, c, s = 1) => `<g id="${id}" transform="translate(0,0)"><circle cx="0" cy="-70" r="${34 * s}" fill="${c}"/><path d="M${-62 * s} 60 C${-62 * s} -10 ${-34 * s} -30 0 -30 C${34 * s} -30 ${62 * s} -10 ${62 * s} 60 Z" fill="${c}"/></g>`;

// ---- you look at someone: their profile appears where they stand --------------------------------------------------------------------
const LOOK = `<div class="v" style="width:1000px;height:640px"><div id="vs" style="position:absolute;left:-60px;top:0;width:1120px;height:600px">
<svg viewBox="0 0 1120 600" width="1120" height="600"><rect x="0" y="470" width="1120" height="130" fill="#0f0f18"/><line x1="0" y1="470" x2="1120" y2="470" stroke="#2a2a3c" stroke-width="2"/>
<g transform="translate(250,400)">${person('pa', '#4a4a6e', 1.15)}</g><g transform="translate(900,410)">${person('pc', '#44445f', 1.0)}</g>
<g id="pbw" transform="translate(560,400)"><g id="pbs">${person('pb', '#6a6a94', 1.3)}</g></g>
<path id="vl" d="" stroke="${TEA}" stroke-width="3" fill="none" stroke-dasharray="8 8" opacity="0"/>
<rect id="vring" x="-70" y="-190" width="140" height="270" rx="30" fill="none" stroke="${TEA}" stroke-width="3" opacity="0" transform="translate(560,400)"/></svg>
<div id="chip" style="position:absolute;width:420px;border-radius:26px;background:#14141ee0;border:2px solid ${TEA};padding:18px 22px;opacity:0;box-sizing:border-box;box-shadow:0 20px 60px rgba(0,0,0,.6)">
<div style="font-size:42px;font-weight:800">Léa</div><div style="font-size:29px;color:#a4a4bc;margin-top:4px">échecs · design · créations</div><div style="font-size:27px;color:${TEA};margin-top:10px;font-weight:700">visible : elle l'a choisi</div></div></div>
<div class="tag">Illustration de la vision</div></div>`;
function lookTick(root, t, M) {
  const $ = (id) => root.querySelector('#' + id);
  const walk = 560 + 70 * Math.sin(t * 0.8), head = -50 * Math.sin(t * 0.55 + 0.3);   // the person walks, the viewer's head turns
  $('vs').style.transform = `translateX(${head}px)`;
  $('pbw').setAttribute('transform', `translate(${walk},400)`);
  $('pbs').setAttribute('transform', `translate(0,${Math.sin(t * 5) * 3})`);
  const on = M.prog(t, 1.5, 0.6, M.ease.outBack), cx = walk - 270, cy = 20;
  $('vring').setAttribute('transform', `translate(${walk},400)`); $('vring').style.opacity = M.clamp(on);
  const chip = $('chip'); chip.style.opacity = M.clamp(on); chip.style.left = (cx + 60) + 'px'; chip.style.top = cy + 'px'; chip.style.transform = `scale(${0.85 + 0.15 * Math.min(1, on)})`; chip.style.transformOrigin = 'center bottom';
  const l = $('vl'); l.setAttribute('d', `M${walk} 215 L${walk} ${cy + 175}`); l.style.opacity = M.clamp(on) * 0.8;
}
// ---- consent: you choose who sees you ----------------------------------------------------------------------------------------------
const MODES = ['Invisible', 'Mes contacts', 'Participants autorisés', 'Découverte publique'];
const CONSENT = `<div class="v" style="width:1000px;height:560px">${MODES.map((m, i) => `<div class="pill" id="cm${i}" style="left:0;top:${i * 128}px;width:1000px;height:104px;font-size:48px;justify-content:flex-start;padding-left:40px;gap:24px"><i id="ck${i}" style="width:46px;height:46px;border-radius:50%;border:4px solid #4a4a60;display:inline-block"></i>${m}</div>`).join('')}<div class="tag" style="bottom:-40px">La proximité n'est pas une autorisation</div></div>`;
function consentTick(root, t, M) {
  const pick = t < 1.2 ? -1 : t < 2.0 ? 3 : t < 2.8 ? 2 : t < 3.5 ? 0 : 1;           // it hesitates, then settles on "contacts"
  MODES.forEach((_, i) => {
    const el = root.querySelector('#cm' + i), p = M.prog(t, 0.2 + i * 0.18, 0.5, M.ease.outBack), on = i === pick;
    el.style.opacity = M.clamp(p); el.style.transform = `translateX(${(1 - Math.min(1, p)) * 70}px)`; el.style.borderColor = on ? GRN : '#34344a'; el.style.background = on ? '#173a2c' : '#1b1b26';
    const k = root.querySelector('#ck' + i); k.style.borderColor = on ? GRN : '#4a4a60'; k.style.background = on ? GRN : 'transparent';
  });
}
// ---- a shared interest, a game, no virtual world -------------------------------------------------------------------------------
const SQ = Array.from({ length: 16 }, (_, i) => `<i style="display:block;background:${((i >> 2) + i) % 2 ? '#3b3b58' : '#222234'}"></i>`).join('');
const CHESS = `<div class="v" style="width:1000px;height:640px">
<svg viewBox="0 0 1000 640" width="1000" height="640"><g transform="translate(150,400)">${person('qa', '#5a5a82', 1.25)}</g><g transform="translate(850,400)">${person('qb', '#5a5a82', 1.25)}</g></svg>
<div id="it" class="pill" style="left:260px;top:20px;width:480px;height:86px;font-size:38px;color:${TEA};border-color:${TEA}">♟ Échecs en commun</div>
<div id="inv" class="pill" style="left:280px;top:130px;width:440px;height:86px;font-size:38px;background:${ORA};color:#090912;border-color:${ORA}">Proposer une partie</div>
<div id="acc" class="pill" style="left:280px;top:130px;width:440px;height:86px;font-size:38px;background:${GRN};color:#090912;border-color:${GRN};opacity:0">Acceptée ✓</div>
<div id="bd" style="position:absolute;left:350px;top:250px;width:300px;height:300px;display:grid;grid-template-columns:repeat(4,1fr);grid-template-rows:repeat(4,1fr);border-radius:18px;overflow:hidden;border:3px solid #4a4a60;opacity:0;box-shadow:0 30px 80px rgba(0,0,0,.6)">${SQ}</div>
<div id="pcs" style="position:absolute;left:350px;top:250px;width:300px;height:300px;font-size:64px;opacity:0;display:grid;grid-template-columns:repeat(4,1fr);grid-template-rows:repeat(4,1fr);text-align:center;line-height:75px;color:#f0f0f5">
<span>♜</span><span></span><span>♚</span><span></span><span></span><span>♟</span><span></span><span>♟</span><span>♙</span><span></span><span>♙</span><span></span><span></span><span>♔</span><span></span><span>♖</span></div>
<div class="tag">Illustration de la vision</div></div>`;
function playTick(root, t, M) {
  const $ = (id) => root.querySelector('#' + id);
  $('it').style.opacity = M.prog(t, 0.2, 0.5); $('inv').style.opacity = t < 2.6 ? M.prog(t, 0.9, 0.5) : 0; $('acc').style.opacity = M.prog(t, 2.6, 0.4, M.ease.outBack);
  $('inv').style.transform = `scale(${t > 2.0 && t < 2.6 ? 0.94 : 1})`;
  const b = M.prog(t, 3.2, 0.7, M.ease.outBack); $('bd').style.opacity = M.clamp(b); $('bd').style.transform = `scale(${0.7 + 0.3 * Math.min(1, b)})`; $('pcs').style.opacity = M.clamp(b); $('pcs').style.transform = `scale(${0.7 + 0.3 * Math.min(1, b)})`;
}
// ---- the five layers -----------------------------------------------------------------------------------------------------------------
const LAYERS = [['Dispositif optique', 'identifie et localise les lunettes', VIO], ['Lunettes', 'voient, suivent, affichent', TEA], ['Téléphone', 'relaie, calcule, protège', ORA], ['Réseau social', 'identités, autorisations, relations', GRN], ['SDK ouvert', 'des expériences créées par des tiers', '#f0f0f5']];
const STACK = `<div class="v" style="width:1000px;height:730px">${LAYERS.map(([n, s, c], i) => `<div class="row" id="ly${i}" style="top:${i * 148}px;border-left:12px solid ${c}"><div class="dot" style="background:${c}">${i + 1}</div><div><b>${n}</b><br><span>${s}</span></div></div>`).join('')}</div>`;
function stackTick(root, t, M) { LAYERS.forEach((_, i) => { const el = root.querySelector('#ly' + i), p = M.prog(t, 0.3 + i * 0.7, 0.6, M.ease.outBack); el.style.opacity = M.clamp(p); el.style.transform = `translateX(${(1 - Math.min(1, p)) * 110}px)`; }); }
// ---- the hard problem, and the targets to prove ---------------------------------------------------------------------------------
const TARGETS = [['< 5°', 'erreur de direction'], ['< 100 ms', 'mise à jour'], ['1 à 3 m', 'portée visée']];
const HARD = `<div class="v" style="width:1000px;height:560px">${TARGETS.map(([v, l], i) => `<div class="row" id="tg${i}" style="top:${i * 140}px;height:120px;justify-content:space-between"><b style="font-size:76px;color:${i === 0 ? TEA : '#f0f0f5'}">${v}</b><span>${l}</span></div>`).join('')}<div class="tag" id="tgn" style="bottom:20px;font-size:25px;letter-spacing:2px">objectifs à prouver — pas des résultats</div></div>`;
function hardTick(root, t, M) { TARGETS.forEach((_, i) => { const el = root.querySelector('#tg' + i), p = M.prog(t, 0.4 + i * 0.5, 0.55, M.ease.outBack); el.style.opacity = M.clamp(p); el.style.transform = `scale(${0.9 + 0.1 * Math.min(1, p)})`; }); root.querySelector('#tgn').style.opacity = M.prog(t, 2.2, 0.6); }
// ---- six phases, the first one is the proof -------------------------------------------------------------------------------------------
const PHASES = ['Prouver la proximité', 'Prototype social avec téléphone', 'Lunettes existantes', 'SDK expérimental', 'Module matériel optimisé', 'Écosystème pilote'];
const ROAD = `<div class="v" style="width:1000px;height:700px">${PHASES.map((p, i) => `<div class="row" id="ph${i}" style="top:${i * 115}px;height:96px"><div class="dot" style="background:#34344a;color:#f0f0f5" id="pd${i}">${i + 1}</div><b style="font-size:44px">${p}</b></div>`).join('')}</div>`;
function roadTick(root, t, M) {
  PHASES.forEach((_, i) => {
    const el = root.querySelector('#ph' + i), p = M.prog(t, 0.2 + i * 0.18, 0.5), hot = i === 0 && t > 1.6;
    el.style.opacity = M.clamp(p) * (i === 0 || t < 1.6 ? 1 : 0.55); el.style.transform = `translateX(${(1 - Math.min(1, p)) * 60}px)`; el.style.borderColor = hot ? TEA : '#2d2d42';
    root.querySelector('#pd' + i).style.background = hot ? TEA : '#34344a'; root.querySelector('#pd' + i).style.color = hot ? '#090912' : '#f0f0f5';
  });
}
// ---- the partners we look for ------------------------------------------------------------------------------------------------------------
const PARTNERS = [['Photonique et capteurs', VIO], ['Systèmes embarqués', TEA], ['Réalité augmentée et suivi spatial', ORA], ['Logiciel et réseau', GRN], ['Design produit', '#f0f0f5'], ['Terrains pilotes : campus, événements', MUT]];
const PEOPLE = `<div class="v" style="width:1000px;height:640px">${PARTNERS.map(([n, c], i) => `<div class="pill" id="pp${i}" style="left:${(i % 2) * 505}px;top:${(i >> 1) * 150}px;width:495px;height:124px;font-size:38px;border-color:${c};white-space:normal;text-align:center;padding:0 26px;line-height:1.15">${n}</div>`).join('')}</div>`;
function peopleTick(root, t, M) { PARTNERS.forEach((_, i) => { const el = root.querySelector('#pp' + i), p = M.prog(t, 0.5 + i * 0.28, 0.5, M.ease.outBack); el.style.opacity = M.clamp(p); el.style.transform = `scale(${0.85 + 0.15 * Math.min(1, p)})`; }); }

const BLOB1 = { type: 'blobs', colors: ['rgba(123,97,255,.40)', 'rgba(38,198,176,.25)', 'rgba(30,30,60,.5)'], base: '#090912' };
window.SHOTS.push(
  // the bridge
  { seconds: 6, transition: { type: 'fade', seconds: 0.7 }, bg: { type: 'blobs', colors: ['rgba(204,120,92,.40)', 'rgba(123,97,255,.40)', 'rgba(30,30,60,.5)'], base: '#090912' }, layers: [
    { type: 'text', text: 'Aiwa,\nc\'est le socle.', mode: 'lines', effect: 'mask', size: [13, 10.5], y: [42, 42], at: 0.3, stagger: 0.35, accent: { 'socle.': ORA } },
    { type: 'text', text: 'Voici ce qu\'on construit dessus.', size: [4.8, 3.8], weight: 600, color: MUT, y: [64, 64], effect: 'rise', mode: 'words', stagger: 0.08, at: 2.6 },
  ] },
  // look at someone
  { seconds: 7.5, transition: { type: 'zoom', seconds: 0.55 }, bg: { type: 'gradient', colors: ['#15152b', '#090912'], angle: 180, drift: true }, layers: [
    { type: 'text', text: 'Tu regardes quelqu\'un.', mode: 'lines', effect: 'mask', size: [8.4, 6.2], x: [50, 23], y: [13, 36], w: [90, 36], at: 0.3, out: { at: 5.6, effect: 'mask' } },
    { type: 'text', text: 'Son profil apparaît\nlà où il se tient.', mode: 'lines', effect: 'rise', size: [5.2, 4], weight: 700, color: TEA, x: [50, 23], y: [86, 62], w: [90, 36], at: 2.2, stagger: 0.3 },
    { type: 'html', id: 'look', css: VCSS, markup: LOOK, design: [1000, 640], w: [100, 56], x: [50, 71], y: [52, 52], at: 0.1, tick: lookTick },
  ] },
  // consent
  { seconds: 6.5, transition: { type: 'push', seconds: 0.5 }, bg: BLOB1, layers: [
    { type: 'text', text: 'Seulement si\nelle l\'a choisi.', mode: 'lines', effect: 'mask', size: [10, 8], x: [50, 23], y: [14, 40], w: [90, 36], at: 0.3, stagger: 0.28, accent: { 'choisi.': GRN } },
    { type: 'html', id: 'consent', css: VCSS, markup: CONSENT, design: [1000, 560], w: [86, 56], x: [50, 71], y: [60, 52], at: 0.1, tick: consentTick },
  ] },
  // an interest, a game
  { seconds: 8, transition: { type: 'wipe', seconds: 0.55 }, bg: { type: 'gradient', colors: ['#1c1736', '#090912'], angle: 170, drift: true }, layers: [
    { type: 'text', text: 'Un intérêt commun.\nUne partie.', mode: 'lines', effect: 'mask', size: [9, 6.2], x: [50, 23], y: [13, 38], w: [90, 36], at: 0.3, stagger: 0.28, accent: { 'partie.': ORA } },
    { type: 'html', id: 'play', css: VCSS, markup: CHESS, design: [1000, 640], w: [100, 56], x: [50, 71], y: [60, 52], at: 0.1, tick: playTick },
    { type: 'text', text: 'Sans entrer dans un monde virtuel.', size: [4, 3.2], weight: 600, color: MUT, x: [50, 23], y: [90, 64], w: [90, 36], effect: 'rise', mode: 'words', stagger: 0.07, at: 4.4 },
  ] },
  // the five layers
  { seconds: 8.5, transition: { type: 'push-up', seconds: 0.5 }, bg: { type: 'blobs', colors: ['rgba(38,198,176,.28)', 'rgba(123,97,255,.35)', 'rgba(204,120,92,.2)'], base: '#090912' }, layers: [
    { type: 'text', text: 'Une infrastructure.', mode: 'lines', effect: 'mask', size: [9.4, 7.4], x: [50, 23], y: [10, 36], w: [90, 36], at: 0.25 },
    { type: 'html', id: 'stack', css: VCSS, markup: STACK, design: [1000, 730], w: [90, 56], x: [50, 71], y: [54, 54], at: 0.1, tick: stackTick },
  ] },
  // the hard problem
  { seconds: 7, transition: { type: 'zoom', seconds: 0.5 }, bg: { type: 'gradient', colors: ['#171433', '#090912'], angle: 190, drift: true }, layers: [
    { type: 'text', text: 'Le vrai défi :\nrelier un signal\nà la bonne personne.', mode: 'lines', effect: 'mask', size: [8.4, 5.6], x: [50, 23], y: [16, 40], w: [90, 36], at: 0.25, stagger: 0.28, accent: { 'personne.': TEA } },
    { type: 'html', id: 'hard', css: VCSS, markup: HARD, design: [1000, 560], w: [86, 56], x: [50, 71], y: [70, 52], at: 0.1, tick: hardTick },
  ] },
  // the phases
  { seconds: 6.5, transition: { type: 'wipe', seconds: 0.5 }, bg: BLOB1, layers: [
    { type: 'text', text: 'Par preuves,\nune à une.', mode: 'lines', effect: 'mask', size: [10, 8], x: [50, 23], y: [12, 38], w: [90, 36], at: 0.3, stagger: 0.28 },
    { type: 'html', id: 'road', css: VCSS, markup: ROAD, design: [1000, 700], w: [90, 56], x: [50, 71], y: [58, 52], at: 0.1, tick: roadTick },
  ] },
  // partners
  { seconds: 7.5, transition: { type: 'push', seconds: 0.5 }, bg: { type: 'blobs', colors: ['rgba(204,120,92,.35)', 'rgba(38,198,176,.25)', 'rgba(123,97,255,.3)'], base: '#090912' }, layers: [
    { type: 'text', text: 'Nous cherchons\ndes partenaires.', mode: 'lines', effect: 'mask', size: [10.4, 8.2], x: [50, 23], y: [13, 40], w: [90, 36], at: 0.3, stagger: 0.3, accent: { 'partenaires.': ORA } },
    { type: 'html', id: 'people', css: VCSS, markup: PEOPLE, design: [1000, 640], w: [92, 56], x: [50, 71], y: [60, 52], at: 0.1, tick: peopleTick },
  ] },
  // the end
  { seconds: 9, transition: { type: 'fade', seconds: 0.8 }, bg: { type: 'blobs', colors: ['rgba(123,97,255,.45)', 'rgba(204,120,92,.3)', 'rgba(38,198,176,.25)'], base: '#090912' }, layers: [
    { type: 'text', text: 'Nous ne voulons pas\naugmenter le monde.', mode: 'lines', effect: 'mask', size: [7.4, 5.6], y: [30, 28], at: 0.3, stagger: 0.3, out: { at: 3.2, effect: 'mask' } },
    { type: 'text', text: 'Nous voulons augmenter\nles possibilités\nde la société humaine.', mode: 'lines', effect: 'mask', size: [7.4, 5.6], y: [40, 42], at: 3.5, stagger: 0.35, accent: { 'humaine.': GRN } },
    { type: 'svg', markup: '<svg viewBox="0 0 24 24" width="100%"><path class="d" d="M4 20 L12 4 L20 20 M7.5 14 H16.5"/></svg>', x: 50, y: [62, 64], w: [14, 6], at: 5.2, dur: 1.2, stroke: '#f0f0f5', width: 1.6 },
    { type: 'text', text: 'AIWA · SOCIÉTÉ AUGMENTÉE', size: [4.6, 3.4], mode: 'chars', effect: 'mask', stagger: 0.04, at: 5.8, y: [72, 76], spacing: 0.08, weight: 800 },
    { type: 'text', text: 'theodoreyong9.github.io/Aiwa_store', size: [3.2, 2.5], weight: 600, color: TEA, spacing: 0.03, y: [80, 84], effect: 'blur', mode: 'chars', stagger: 0.02, at: 6.6 },
    { type: 'text', text: 'Musique : The Complex, Kevin MacLeod (incompetech.com), CC BY 4.0', size: [2.1, 1.5], weight: 500, color: '#8c8ca0', y: [95, 95], effect: 'fade', at: 6.8 },
  ] },
);
