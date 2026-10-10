#!/usr/bin/env node
// video-kit render <page.html> <frames-dir> --seconds=25 [--fps=30 --width=1080 --height=1920 --workers=4]
// video-kit assemble <frames-dir> <out.mp4> [--audio=music.mp3 --voice=vo.wav --duck=strong|light --fps=30]   (--voice: the music ducks under it; light = music stays forward)
// video-kit capture <url-or-file> <out.png> [--width=1440 --height=810]   (the whole page as one tall picture, for the "page" layer)
// video-kit check <video.mp4> [--seconds=25 --width=1080 --height=1920]   (exit code 1 when a blocking error is found)
import { renderFrames } from './render.js';
import { assemble } from './assemble.js';
import { checkVideo } from './qc.js';
import { capturePage } from './capture.js';

const args = process.argv.slice(2);
const flags = Object.fromEntries(args.filter((a) => a.startsWith('--')).map((a) => { const [k, v] = a.slice(2).split('='); return [k, v ?? true]; }));
const [command, a, b] = args.filter((x) => !x.startsWith('--'));
const num = (v, d) => (v === undefined ? d : Number(v));
try {
  if (command === 'render') console.log(JSON.stringify(await renderFrames({ page: a, outDir: b, seconds: num(flags.seconds), fps: num(flags.fps, 30), width: num(flags.width, 1080), height: num(flags.height, 1920), workers: num(flags.workers, 4) })));
  else if (command === 'assemble') console.log(assemble({ framesDir: a, out: b, audio: flags.audio || null, voice: flags.voice || null, duck: flags.duck || 'strong', fps: num(flags.fps, 30), crf: num(flags.crf, 23) }));
  else if (command === 'capture') console.log(JSON.stringify(await capturePage({ source: a, out: b, width: num(flags.width, 1440), height: num(flags.height, 810) })));
  else if (command === 'check') { const r = checkVideo(a, { seconds: num(flags.seconds, null), width: num(flags.width, null), height: num(flags.height, null), fps: num(flags.fps, null) }); console.log(JSON.stringify(r, null, 2)); process.exit(r.ok ? 0 : 1); }
  else console.log('video-kit render | assemble | check (see the header of src/cli.js)');
} catch (err) { console.error(String(err.message ?? err)); process.exit(2); }
