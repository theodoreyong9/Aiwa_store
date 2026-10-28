// Checks the home runtime (android/app/src/main/assets/home/aiwa-home.js) in a real browser: slots are filled with the buttons of the application, a press reaches the native side,
// a page with no slot gets the round button, the state changes a button, and what the runtime draws does not start another drawing.   node scripts/test-home-runtime.mjs
import { chromium } from 'playwright';
import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
const js = readFileSync(new URL('../android/app/src/main/assets/home/aiwa-home.js', import.meta.url), 'utf8');
const browser = await chromium.launch();
const run = async (html, { state = {} } = {}) => {
  const page = await browser.newPage();
  const errors = []; page.on('pageerror', (e) => errors.push(String(e)));
  await page.setContent(html);
  await page.evaluate((st) => { window.__presses = []; window.AiwaHomeHost = { postMessage: (m) => window.__presses.push(JSON.parse(m).id) }; window.__state = st; }, state);
  await page.addScriptTag({ content: js });
  await page.evaluate(() => window.aiwa.setState(window.__state));
  await page.waitForTimeout(150);
  return { page, errors };
};
let n = 0; const ok = (name) => console.log('ok', ++n, name);

{ const { page, errors } = await run('<body><div id=a data-aiwa-slot="build"></div><div id=b data-aiwa-slot="voir" data-aiwa-look="icons"></div><i id=c data-aiwa-button="docs"></i><div id=d data-aiwa-slot="files" data-aiwa-only="docs,media"></div><div id=e data-aiwa-slot="domain" data-aiwa-skip="mcp,cast"></div></body>');
  assert.equal(await page.locator('#a .aiwa-btn').count(), 5); ok('a group gives its buttons');
  assert.equal(await page.locator('#c .aiwa-btn').count(), 1); ok('one button');
  assert.equal(await page.locator('#d .aiwa-btn').count(), 2); ok('data-aiwa-only keeps the named ones');
  assert.equal(await page.locator('#e .aiwa-btn').count(), 4); ok('data-aiwa-skip drops the named ones');
  assert.equal(await page.locator('#b .aiwa-btn[data-look=icons] span').first().isVisible(), false); ok('the look "icons" hides the words');
  assert.ok(await page.locator('#a .aiwa-btn svg').first().isVisible()); ok('an icon is drawn');
  await page.locator('#d .aiwa-btn[data-id=media]').click();
  assert.deepEqual(await page.evaluate(() => window.__presses), ['media']); ok('a press reaches the native side with the id of the button');
  assert.equal(await page.locator('.aiwa-dock').count(), 0); ok('no round button when the page has slots');
  assert.deepEqual(errors, []); await page.close(); }

{ const { page } = await run('<body><h1>Anna</h1></body>');
  assert.equal(await page.locator('.aiwa-dock').count(), 1); ok('a page with no slot gets the round button');
  await page.locator('.aiwa-dock > button').click();
  assert.ok((await page.locator('.aiwa-dock .aiwa-btn').count()) >= 20); ok('it opens the whole catalogue');
  await page.close(); }

{ const { page } = await run('<head><meta name="aiwa-home" content="none"></head><body><h1>Anna</h1></body>');
  assert.equal(await page.locator('.aiwa-dock').count(), 0); ok('a page can refuse the round button'); await page.close(); }

{ const { page } = await run('<body><div data-aiwa-slot="build voir"></div></body>', { state: { agent: { on: 1, tone: 'green', label: 'Super agent · à l\'écoute' }, visible: { label: 'Visible', tone: 'violet', on: 1 } } });
  assert.equal(await page.locator('.aiwa-btn[data-id=agent]').getAttribute('data-tone'), 'green'); ok('the state of the application changes a button');
  assert.match(await page.locator('.aiwa-btn[data-id=agent]').innerText(), /à l'écoute/); ok('and its words');
  await page.close(); }

{ const { page } = await run('<body><div data-aiwa-slot="all"></div><script>window.__draws=0;new MutationObserver(()=>window.__draws++).observe(document.body,{childList:true,subtree:true})</script></body>');
  await page.waitForTimeout(100); const a = await page.evaluate(() => window.__draws); await page.waitForTimeout(500); const b = await page.evaluate(() => window.__draws);
  assert.equal(a, b); ok('the drawing of the runtime does not start another one');
  await page.evaluate(() => { const d = document.createElement('div'); d.setAttribute('data-aiwa-slot', 'files'); document.body.appendChild(d); });
  await page.waitForTimeout(200);
  assert.equal(await page.locator('[data-aiwa-slot=files] .aiwa-btn').count(), 6); ok('a slot added later is filled');
  await page.close(); }

{ const { page } = await run('<style>.aiwa-btn{background:rgb(1,2,3)!important}</style><body><div data-aiwa-slot="build"></div></body>');
  assert.equal(await page.locator('.aiwa-btn').first().evaluate((e) => getComputedStyle(e).backgroundColor), 'rgb(1, 2, 3)'); ok('a rule of the page beats the base look');
  await page.close(); }
await browser.close(); console.log('all good:', n);
