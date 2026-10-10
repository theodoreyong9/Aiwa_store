# Director's playbook: what makes a short film for a product worth watching

What a Claude Code session reads at stage 1 (brief) and stage 6 (critique) of `docs/PROMO-VIDEO.md`. These are **rules of the craft**, written from general knowledge of how short advertising films and product films are made. They are **not measured on a corpus of awarded films** (no film is stored or analysed here without a licence to do so); `video-kit/src/analyze.py` measures any film you are allowed to read, and `video-research/catalog/campaigns.jsonl` lists campaigns with their idea and credits, to replace a guess with a measurement or an example wherever one exists.

## 1. The hook (the first 2 seconds)
- The first image or line is a **promise, a question or a surprise**, never a logo, a title card or a fade from black that waits.
- One idea per hook. If it needs a second sentence to be understood, it is not a hook.
- Vertical and social formats lose half the audience at 3 s: the payoff of the hook arrives before then.

## 2. The five beats
| Beat | Job | In a 30 s film | In a 60 s film |
|---|---|---|---|
| Hook | stop the scroll | 0 to 3 s | 0 to 5 s |
| Tension | what is wrong, missing, absurd | 3 to 9 s | 5 to 18 s |
| Reveal | the creation, in use, doing the thing | 9 to 20 s | 18 to 40 s |
| Proof | one real detail that makes it believable | 20 to 26 s | 40 to 52 s |
| Call to action | one action, one address | 26 to 30 s | 52 to 60 s |

Write the logline first: *"For [who], [creation] [does what], unlike [what they do now]."* If the film does not say it, the film is not finished.

## 3. Rhythm
- **Shot length** follows the energy: 1 to 2.5 s for social and product films, 3 to 5 s for a brand film that breathes. A film in which every shot has the same length is monotonous: vary it.
- **Accelerate toward the reveal, hold on the proof, breathe before the call to action.** The pace curve is part of the script.
- **Cut on a movement or on a beat of the music**, never in the middle of nothing. A transition has a reason (a push for "next", a wipe for "instead", a zoom for "inside").
- Every shot has **a movement** (camera, text, the real page scrolling, an element that enters). A still picture is a slide: never more than 1 s without a change.
- Little material (a page nearly empty)? Make material out of what the creation does: zoom on a detail, animate it, turn it into a graphic. Never stretch emptiness.

## 4. Text on screen
- 7 words at most per card. Reading speed: about 3 words per second, plus 0.5 s: a card of 6 words stays 2.5 s at least.
- One size for the message, one for the support; strong contrast; keep out of the zones platforms cover (top 10 % and bottom 17 % in vertical).
- Words that carry the idea are alone on their line and animated by word or by line, not all at once.

## 5. Sound
- Music is **a tempo** (90 to 128 bpm), not a pad: kick or pulse on the beat, an **accent on every cut**, a riser into the reveal, **a silence just before the line that matters**.
- Voice-over: short sentences, one idea each, started **after** the picture has shown it (the eye leads). Music goes down about 9 dB under the voice (ducking).
- Level for social: about -14 LUFS, true peak under -1 dB. No clipping.
- A synthetic voice and generated music are placeholders: say so, and replace them when better ones exist.

## 6. Colour and type
- One palette: a dark or light ground (60 %), one support colour (30 %), one accent (10 %) taken from the creation itself.
- One type family, two weights. Large type is a graphic element: let it fill the frame.
- One motion language for the whole film: the same easing (ease-out), the same durations (0.25 to 0.7 s for entrances), the same stagger (60 to 120 ms between words).
- A camera move on a still stays under about 8 % of scale or 3 % of the frame in position over a shot: slow is expensive-looking, fast is cheap-looking.

## 7. Honesty
Nothing invented: no figure, testimonial, result or "before / after" that is not on the page, in the brief or in the creation. Say the assumptions.

## 8. The critique, in one sheet
Score 1 to 5, as a demanding creative director who has not seen the code:
1. **Hook**: would I stop for this in 2 s? 2. **Message**: can I say it back in one sentence? 3. **Pace**: do shot lengths vary, does it accelerate and breathe? 4. **Movement**: does every shot move? 5. **Legibility**: can every card be read in its time? 6. **Art direction**: one palette, one type, one motion language? 7. **Sound and sync**: accents on the cuts, silence before the line, voice not covered? 8. **Honesty**: is every statement true? 9. **Call to action**: one action, clear, long enough?

Anything under 4 is fixed, then the preview is redone, at most three rounds. Report the scores as they are.
