#!/usr/bin/env node
// Processes one submission to the store: validates it and, if it is accepted, writes the registry's files.
//
//   node registry/bin/process.mjs --submission <file.json> [--store store] [--deployment deployment.json] [--rpc <url>]
//
// The submission is DATA: it is read, checked and (for a publication) copied into store/apps/ — never executed. The
// GitHub Action (.github/workflows/registry.yml) runs this from the main branch on the file a pull request adds under
// submissions/, then commits what changed under store/. Exit code 0: accepted and written; 1: refused (the reason is
// printed); 2: could not run.

import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { processSubmissionFile } from '../src/process.js';
import { loadDeployment } from '../src/deployment.js';

const args = Object.fromEntries(process.argv.slice(2).reduce((pairs, arg, i, all) => (arg.startsWith('--') ? [...pairs, [arg.slice(2), all[i + 1]]] : pairs), []));
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');

async function main() {
  if (!args.submission) { console.error('usage: process.mjs --submission <file.json> [--store store] [--deployment deployment.json] [--rpc url]'); return 2; }
  const deployment = loadDeployment(resolve(args.deployment ?? `${root}/deployment.json`));
  const { Connection } = await import('@solana/web3.js');
  const connection = new Connection(args.rpc ?? process.env.SOLANA_RPC ?? deployment.rpc, 'confirmed');
  const { code, message } = await processSubmissionFile({ file: resolve(args.submission), storeDir: resolve(args.store ?? `${root}/store`), deployment, connection });
  (code === 0 ? console.log : console.error)(message);
  return code;
}

main().then((code) => process.exit(code), (err) => { console.error(`ERROR: ${err.message}`); process.exit(2); });
