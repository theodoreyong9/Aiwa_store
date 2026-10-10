// Frames + an optional audio file → MP4 (H.264 + AAC, faststart). No audio: a silent AAC track is still written, because some players and
// platforms refuse a file without one (the check reports a missing or silent track either way).
//   audio   the music (a generated bed or a real track: it is looped if shorter than the film, faded in and out, and cut at the end of the film)
//   voice   a voice-over: the music goes down while it speaks. duck 'strong' (default) lets the voice lead; 'light' keeps the music forward, for a film
//           of a few punchy lines over a loud track (the voice is a little louder than the music and still clear).
import { readdirSync } from 'node:fs';
import { ffmpegPath, run } from './ffmpeg.js';

export function assemble({ framesDir, audio = null, voice = null, out, fps = 30, crf = 23, audioBitrate = '128k', duck = 'strong', fade = 1.5 }) {
  const frames = readdirSync(framesDir).filter((f) => f.endsWith('.jpg')).length;
  const seconds = frames / fps;
  const input = ['-y', '-loglevel', 'error', '-framerate', String(fps), '-i', `${framesDir}/%05d.jpg`];
  const audioArgs = audio ? ['-stream_loop', '-1', '-i', audio] : ['-f', 'lavfi', '-i', 'anullsrc=r=44100:cl=stereo'];
  const music = `[1:a]afade=t=in:d=0.3,afade=t=out:st=${Math.max(0, seconds - fade).toFixed(2)}:d=${fade}`;
  const mix = duck === 'light'
    ? 'sidechaincompress=threshold=0.05:ratio=3.5:attack=10:release=300:makeup=1[d];[d][v1]amix=inputs=2:normalize=0:weights=1 1.15,alimiter=limit=0.89:level=disabled[m]'
    : 'sidechaincompress=threshold=0.02:ratio=9:attack=15:release=450:makeup=1[d];[d][v1]amix=inputs=2:normalize=0:weights=0.8 1.25,alimiter=limit=0.89:level=disabled[m]';
  let filter = [];
  if (audio && voice) filter = ['-i', voice, '-filter_complex', `${music}[mus];[2:a]asplit[v1][v2];[mus][v2]${mix}`];
  else if (audio) filter = ['-filter_complex', `${music},alimiter=limit=0.89:level=disabled[m]`];
  const args = [...input, ...audioArgs, ...filter, '-map', '0:v', '-map', audio ? '[m]' : '1:a', '-c:v', 'libx264', '-preset', 'medium', '-crf', String(crf), '-pix_fmt', 'yuv420p',
    '-c:a', 'aac', '-b:a', audioBitrate, '-t', seconds.toFixed(3), '-movflags', '+faststart', out];
  run(ffmpegPath(), args);
  return out;
}
