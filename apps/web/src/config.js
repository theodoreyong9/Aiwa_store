// The deployment's parameters: deployment.json at the root of the repository, the same file the registry reads
// (the build resolves '@deployment' to it).
import deployment from '@deployment';
import { rewardParamsOf } from 'aiwa-registry/reward-params';

export const config = { ...deployment, rewardParams: rewardParamsOf(deployment) };
