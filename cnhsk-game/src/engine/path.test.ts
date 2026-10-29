import { describe, expect, it } from 'vitest'
import { flattenPath, parsePath, polylineLength, resample, sampleStroke } from './path'

describe('parsePath', () => {
  it('reads move and line commands', () => {
    const segs = parsePath('M 10 20 L 30 40')
    expect(segs.map((s) => s.cmd)).toEqual(['M', 'L'])
    expect(segs[0].points[0]).toEqual({ x: 10, y: 20 })
  })

  it('reads quadratic and cubic commands', () => {
    const segs = parsePath('M 0 0 Q 1 2 3 4 C 5 6 7 8 9 10')
    expect(segs.map((s) => s.cmd)).toEqual(['M', 'Q', 'C'])
  })

  it('handles negative and decimal coordinates', () => {
    const segs = parsePath('M -10.5 0.25 L -3 -4')
    expect(segs[0].points[0].x).toBeCloseTo(-10.5)
    expect(segs[1].points[0].y).toBeCloseTo(-4)
  })
})

describe('flattenPath', () => {
  it('samples a line into many points', () => {
    const pts = flattenPath('M 0 0 L 100 0')
    expect(pts.length).toBe(2)
  })

  it('samples a curve densely', () => {
    const pts = flattenPath('M 0 0 Q 50 100 100 0', 16)
    expect(pts.length).toBeGreaterThan(10)
  })

  it('never returns an empty polyline', () => {
    expect(flattenPath('').length).toBeGreaterThan(0)
  })
})

describe('resample', () => {
  it('returns exactly the requested number of points', () => {
    const pts = resample(flattenPath('M 0 0 Q 50 100 100 0'), 32)
    expect(pts).toHaveLength(32)
  })

  it('keeps the endpoints', () => {
    const pts = resample([{ x: 0, y: 0 }, { x: 10, y: 0 }, { x: 10, y: 10 }], 5)
    expect(pts[0]).toEqual({ x: 0, y: 0 })
    expect(pts[pts.length - 1]).toEqual({ x: 10, y: 10 })
  })

  it('handles a degenerate (zero length) polyline', () => {
    const pts = resample([{ x: 5, y: 5 }, { x: 5, y: 5 }], 4)
    expect(pts).toHaveLength(4)
  })
})

describe('sampleStroke', () => {
  it('produces evenly spaced points along the real stroke data', () => {
    const pts = sampleStroke('M 130 520 Q 500 470 870 495', 24)
    expect(pts).toHaveLength(24)
    const length = polylineLength(pts)
    // a horizontal-ish 1000-box stroke must be roughly 740 long
    expect(length).toBeGreaterThan(600)
    expect(length).toBeLessThan(900)
  })
})
