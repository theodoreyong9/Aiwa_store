// Renders a timeline page frame by frame. The page defines window.render(t) (t in seconds, a pure function of t: the same t draws the
// same picture) and a <meta name="duration"> is not needed: the caller says how long. Frames are JPEG files 00000.jpg, 00001.jpg, …
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export async function renderFrames({ page: pageFile, outDir, width = 1080, height = 1920, fps = 30, seconds, quality = 90, workers = 4, from = 0, to, executablePath = process.env.CHROMIUM_PATH || undefined }) {
  if (!(seconds > 0)) throw new Error('seconds must be positive');
  mkdirSync(outDir, { recursive: true });
  const total = Math.round(seconds * fps);
  const end = to ?? total;
  const browser = await chromium.launch({ executablePath });
  try {
    const url = pathToFileURL(resolve(pageFile)).href;
    const chunk = Math.ceil((end - from) / workers);
    await Promise.all(Array.from({ length: workers }, async (_, w) => {
      const a = from + w * chunk, b = Math.min(end, a + chunk);
      if (a >= b) return;
      const page = await browser.newPage({ viewport: { width, height } });
      const errors = [];
      page.on('pageerror', (e) => errors.push(e.message));
      await page.goto(url);
      await page.waitForFunction(() => typeof window.render === 'function', null, { timeout: 15000 });
      await page.waitForTimeout(500);
      for (let i = a; i < b; i++) {
        await page.evaluate((t) => window.render(t), i / fps);
        await page.screenshot({ path: `${outDir}/${String(i).padStart(5, '0')}.jpg`, type: 'jpeg', quality });
      }
      await page.close();
      if (errors.length) throw new Error(`the page raised: ${errors[0]}`);
    }));
  } finally { await browser.close(); }
  return { frames: end - from, fps, width, height };
}
