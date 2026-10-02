// The deployment's parameters as aiwa-core takes them. Pure, so the web app can use it as the registry does.

/** `deployment.rewardParams`, with the creator fee in it once the deployment has given it an address. */
export function rewardParamsOf(deployment) {
  const { rewardParams, creatorFee } = deployment;
  const withFee = creatorFee && typeof creatorFee.address === 'string' && creatorFee.address && creatorFee.rateOfT > 0;
  return withFee ? { ...rewardParams, creatorFee: { address: creatorFee.address, rateOfT: creatorFee.rateOfT } } : { ...rewardParams };
}
