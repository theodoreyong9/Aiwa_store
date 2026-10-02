// The deployment's parameters, one file (deployment.json at the root of the repository) read by the registry, the web
// app and the Android app, so that the wallet that burns and the registry that validates apply the same rules.

import { readFileSync } from 'node:fs';

/** The parameters in `deployment` as aiwa-core takes them: the creator fee is part of them only once it has an address. */
export function rewardParamsOf(deployment) {
  const { rewardParams, creatorFee } = deployment;
  const withFee = creatorFee && typeof creatorFee.address === 'string' && creatorFee.address && creatorFee.rateOfT > 0;
  return withFee ? { ...rewardParams, creatorFee: { address: creatorFee.address, rateOfT: creatorFee.rateOfT } } : { ...rewardParams };
}

export function loadDeployment(file = new URL('../../deployment.json', import.meta.url)) {
  const deployment = JSON.parse(readFileSync(file, 'utf8'));
  return { ...deployment, rewardParams: rewardParamsOf(deployment) };
}
