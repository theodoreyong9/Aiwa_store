// The deployment's parameters, one file (deployment.json at the root of the repository) read by the registry, the web
// app and the Android app, so that the wallet that burns and the registry that validates apply the same rules.

import { readFileSync } from 'node:fs';
import { rewardParamsOf } from './reward-params.js';

export { rewardParamsOf };

export function loadDeployment(file = new URL('../../deployment.json', import.meta.url)) {
  const deployment = JSON.parse(readFileSync(file, 'utf8'));
  return { ...deployment, rewardParams: rewardParamsOf(deployment) };
}
