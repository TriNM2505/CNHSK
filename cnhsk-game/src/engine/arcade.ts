export interface FallTiming {
  fallStartMs: number
  fallStepMs: number
  fallMinMs: number
}

/** Spec 008 FR-031. The server supplies the timing and the first item has index 0. */
export function fallDurationMs(index: number, timing: FallTiming): number {
  return Math.max(timing.fallMinMs, timing.fallStartMs - index * timing.fallStepMs)
}
