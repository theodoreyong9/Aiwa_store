// Frames + an optional audio file → MP4 (H.264 + AAC, faststart). No audio: a silent AAC track is still written, because some players and
// platforms refuse a file without one (the check reports a missing or silent track either way).
import { ffmpegPath, run } from './ffmpeg.js';

export function assemble({ framesDir, audio = null, out, fps = 30, crf = 23, audioBitrate = '128k', maxBytes = null }) {
  const input = ['-y', '-loglevel', 'error', '-framerate', String(fps), '-i', `${framesDir}/%05d.jpg`];
  const audioArgs = audio ? ['-i', audio] : ['-f', 'lavfi', '-i', 'anullsrc=r=44100:cl=stereo'];
  const args = [...input, ...audioArgs, '-map', '0:v', '-map', '1:a', '-c:v', 'libx264', '-preset', 'medium', '-crf', String(crf), '-pix_fmt', 'yuv420p',
    '-c:a', 'aac', '-b:a', audioBitrate, '-shortest', '-movflags', '+faststart', out];
  run(ffmpegPath(), args);
  return out;
}
