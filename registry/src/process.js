// Processing one submission file: the thing bin/process.mjs does, as a function (so it can be tested without a network).

import { readFileSync, lstatSync } from 'node:fs';
import { validateSubmission, applyAccepted, POLICY } from './validate.js';
import { readStore, writeStore } from './store-files.js';

/**
 * @returns {Promise<{ code: 0|1, message: string }>} 0: accepted and written; 1: refused (message is the reason)
 */
export async function processSubmissionFile({ file, storeDir, deployment, connection, now }) {
  const info = lstatSync(file);
  if (!info.isFile()) return { code: 1, message: 'REFUSED: the submission is not a regular file' };     // a link to another file is not one
  const size = info.size;
  if (size > POLICY.maxSubmissionBytes) return { code: 1, message: `REFUSED: the submission is ${size} bytes, at most ${POLICY.maxSubmissionBytes}` };
  let submission;
  try { submission = JSON.parse(readFileSync(file, 'utf8')); } catch { return { code: 1, message: 'REFUSED: the submission is not JSON' }; }

  const store = readStore(storeDir);
  const result = await validateSubmission({ submission, store, deployment, connection, now });
  if (!result.ok) return { code: 1, message: `REFUSED: ${result.reason}` };
  const { accepted } = result;
  writeStore(storeDir, applyAccepted(store, accepted), accepted);
  return { code: 0, message: `ACCEPTED: ${accepted.kind} ${accepted.entry.id} ${accepted.entry.version} — score ${accepted.entry.score} laps ${accepted.entry.laps}` };
}
