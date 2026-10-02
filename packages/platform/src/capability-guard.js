// The one real check both guarded stores in this package share: does
// `capabilities` (a real, already-verified CapabilitySet — see this
// package's own capability.js) actually cover this action on this
// resource? A real, honest refusal on anything else, never a silent
// partial write.

export function assertCapability(capabilities, resource, action) {
  if (!capabilities.can(resource, action)) {
    throw new Error(`Write refused: no capability for action '${action}' on resource '${resource}'`);
  }
}
