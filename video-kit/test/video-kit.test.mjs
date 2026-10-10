import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, readFileSync, readdirSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { renderFrames } from '../src/render.js';
import { assemble } from '../src/assemble.js';
import { checkVideo } from '../src/qc.js';
import { hasFfmpeg, ffmpegPath, run } from '../src/ffmpeg.js';

const here = dirname(fileURLToPath(import.meta.url));
const template = join(here, '..', 'template', 'timeline.html');
const ready = hasFfmpeg();
const need = { skip: ready ? false : 'ffmpeg is not installed here: the kit needs it (set FFMPEG, or install it)' };

test('a timeline page renders frame by frame, in every format, and assembles to a valid MP4 that passes the check', need, async () => {
  for (const [name, width, height] of [['vertical', 270, 480], ['horizontal', 480, 270], ['square', 360, 360], ['four-five', 360, 450]]) {
    const dir = mkdtempSync(join(tmpdir(), `vk-${name}-`));
    const frames = await renderFrames({ page: template, outDir: join(dir, 'f'), width, height, fps: 10, seconds: 10, workers: 2 });
    assert.equal(frames.frames, 100);
    assert.equal(readdirSync(join(dir, 'f')).filter((f) => f.endsWith('.jpg')).length, 100);
    const bed = join(dir, 'bed.wav');
    const py = spawnSync('python3', [join(here, '..', 'src', 'audio-bed.py'), bed, '10', '0', '3', '7'], { encoding: 'utf8' });
    assert.equal(py.status, 0, py.stderr);
    const out = join(dir, 'out.mp4');
    assemble({ framesDir: join(dir, 'f'), audio: bed, out, fps: 10 });
    const report = checkVideo(out, { seconds: 10, width, height, fps: 10 });
    assert.deepEqual(report.findings.filter((f) => f.level === 'blocking'), [], `${name}: ${JSON.stringify(report.findings)}`);
    assert.equal(report.ok, true);
  }
});

test('the check finds what it is there to find: a silent file, a black stretch, a wrong size, no audio, an unreadable file', need, () => {
  const dir = mkdtempSync(join(tmpdir(), 'vk-qc-'));
  const make = (name, args) => { const out = join(dir, name); run(ffmpegPath(), ['-y', '-loglevel', 'error', ...args, '-c:v', 'libx264', '-pix_fmt', 'yuv420p', ...(args.includes('-an') ? [] : ['-c:a', 'aac']), '-shortest', out]); return out; };
  const color = (c) => ['-f', 'lavfi', '-i', `color=c=${c}:s=320x240:r=10:d=6`];
  const silent = make('silent.mp4', [...color('white'), '-f', 'lavfi', '-i', 'anullsrc=r=44100:cl=stereo']);
  assert.ok(checkVideo(silent).findings.some((f) => f.check === 'audio-silent' && f.level === 'blocking'));
  const black = make('black.mp4', [...color('black'), '-f', 'lavfi', '-i', 'sine=f=440:d=6']);
  assert.ok(checkVideo(black).findings.some((f) => f.check === 'black-screen' && f.level === 'blocking'));
  const noAudio = make('noaudio.mp4', [...color('blue'), '-an']);
  assert.ok(checkVideo(noAudio).findings.some((f) => f.check === 'audio-track' && f.level === 'blocking'));
  const fine = make('fine.mp4', [...color('blue'), '-f', 'lavfi', '-i', 'sine=f=440:d=6']);
  assert.ok(checkVideo(fine, { seconds: 6, width: 320, height: 240 }).ok);
  assert.ok(checkVideo(fine, { width: 1080, height: 1920 }).findings.some((f) => f.check === 'resolution' && f.level === 'blocking'));
  assert.ok(checkVideo(fine, { seconds: 30 }).findings.some((f) => f.check === 'duration'));
  const junk = join(dir, 'junk.mp4'); writeFileSync(junk, 'not a video');
  assert.equal(checkVideo(junk).ok, false);
});

test('a page that raises an error stops the render and says so', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'vk-bad-'));
  const page = join(dir, 'bad.html');
  writeFileSync(page, '<html><body><script>window.render = (t) => { throw new Error("boom " + t); };</script></body></html>');
  await assert.rejects(renderFrames({ page, outDir: join(dir, 'f'), width: 100, height: 100, fps: 2, seconds: 1, workers: 1 }), /boom/);
  assert.ok(existsSync(dir));
});

test('the cinematic template moves in every shot: it renders, gets a pulse with accents on its cuts, and is not taken for a slideshow', need, async () => {
  const tpl = join(here, '..', 'template');
  const dir = mkdtempSync(join(tmpdir(), 'vk-cine-'));
  for (const f of ['cinematic.html', 'cinematic.js', 'motion.js']) writeFileSync(join(dir, f), readFileSync(join(tpl, f)));
  const frames = join(dir, 'f');
  await renderFrames({ page: join(dir, 'cinematic.html'), outDir: frames, width: 480, height: 270, fps: 10, seconds: 11.25, workers: 2 });
  const meta = JSON.parse(readFileSync(join(frames, 'cuts.json'), 'utf8'));
  assert.equal(meta.cuts.length, 2, 'the page says where its shots change');
  assert.ok(Math.abs(meta.total - 11.25) < 0.01);
  const bed = join(dir, 'bed.wav');
  const py = spawnSync('python3', [join(here, '..', 'src', 'beat.py'), bed, '11.25', '--bpm=108', `--cuts-file=${join(frames, 'cuts.json')}`], { encoding: 'utf8' });
  assert.equal(py.status, 0, py.stderr);
  const out = join(dir, 'out.mp4');
  assemble({ framesDir: frames, audio: bed, out, fps: 10 });
  const report = checkVideo(out, { seconds: 11.25, width: 480, height: 270, fps: 10 });
  assert.equal(report.ok, true, JSON.stringify(report.findings));
  assert.ok(!report.findings.some((f) => f.check === 'slideshow'), `a moving cut is not a slideshow: ${JSON.stringify(report.findings)}`);
});

test('the check names a slideshow: a picture that stays still is reported, and it is a warning, never a blocking error', need, () => {
  const dir = mkdtempSync(join(tmpdir(), 'vk-still-'));
  const out = join(dir, 'still.mp4');
  run(ffmpegPath(), ['-y', '-loglevel', 'error', '-f', 'lavfi', '-i', 'color=c=0x223355:s=320x240:r=15:d=8', '-f', 'lavfi', '-i', 'sine=f=440:d=8', '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-shortest', out]);
  const found = checkVideo(out).findings.find((f) => f.check === 'slideshow');
  assert.ok(found, 'a still picture is a slideshow');
  assert.equal(found.level, 'warning');
});

test('the voice-over tool reports its engine and lines, or says plainly that no engine is there', need, () => {
  const dir = mkdtempSync(join(tmpdir(), 'vk-voice-'));
  writeFileSync(join(dir, 'cues.json'), JSON.stringify([{ at: 0.5, text: 'Salut.' }, { at: 2, text: 'Au revoir.' }]));
  const r = spawnSync('python3', [join(here, '..', 'src', 'voice.py'), join(dir, 'cues.json'), join(dir, 'vo.wav'), '5'], { encoding: 'utf8', env: { ...process.env, PIPER_BIN: '', PIPER_MODEL: '' } });
  const said = JSON.parse(r.stdout);
  if (r.status === 3) { assert.equal(said.ok, false); assert.match(said.error, /espeak-ng/); return; }
  assert.equal(r.status, 0, r.stderr);
  assert.equal(said.ok, true);
  assert.equal(said.lines.length, 2);
  assert.ok(existsSync(join(dir, 'vo.wav')));
});
