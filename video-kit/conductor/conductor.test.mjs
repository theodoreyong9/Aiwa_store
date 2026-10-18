import test from 'node:test';
import assert from 'node:assert/strict';
import { validate, plan } from './conductor.mjs';
import { gradeOf, cameraOf, transitionSeconds } from './mood.mjs';

const film = () => ({ title: 't', total: 20, worlds: { a: { kind: 'plate', plate: 'pa' }, b: { kind: 'plate', plate: 'pb' } }, beats: [
  { id: 'one', page: 'p1', t: 0, world: 'a', idea: 'a thing appears', mood: { energy: 0.3 }, transition: { kind: 'iris', through: 'center' } },
  { id: 'two', page: 'p2', t: 10, world: 'b', idea: 'then it moves', mood: { energy: 0.5 } },
] });

test('a sound film has no error', () => { const r = validate(film(), { pages: ['p1', 'p2'], plates: ['pa', 'pb'] }); assert.deepEqual(r.errors, []); });
test('a missing idea, a missing plate and a missing through are errors', () => {
  const f = film(); f.beats[0].idea = ''; f.beats[0].transition = { kind: 'iris' }; f.worlds.a.plate = 'nope';
  const r = validate(f, { pages: ['p1', 'p2'], plates: ['pa', 'pb'] });
  assert.equal(r.errors.length, 3);
});
test('a change of place with nothing to hand over is an error', () => { const f = film(); delete f.beats[0].transition; assert.ok(validate(f).errors.some((x) => /nothing hands over/.test(x))); });
test('an energy jump is a warning unless it is a whip', () => {
  const f = film(); f.beats[1].mood.energy = 1; assert.ok(validate(f).warnings.some((x) => /energy jumps/.test(x)));
  f.beats[0].transition = { kind: 'whip' }; assert.ok(!validate(f).warnings.some((x) => /energy jumps/.test(x)));
});
test('the film must land', () => { const f = film(); f.beats[1].mood.energy = 0.9; assert.ok(validate(f).warnings.some((x) => /does not land/.test(x))); });
test('the mood drives the numbers: calm and warm differ from frantic and cool', () => {
  assert.ok(cameraOf({ energy: 1 }).swing > cameraOf({ energy: 0 }).swing);
  assert.ok(gradeOf({ warmth: 1 }).hue < gradeOf({ warmth: 0 }).hue);
  assert.ok(transitionSeconds({ energy: 1 }, { energy: 1 }) < transitionSeconds({ energy: 0 }, { energy: 0 }));
});
test('the plan hands over at the start of the next beat and carries the sound cues', () => {
  const p = plan(film());
  assert.equal(p.beats[0].transition.t1, 10);
  assert.ok(p.beats[0].transition.t0 < 10);
  assert.ok(p.sound.cues.some((c) => c.kind === 'whoosh') && p.sound.cues.some((c) => c.kind === 'arrive'));
});

import { pick, distance, places, moodOfPanorama } from './library.mjs';
test('the library finds a transition and a bed by mood', () => {
  assert.equal(pick('transitions', { energy: 0.95, tension: 0.7 }).id, 'whip');
  assert.equal(pick('transitions', { energy: 0.2, warmth: 0.6, awe: 0.6 }).id, 'dissolve');
  assert.equal(pick('sound_beds', { energy: 0.1, scale: 0.9 }).id, 'void');
  assert.ok(pick('transitions', { energy: 0.95, tension: 0.7 }, { not: ['whip'] }).id !== 'whip');
});
test('a panorama has a mood from its tags, and the places are ranked by mood', () => {
  assert.ok(moodOfPanorama({ name: 'x', tags: ['night', 'city'] }).warmth < moodOfPanorama({ name: 'y', tags: ['sunset', 'desert'] }).warmth);
  assert.ok(distance({ energy: 0.1 }, { energy: 0.1 }) < distance({ energy: 0.9 }, { energy: 0.1 }));
  const p = places({ words: 'night', mood: { warmth: 0.1, scale: 0.4 }, kinds: ['panorama'], limit: 3 });
  assert.equal(p.length, 3); assert.ok(p.every((x) => x.kind === 'panorama'));
});
