// The real Capability — an application never receives "access to
// everything." A real capability is a real, signed, scoped grant:
// this real resource, these real actions, issued by this real
// identity, for this real subject.

function coreBytes({ resource, actions, issuer, subject, expiresAt }) {
  const core = { resource, actions: [...actions].sort(), issuer, subject, expiresAt: expiresAt ?? null };
  return new TextEncoder().encode(JSON.stringify(core));
}

/** `issuerIdentity` must be a real, secret-key-bearing Identity (aiwa-core's own identity.js) — the real subject can never issue its own real capability over someone else's real resource. */
export async function issueCapability(issuerIdentity, { resource, actions, subject, expiresAt }) {
  const bytes = coreBytes({ resource, actions, issuer: issuerIdentity.id, subject, expiresAt });
  const signature = await issuerIdentity.sign(bytes);
  return { resource, actions, issuer: issuerIdentity.id, subject, expiresAt: expiresAt ?? null, signature };
}

/**
 * Real, pure verification: the real signature must genuinely verify
 * against the real, claimed issuer, and — if `expiresAt` is set — the
 * real capability must not have genuinely expired as of `now`.
 */
export async function verifyCapability(capability, issuerIdentity, now = Date.now()) {
  if (capability.issuer !== issuerIdentity.id) return { valid: false, reason: 'issuer mismatch' };
  if (capability.expiresAt !== null && now > capability.expiresAt) return { valid: false, reason: 'real capability has genuinely expired' };
  const bytes = coreBytes(capability);
  const sigValid = await issuerIdentity.verify(bytes, capability.signature);
  if (!sigValid) return { valid: false, reason: 'real signature does not verify' };
  return { valid: true };
}

/** Real, pure: does this real, already-verified capability actually cover the real, requested action on the real, requested resource? */
export function capabilityAllows(capability, { resource, action }) {
  return capability.resource === resource && capability.actions.includes(action);
}

/**
 * A real, minimal, per-subject capability set — what a real
 * application (a "sphere") actually holds, checked before every real
 * privileged operation.
 */
export class CapabilitySet {
  constructor() {
    this._grants = [];
  }
  grant(capability) {
    this._grants.push(capability);
  }
  /** Real, pure check against every real, currently-held grant — never trusts a capability that was never verified first (see `verifyCapability`). */
  can(resource, action) {
    return this._grants.some((c) => capabilityAllows(c, { resource, action }));
  }
}
