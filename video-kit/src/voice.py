#!/usr/bin/env python3
"""A voice-over track from a list of cues, with a local text-to-speech engine. No account, no network, no licence question.

  voice.py cues.json out.wav SECONDS [--lang=fr] [--speed=150] [--voice=NAME]

  cues.json: [ { "at": 3.2, "text": "Salut, je suis Yo." }, … ]    each line is spoken from its `at` second; the track is SECONDS long
  Engines, in order: piper (PIPER_BIN and PIPER_MODEL set: a neural voice, much better), espeak-ng (`apt-get install -y espeak-ng`), flite.
  The engine used and the duration of each line are printed as JSON, so that a line which runs into the next one can be seen and shortened.
  Honest limit: espeak-ng is intelligible but robotic. With piper and a French voice it is far better; say which one was used.
"""
import json, os, shutil, subprocess, sys, tempfile, wave


def main():
    pos = [a for a in sys.argv[1:] if not a.startswith('--')]
    opt = dict(a[2:].partition('=')[::2] for a in sys.argv[1:] if a.startswith('--'))
    cues_file, out, seconds = pos[0], pos[1], float(pos[2])
    lang, speed, voice = opt.get('lang', 'fr'), opt.get('speed', '150'), opt.get('voice', '')
    ffmpeg = os.environ.get('FFMPEG') or 'ffmpeg'
    cues = json.load(open(cues_file))
    tmp = tempfile.mkdtemp(prefix='vo-')
    engine = None
    if os.environ.get('PIPER_BIN') and os.environ.get('PIPER_MODEL'):
        engine = 'piper'
    elif shutil.which('espeak-ng'):
        engine = 'espeak-ng'
    elif shutil.which('flite'):
        engine = 'flite'
    if not engine:
        print(json.dumps({'ok': False, 'error': 'no text-to-speech engine: apt-get install -y espeak-ng (or set PIPER_BIN and PIPER_MODEL)'}))
        sys.exit(3)
    parts, report = [], []
    for i, c in enumerate(cues):
        wav = os.path.join(tmp, f'{i:02d}.wav')
        text = str(c['text'])
        if engine == 'piper':
            subprocess.run([os.environ['PIPER_BIN'], '--model', os.environ['PIPER_MODEL'], '--output_file', wav], input=text, text=True, check=True, capture_output=True)
        elif engine == 'espeak-ng':
            subprocess.run(['espeak-ng', '-v', voice or lang, '-s', speed, '-p', '45', '-w', wav, text], check=True, capture_output=True)
        else:
            subprocess.run(['flite', '-t', text, '-o', wav], check=True, capture_output=True)
        with wave.open(wav) as w:
            dur = w.getnframes() / w.getframerate()
        report.append({'at': c['at'], 'seconds': round(dur, 2), 'ends': round(c['at'] + dur, 2), 'text': text})
        parts.append((c['at'], wav))
    # place each line at its second, on a track of the right length
    cmd = [ffmpeg, '-y', '-loglevel', 'error']
    for _, wav in parts:
        cmd += ['-i', wav]
    filt = ''.join(f'[{i}:a]aresample=44100,aformat=channel_layouts=mono,adelay={int(at * 1000)}:all=1[a{i}];' for i, (at, _) in enumerate(parts))
    filt += ''.join(f'[a{i}]' for i in range(len(parts))) + f'amix=inputs={len(parts)}:normalize=0,apad=whole_dur={seconds},atrim=0:{seconds},loudnorm=I=-16:TP=-1.5:LRA=7[v]'
    subprocess.run(cmd + ['-filter_complex', filt, '-map', '[v]', '-ar', '44100', '-ac', '1', out], check=True)
    overlaps = [(a['text'][:30], b['text'][:30]) for a, b in zip(report, report[1:]) if a['ends'] > b['at']]
    print(json.dumps({'ok': True, 'engine': engine, 'out': out, 'lines': report, 'overlaps': overlaps}, ensure_ascii=False))


main()
