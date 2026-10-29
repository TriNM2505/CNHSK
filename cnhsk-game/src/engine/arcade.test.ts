import { describe, expect, it } from 'vitest'
import {
  ARCADE_RULES,
  accuracyOf,
  drawQueue,
  fallDurationMs,
  mulberry32,
  stageConfig,
  starsFor,
  strikePoints,
  timeAttackConfig,
} from './arcade'

describe('fallDurationMs', () => {
  it('follows spec 008 FR-004: max(fallMin, fallStart - i * fallStep)', () => {
    expect(fallDurationMs(0)).toBe(ARCADE_RULES.fallStartMs)
    expect(fallDurationMs(10)).toBe(6000 - 10 * 150)
    expect(fallDurationMs(100)).toBe(ARCADE_RULES.fallMinMs)
  })

  it('never goes below the floor', () => {
    for (let i = 0; i < 200; i += 1) {
      expect(fallDurationMs(i)).toBeGreaterThanOrEqual(ARCADE_RULES.fallMinMs)
    }
  })

  it('never speeds up as the round progresses', () => {
    for (let i = 1; i < 40; i += 1) {
      expect(fallDurationMs(i)).toBeLessThanOrEqual(fallDurationMs(i - 1))
    }
  })

  it('gives stage mode more time to trace', () => {
    expect(fallDurationMs(0, 'stage')).toBeGreaterThan(fallDurationMs(0, 'time'))
  })
})

describe('strikePoints', () => {
  it('is at least the base value', () => {
    expect(strikePoints(0, 0)).toBe(ARCADE_RULES.basePoints)
  })

  it('grows with the combo and caps it', () => {
    const c0 = strikePoints(0, 0)
    const c5 = strikePoints(5, 0)
    const c99 = strikePoints(99, 0)
    expect(c5).toBeGreaterThan(c0)
    expect(c99).toBe(strikePoints(ARCADE_RULES.comboCap, 0))
  })

  it('rewards a fast strike', () => {
    expect(strikePoints(0, 1)).toBeGreaterThan(strikePoints(0, 0))
  })

  it('only ever returns integers', () => {
    for (const combo of [0, 1, 3, 7, 12]) {
      for (const speed of [0, 0.33, 0.5, 1]) {
        expect(Number.isInteger(strikePoints(combo, speed))).toBe(true)
      }
    }
  })

  it('clamps out-of-range speed ratios', () => {
    expect(strikePoints(0, -5)).toBe(strikePoints(0, 0))
    expect(strikePoints(0, 5)).toBe(strikePoints(0, 1))
  })
})

describe('stageConfig', () => {
  it('gets harder each stage', () => {
    const a = stageConfig(1)
    const b = stageConfig(3)
    expect(b.quota).toBeGreaterThan(a.quota)
    expect(b.requiredStrokes).toBeGreaterThanOrEqual(a.requiredStrokes)
  })

  it('clamps to the last stage', () => {
    expect(stageConfig(99).level).toBe(ARCADE_RULES.maxStage)
    expect(stageConfig(0).level).toBe(1)
  })

  it('gives stage mode lives and no clock', () => {
    expect(stageConfig(2).lives).toBe(ARCADE_RULES.stageLives)
    expect(stageConfig(2).seconds).toBeNull()
  })

  it('gives time attack a 60 second clock and no lives', () => {
    const cfg = timeAttackConfig()
    expect(cfg.seconds).toBe(60)
    expect(cfg.lives).toBe(0)
  })
})

describe('drawQueue', () => {
  const pool = ['a', 'b', 'c', 'd', 'e']

  it('is deterministic for a given seed', () => {
    const a = drawQueue(pool, 5, mulberry32(42))
    const b = drawQueue(pool, 5, mulberry32(42))
    expect(a).toEqual(b)
  })

  it('never returns more than requested', () => {
    expect(drawQueue(pool, 3, mulberry32(1))).toHaveLength(3)
  })

  it('cycles when asked for more than the pool holds', () => {
    expect(drawQueue(pool, 12, mulberry32(7))).toHaveLength(12)
  })

  it('returns nothing for an empty pool', () => {
    expect(drawQueue([], 5, mulberry32(1))).toEqual([])
  })
})

describe('accuracyOf', () => {
  it('counts only answered items', () => {
    expect(accuracyOf(8, 2, 5).accuracy).toBe(80)
  })

  it('is 0 when nothing was answered', () => {
    expect(accuracyOf(0, 0, 5).accuracy).toBe(0)
  })

  it('never returns a float percentage', () => {
    expect(Number.isInteger(accuracyOf(1, 2, 0).accuracy)).toBe(true)
  })
})

describe('starsFor', () => {
  it('gives three stars for a clean run', () => {
    expect(starsFor(100, 0)).toBe(3)
  })

  it('never gives zero stars for a finished level', () => {
    expect(starsFor(10, 20)).toBeGreaterThanOrEqual(1)
  })
})
