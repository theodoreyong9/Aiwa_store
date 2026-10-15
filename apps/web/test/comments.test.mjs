import { test } from 'node:test';
import assert from 'node:assert/strict';
import { rankedComments } from '../src/comments-rank.js';

test('comments are ranked by the author score per lap, and anything malformed is dropped', () => {
  const index = { comments: [
    { kind: 'comment', id: 'c-a', version: '1.0.0', author: 'A', path: 'comments/c-a-1.0.0.json', score: 10, laps: 5, updatedAt: 1 },
    { kind: 'comment', id: 'c-b', version: '1.0.0', author: 'B', path: 'comments/c-b-1.0.0.json', score: 90, laps: 3, updatedAt: 2 },
    { kind: 'comment', id: 'c-c', version: '1.0.0', author: 'C', path: '../evil.json', score: 999, laps: 1 },
    { kind: 'app', id: 'x', author: 'D', path: 'comments/x.json', score: 1, laps: 1 },
    { kind: 'comment', id: 'c-e', version: '1.0.0', author: 'E', path: 'comments/c-e-1.0.0.json', score: 'many', laps: 1 },
  ] };
  assert.deepEqual(rankedComments(index).map((e) => e.id), ['c-b', 'c-a']);
  assert.deepEqual(rankedComments({}), []);
  assert.deepEqual(rankedComments(null), []);
});
