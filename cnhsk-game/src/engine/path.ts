/**
 * Minimal SVG path sampler for the hand-authored stroke data.
 * Supports M / L / Q / C / Z only — that is all the stroke data uses.
 * Coordinates are in the 0..1000 glyph box used by `data/strokes.ts`.
 */

export interface Point {
  x: number
  y: number
}

type Command = 'M' | 'L' | 'Q' | 'C' | 'Z'

interface Segment {
  cmd: Command
  points: Point[]
}

export function parsePath(d: string): Segment[] {
  const tokens = d.match(/[MLQCZmlqcz]|-?\d*\.?\d+(?:e[-+]?\d+)?/gi) ?? []
  const segments: Segment[] = []
  let i = 0
  let current: Command | null = null
  let buffer: number[] = []

  const flush = () => {
    if (!current) return
    const pts: Point[] = []
    for (let k = 0; k + 1 < buffer.length; k += 2) pts.push({ x: buffer[k], y: buffer[k + 1] })
    if (current === 'Z') segments.push({ cmd: 'Z', points: [] })
    else if (pts.length > 0) segments.push({ cmd: current, points: pts })
    buffer = []
  }

  while (i < tokens.length) {
    const tok = tokens[i]
    const upper = tok.toUpperCase()
    if ('MLQCZ'.includes(upper) && tok.length === 1) {
      flush()
      current = upper as Command
      i += 1
      if (current === 'Z') flush()
      continue
    }
    const value = Number(tok)
    if (!Number.isNaN(value)) buffer.push(value)
    i += 1
  }
  flush()
  return segments
}

function lerp(a: Point, b: Point, t: number): Point {
  return { x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t }
}

function quad(p0: Point, p1: Point, p2: Point, t: number): Point {
  const u = 1 - t
  return {
    x: u * u * p0.x + 2 * u * t * p1.x + t * t * p2.x,
    y: u * u * p0.y + 2 * u * t * p1.y + t * t * p2.y,
  }
}

function cubic(p0: Point, p1: Point, p2: Point, p3: Point, t: number): Point {
  const u = 1 - t
  return {
    x: u * u * u * p0.x + 3 * u * u * t * p1.x + 3 * u * t * t * p2.x + t * t * t * p3.x,
    y: u * u * u * p0.y + 3 * u * u * t * p1.y + 3 * u * t * t * p2.y + t * t * t * p3.y,
  }
}

/** Densely sample a path into a polyline (no arc-length normalisation yet). */
export function flattenPath(d: string, perSegment = 16): Point[] {
  const segments = parsePath(d)
  const out: Point[] = []
  let cursor: Point = { x: 0, y: 0 }
  let start: Point = { x: 0, y: 0 }

  for (const seg of segments) {
    if (seg.cmd === 'M') {
      cursor = seg.points[0]
      start = cursor
      out.push({ ...cursor })
      for (let k = 1; k < seg.points.length; k += 1) {
        cursor = seg.points[k]
        out.push({ ...cursor })
      }
      continue
    }
    if (seg.cmd === 'L') {
      for (const p of seg.points) {
        cursor = p
        out.push({ ...p })
      }
      continue
    }
    if (seg.cmd === 'Q') {
      for (let k = 0; k + 1 < seg.points.length; k += 2) {
        const c = seg.points[k]
        const end = seg.points[k + 1]
        for (let s = 1; s <= perSegment; s += 1) out.push(quad(cursor, c, end, s / perSegment))
        cursor = end
      }
      continue
    }
    if (seg.cmd === 'C') {
      for (let k = 0; k + 2 < seg.points.length; k += 3) {
        const c1 = seg.points[k]
        const c2 = seg.points[k + 1]
        const end = seg.points[k + 2]
        for (let s = 1; s <= perSegment; s += 1) out.push(cubic(cursor, c1, c2, end, s / perSegment))
        cursor = end
      }
      continue
    }
    if (seg.cmd === 'Z') {
      for (let s = 1; s <= perSegment; s += 1) out.push(lerp(cursor, start, s / perSegment))
      cursor = start
    }
  }
  return out.length > 0 ? out : [{ x: 0, y: 0 }]
}

export function polylineLength(pts: Point[]): number {
  let total = 0
  for (let i = 1; i < pts.length; i += 1) total += Math.hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
  return total
}

/** Resample a polyline to exactly `count` points spaced evenly along its arc length. */
export function resample(pts: Point[], count: number): Point[] {
  if (pts.length === 0) return []
  if (pts.length === 1 || count <= 1) return new Array(Math.max(1, count)).fill(null).map(() => ({ ...pts[0] }))
  const total = polylineLength(pts)
  if (total === 0) return new Array(count).fill(null).map(() => ({ ...pts[0] }))

  const step = total / (count - 1)
  const out: Point[] = [{ ...pts[0] }]
  let acc = 0
  let index = 1
  let prev = pts[0]

  while (index < pts.length && out.length < count) {
    const next = pts[index]
    const segLen = Math.hypot(next.x - prev.x, next.y - prev.y)
    if (acc + segLen >= step - 1e-9) {
      const t = (step - acc) / (segLen || 1)
      const at = lerp(prev, next, t)
      out.push(at)
      prev = at
      acc = 0
    } else {
      acc += segLen
      prev = next
      index += 1
    }
  }
  while (out.length < count) out.push({ ...pts[pts.length - 1] })
  return out.slice(0, count)
}

/** Sample a stroke path into `count` evenly spaced points in the 0..1000 box. */
export function sampleStroke(d: string, count = 32): Point[] {
  return resample(flattenPath(d), count)
}

/** Move points from the 0..1000 glyph box into a normalised 0..1 space. */
export function normalizePoints(pts: Point[], box = 1000): Point[] {
  return pts.map((p) => ({ x: p.x / box, y: p.y / box }))
}
