/**
 * Shared arcade rules for Thần Bút and Phi Đao.
 *
 * Fall timing follows spec 008 FR-004:
 *   fallDuration(i) = max(fallMinMs, fallStartMs - i * fallStepMs)
 *
 * Scoring is integer only (constitution: no FLOAT for points).
 * Pure module — unit tested.
 */

export type ArcadeMode = 'stage' | 'time'

export const ARCADE_RULES = {
  countdownMs: 3000,
  fallStartMs: 6000,
  fallStepMs: 150,
  fallMinMs: 3000,
  /** Tracing takes longer than typing, so stage mode stretches the fall. */
  stageFallScale: 1.4,
  basePoints: 10,
  speedBonusMax: 5,
  comboCap: 9,
  timeAttackSeconds: 60,
  maxStage: 5,
  stageLives: 3,
} as const

export function fallDurationMs(index: number, mode: ArcadeMode = 'time'): number {
  const raw = Math.max(
    ARCADE_RULES.fallMinMs,
    ARCADE_RULES.fallStartMs - index * ARCADE_RULES.fallStepMs,
  )
  return Math.round(raw * (mode === 'stage' ? ARCADE_RULES.stageFallScale : 1))
}

/** Points for one successful strike. `speedRatio` is 1 when struck instantly. */
export function strikePoints(combo: number, speedRatio: number): number {
  const safeCombo = Math.max(0, Math.min(ARCADE_RULES.comboCap, combo))
  const ratio = speedRatio < 0 ? 0 : speedRatio > 1 ? 1 : speedRatio
  const comboBonus = Math.floor((ARCADE_RULES.basePoints * safeCombo) / 10)
  const speedBonus = Math.floor(ratio * ARCADE_RULES.speedBonusMax)
  return ARCADE_RULES.basePoints + comboBonus + speedBonus
}

export interface StageConfig {
  level: number
  /** Number of characters to strike to clear the stage. */
  quota: number
  lives: number
  /** Strokes the player must trace per character (Thần Bút). */
  requiredStrokes: number
  seconds: number | null
}

export function stageConfig(level: number): StageConfig {
  const lv = Math.max(1, Math.min(ARCADE_RULES.maxStage, level))
  return {
    level: lv,
    quota: 6 + (lv - 1) * 2,
    lives: ARCADE_RULES.stageLives,
    requiredStrokes: Math.min(4, 1 + Math.floor((lv - 1) / 2)),
    seconds: null,
  }
}

export function timeAttackConfig(): StageConfig {
  return { level: 0, quota: Number.POSITIVE_INFINITY, lives: 0, requiredStrokes: 1, seconds: ARCADE_RULES.timeAttackSeconds }
}

/** Deterministic RNG so a round can be reproduced in tests. */
export function mulberry32(seed: number): () => number {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/** Draw `count` distinct entries from a pool without repetition. */
export function drawQueue<T>(pool: readonly T[], count: number, rng: () => number = Math.random): T[] {
  if (pool.length === 0) return []
  const source = [...pool]
  for (let i = source.length - 1; i > 0; i -= 1) {
    const j = Math.floor(rng() * (i + 1))
    const tmp = source[i]
    source[i] = source[j]
    source[j] = tmp
  }
  if (count >= source.length) {
    const out: T[] = []
    while (out.length < count) out.push(...source.slice(0, Math.min(source.length, count - out.length)))
    return out
  }
  return source.slice(0, count)
}

export interface AccuracyStats {
  correct: number
  wrong: number
  missed: number
  /** Integer percentage 0..100; 0 when nothing was answered. */
  accuracy: number
}

export function accuracyOf(correct: number, wrong: number, missed: number): AccuracyStats {
  const answered = correct + wrong
  return {
    correct,
    wrong,
    missed,
    accuracy: answered === 0 ? 0 : Math.floor((correct * 100) / answered),
  }
}

/** Stars for the Mưa Chữ level result: 3 = clean run, 1 = finished anyway. */
export function starsFor(accuracy: number, retries: number): number {
  if (accuracy >= 90 && retries <= 2) return 3
  if (accuracy >= 70) return 2
  return 1
}
