// Frames + an optional audio file → MP4 (H.264 + AAC, faststart). No audio: a silent AAC track is still written, because some players and
// platforms refuse a file without one (the check reports a missing or silent track either way).
import { ffmpegPath, run } from './ffmpeg.js';

export function assemble({ framesDir, audio = null, voice = null, out, fps = 30, crf = 23, audioBitrate = '128k', maxBytes = null }) {
  const input = ['-y', '-loglevel', 'error', '-framerate', String(fps), '-i', `${framesDir}/%05d.jpg`];
  const audioArgs = audio ? ['-i', audio] : ['-f', 'lavfi', '-i', 'anullsrc=r=44100:cl=stereo'];
  // A voice-over: the music goes down while it speaks (sidechain compression), then the two are mixed.
  const withVoice = audio && voice;
  const voiceArgs = withVoice ? ['-i', voice, '-filter_complex', '[2:a]asplit[v1][v2];[1:a][v2]sidechaincompress=threshold=0.02:ratio=9:attack=15:release=450:makeup=1[d];[d][v1]amix=inputs=2:normalize=0:weights=0.8 1.25,alimiter=limit=0.89:level=disabled[m]'] : [];
  const args = [...input, ...audioArgs, ...voiceArgs, '-map', '0:v', '-map', withVoice ? '[m]' : '1:a', '-c:v', 'libx264', '-preset', 'medium', '-crf', String(crf), '-pix_fmt', 'yuv420p',
    '-c:a', 'aac', '-b:a', audioBitrate, '-shortest', '-movflags', '+faststart', out];
  run(ffmpegPath(), args);
  return out;
}
