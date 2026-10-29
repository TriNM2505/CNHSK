/**
 * "Tô chữ" grading used by Thần Bút.
 *
 * The target glyph is rasterised once (in the browser) into a coarse boolean
 * grid; the player's ink is painted into a second grid. Grading is then a pure
 * set comparison:
 *   coverage  = how much of the glyph the player has covered
 *   precision = how much of the player's ink is actually inside the glyph
 *
 * This works for ANY character without stroke-path data, which is why Thần Bút
 * can play the whole word pool while Mưa Chữ (real stroke order) only plays the
 * curated characters that have hand-authored stroke data.
 *
 * Pure module — unit tested, no DOM.
 */

export const MASK_SIZE = 48

export interface GlyphMask {
  size: number
  /** 1 = inside the glyph (after dilation). */
  cells: Uint8Array
  /** Number of cells set to 1. */
  total: number
}

export interface TraceEval {
  /** 0..1 — share of the glyph covered. */
  coverage: number
  /** 0..1 — share of the player's ink that landed inside the glyph. */
  precision: number
  /** Absolute number of covered glyph cells (for progress bars). */
  painted: number
}

export const TRACE_THRESHOLDS = {
  coverage: 0.46,
  precision: 0.5,
} as const

export function createMask(size = MASK_SIZE): GlyphMask {
  return { size, cells: new Uint8Array(size * size), total: 0 }
}

export function countCells(mask: GlyphMask): number {
  let n = 0
  for (let i = 0; i < mask.cells.length; i += 1) if (mask.cells[i]) n += 1
  return n
}

/** Box dilation — gives the player a tolerance band around thin strokes. */
export function dilate(mask: GlyphMask, radius = 1): GlyphMask {
  const { size, cells } = mask
  const out = new Uint8Array(size * size)
  for (let y = 0; y < size; y += 1) {
    for (let x = 0; x < size; x += 1) {
      if (!cells[y * size + x]) continue
      for (let dy = -radius; dy <= radius; dy += 1) {
        for (let dx = -radius; dx <= radius; dx += 1) {
          const nx = x + dx
          const ny = y + dy
          if (nx < 0 || ny < 0 || nx >= size || ny >= size) continue
          out[ny * size + nx] = 1
        }
      }
    }
  }
  const next: GlyphMask = { size, cells: out, total: 0 }
  next.total = countCells(next)
  return next
}

export function evaluateTrace(covered: Uint8Array, mask: GlyphMask): TraceEval {
  let painted = 0
  let outside = 0
  for (let i = 0; i < mask.cells.length; i += 1) {
    if (!covered[i]) continue
    if (mask.cells[i]) painted += 1
    else outside += 1
  }
  const inked = painted + outside
  return {
    coverage: mask.total === 0 ? 0 : painted / mask.total,
    precision: inked === 0 ? 1 : painted / inked,
    painted,
  }
}

export function isTracePass(evalResult: TraceEval): boolean {
  return (
    evalResult.coverage >= TRACE_THRESHOLDS.coverage &&
    evalResult.precision >= TRACE_THRESHOLDS.precision
  )
}

/**
 * Paint a normalised (0..1) segment into a covered grid.
 * Mutates `covered` — the caller owns the buffer.
 */
export function paintSegment(
  covered: Uint8Array,
  size: number,
  from: { x: number; y: number },
  to: { x: number; y: number },
  brush = 0,
): void {
  const steps = Math.max(1, Math.ceil(Math.hypot(to.x - from.x, to.y - from.y) * size * 2))
  for (let s = 0; s <= steps; s += 1) {
    const t = s / steps
    const x = Math.round((from.x + (to.x - from.x) * t) * (size - 1))
    const y = Math.round((from.y + (to.y - from.y) * t) * (size - 1))
    paintCell(covered, size, x, y, brush)
  }
}

export function paintCell(covered: Uint8Array, size: number, x: number, y: number, brush = 0): void {
  const r = brush
  for (let dy = -r; dy <= r; dy += 1) {
    for (let dx = -r; dx <= r; dx += 1) {
      const nx = x + dx
      const ny = y + dy
      if (nx < 0 || ny < 0 || nx >= size || ny >= size) continue
      covered[ny * size + nx] = 1
    }
  }
}
