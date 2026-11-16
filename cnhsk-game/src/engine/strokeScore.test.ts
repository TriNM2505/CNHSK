import { describe, expect, it } from 'vitest'
import { gradeStroke, meanDistance } from './strokeScore'
import { normalizePoints, resample } from './path'

const target = resample(
  [
    { x: 0.1, y: 0.5 },
    { x: 0.5, y: 0.48 },
    { x: 0.9, y: 0.5 },
  ],
  24,
)

function jitter(points: { x: number; y: number }[], dx: number, dy: number) {
  return points.map((p) => ({ x: p.x + dx, y: p.y + dy }))
}

describe('meanDistance', () => {
  it('is zero for identical polylines', () => {
    expect(meanDistance(target, target)).toBeCloseTo(0)
  })

  it('grows with the offset', () => {
    expect(meanDistance(target, jitter(target, 0, 0.2))).toBeCloseTo(0.2)
  })
})

describe('gradeStroke', () => {
  it('passes a stroke drawn almost exactly on the target', () => {
    const r = gradeStroke(target, jitter(target, 0, 0.01))
    expect(r.ok).toBe(true)
    expect(r.score).toBeGreaterThan(85)
    expect(r.hint).toBeNull()
  })

  it('rejects a stroke starting in the wrong place', () => {
    const r = gradeStroke(target, jitter(target, 0.4, 0))
    expect(r.ok).toBe(false)
    expect(r.hint).toContain('vị trí')
  })

  it('rejects a stroke going the wrong way', () => {
    const r = gradeStroke(target, [...target].reverse())
    expect(r.ok).toBe(false)
  })

  it('rejects a stroke that is far too short', () => {
    const r = gradeStroke(target, target.slice(0, 4))
    expect(r.ok).toBe(false)
    expect(r.hint).toBeTruthy()
  })

  it('rejects a one-point scribble', () => {
    const r = gradeStroke(target, [{ x: 0.1, y: 0.5 }])
    expect(r.ok).toBe(false)
    expect(r.hint).toContain('quá ngắn')
  })

  it('tolerates a small wobble from a real hand', () => {
    const wobbly = target.map((p, i) => ({ x: p.x, y: p.y + (i % 2 === 0 ? 0.02 : -0.02) }))
    expect(gradeStroke(target, wobbly).ok).toBe(true)
  })

  it('is resolution independent (normalised input)', () => {
    const normalisedTarget = normalizePoints(
      resample(
        [
          { x: 100, y: 500 },
          { x: 500, y: 480 },
          { x: 900, y: 500 },
        ],
        24,
      ),
    )
    expect(gradeStroke(normalisedTarget, jitter(normalisedTarget, 0, 0.01)).ok).toBe(true)
  })
})
