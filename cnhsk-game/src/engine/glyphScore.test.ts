import { describe, expect, it } from 'vitest'
import {
  MASK_SIZE,
  createMask,
  dilate,
  evaluateTrace,
  isTracePass,
  paintSegment,
  TRACE_THRESHOLDS,
  type GlyphMask,
} from './glyphScore'

/** Build a square mask from an ASCII picture ('#' = inside the glyph). */
function maskFrom(rows: string[]): GlyphMask {
  const size = rows.length
  const mask = createMask(size)
  rows.forEach((row, y) => {
    row.split('').forEach((ch, x) => {
      if (ch === '#') mask.cells[y * size + x] = 1
    })
  })
  mask.total = mask.cells.reduce((n, v) => n + v, 0)
  return mask
}

const SQUARE_4 = maskFrom(['####', '####', '####', '####'])

describe('dilate', () => {
  it('grows the mask and keeps every original cell', () => {
    const mask = maskFrom(['....', '.##.', '.##.', '....'])
    const grown = dilate(mask, 1)
    expect(grown.total).toBeGreaterThan(mask.total)
    for (let i = 0; i < mask.cells.length; i += 1) {
      if (mask.cells[i]) expect(grown.cells[i]).toBe(1)
    }
  })
})

describe('evaluateTrace', () => {
  it('reports zero coverage for an untouched board', () => {
    const result = evaluateTrace(new Uint8Array(16), SQUARE_4)
    expect(result.coverage).toBe(0)
    expect(result.painted).toBe(0)
  })

  it('reports full coverage and precision for a perfect trace', () => {
    const result = evaluateTrace(new Uint8Array(16).fill(1), SQUARE_4)
    expect(result.coverage).toBe(1)
    expect(result.precision).toBe(1)
    expect(isTracePass(result)).toBe(true)
  })

  it('penalises ink drawn outside the glyph', () => {
    const size = MASK_SIZE
    const cells = new Uint8Array(size * size)
    for (let y = 0; y < 4; y += 1) for (let x = 0; x < 4; x += 1) cells[y * size + x] = 1
    const glyph: GlyphMask = { size, cells, total: 16 }

    const covered = new Uint8Array(size * size)
    for (let y = 0; y < 4; y += 1) for (let x = 0; x < 4; x += 1) covered[y * size + x] = 1
    for (let y = 30; y < 48; y += 1) for (let x = 30; x < 48; x += 1) covered[y * size + x] = 1

    const result = evaluateTrace(covered, glyph)
    expect(result.coverage).toBe(1)
    expect(result.precision).toBeLessThan(0.2)
    expect(isTracePass(result)).toBe(false)
  })

  it('passes as soon as the thresholds are met', () => {
    const covered = new Uint8Array(16)
    for (let i = 0; i < 10; i += 1) covered[i] = 1
    const result = evaluateTrace(covered, SQUARE_4)
    expect(result.coverage).toBeGreaterThanOrEqual(TRACE_THRESHOLDS.coverage)
    expect(isTracePass(result)).toBe(true)
  })

  it('fails while the player has covered too little', () => {
    const covered = new Uint8Array(16)
    covered[0] = 1
    const result = evaluateTrace(covered, SQUARE_4)
    expect(isTracePass(result)).toBe(false)
  })
})

describe('paintSegment', () => {
  it('marks cells along a normalised segment', () => {
    const covered = new Uint8Array(MASK_SIZE * MASK_SIZE)
    paintSegment(covered, MASK_SIZE, { x: 0, y: 0 }, { x: 1, y: 1 })
    expect(covered.reduce((n, v) => n + v, 0)).toBeGreaterThan(10)
  })

  it('stays inside the grid even for out-of-range input', () => {
    const covered = new Uint8Array(MASK_SIZE * MASK_SIZE)
    paintSegment(covered, MASK_SIZE, { x: -5, y: -5 }, { x: 5, y: 5 })
    expect(covered).toHaveLength(MASK_SIZE * MASK_SIZE)
    expect(covered.reduce((n, v) => n + v, 0)).toBeGreaterThan(0)
  })
})
