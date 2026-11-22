import type { Point } from './path'

/** Stroke centre lines as hanzi-writer-data stores them: 1024 box, y pointing up from baseline 900. */
export type Medians = readonly (readonly (readonly [number, number])[])[]

// The limits below were tuned on simulated handwriting of all 655 HSK 1-3 characters: loose enough for untidy
// writing, tight enough that another character almost never passes.

/** Strokes the learner may join or split; whole strokes still may not be missing (see STROKE_COVERAGE). */
export const STROKE_COUNT_TOLERANCE = 1
/** Distance, as a share of the character size, within which ink counts as being in the right place. */
export const REACH = 0.12
/** Largest angle between the two strokes at that place; keeps crossing strokes from covering each other. */
export const MAX_ANGLE = (35 * Math.PI) / 180
/** Share of all ink that must be covered, in both directions. */
export const TOTAL_COVERAGE = 0.85
/** Share of every single stroke that must be covered, in both directions. */
export const STROKE_COVERAGE = 0.65

const STEP = 0.035

type Ink = { x: number; y: number; angle: number }

/**
 * Whether free handwriting matches the character it was asked to write. It compares where the ink lies, so stroke
 * order, stroke direction and joined strokes do not matter, and writing small or off-centre is fine.
 */
export function matchesCharacter(drawn: readonly (readonly Point[])[], medians: Medians): boolean {
  const written = drawn.filter((stroke) => length(stroke) > 0)
  if (written.length === 0 || medians.length === 0) return false
  if (Math.abs(written.length - medians.length) > STROKE_COUNT_TOLERANCE) return false

  const target = toUnitBox(medians.map((median) => median.map(([x, y]) => ({ x, y: 900 - y }))))
  const mine = fitTo(written, target)
  return covers(mine.map(ink), target.map(ink)) && covers(target.map(ink), mine.map(ink))
}

/** Whether the ink of `cover` lies along every stroke of `strokes`. */
function covers(strokes: readonly Ink[][], cover: readonly Ink[][]): boolean {
  const all = cover.flat()
  let total = 0
  let covered = 0
  for (const stroke of strokes) {
    const hits = stroke.filter((dot) => all.some((other) => isNear(dot, other))).length
    if (hits / stroke.length < STROKE_COVERAGE) return false
    total += stroke.length
    covered += hits
  }
  return covered / total >= TOTAL_COVERAGE
}

function isNear(a: Ink, b: Ink): boolean {
  if (Math.hypot(a.x - b.x, a.y - b.y) > REACH) return false
  // Strokes have no direction here, so angles half a turn apart are the same line.
  const turn = Math.abs(a.angle - b.angle) % Math.PI
  return Math.min(turn, Math.PI - turn) <= MAX_ANGLE
}

function bounds(strokes: readonly (readonly Point[])[]) {
  const points = strokes.flat()
  const xs = points.map((p) => p.x)
  const ys = points.map((p) => p.y)
  const minX = Math.min(...xs)
  const minY = Math.min(...ys)
  return { minX, minY, width: Math.max(...xs) - minX, height: Math.max(...ys) - minY }
}

function toUnitBox(strokes: readonly (readonly Point[])[]): Point[][] {
  const { minX, minY, width, height } = bounds(strokes)
  const size = Math.max(width, height, 1)
  return strokes.map((stroke) => stroke.map((p) => ({ x: (p.x - minX) / size, y: (p.y - minY) / size })))
}

/** Scales the drawing onto the target's box. Flat characters such as 一 keep their proportions. */
function fitTo(strokes: readonly (readonly Point[])[], target: readonly (readonly Point[])[]): Point[][] {
  const from = bounds(strokes)
  const to = bounds(target)
  let scaleX = to.width / Math.max(from.width, 1)
  let scaleY = to.height / Math.max(from.height, 1)
  if (Math.min(to.width, to.height) < 0.3 || Math.max(scaleX, scaleY) > 2 * Math.min(scaleX, scaleY)) {
    scaleX = scaleY = Math.max(to.width, to.height) / Math.max(from.width, from.height, 1)
  }
  const offsetX = to.minX + (to.width - from.width * scaleX) / 2
  const offsetY = to.minY + (to.height - from.height * scaleY) / 2
  return strokes.map((stroke) => stroke.map((p) => ({
    x: offsetX + (p.x - from.minX) * scaleX,
    y: offsetY + (p.y - from.minY) * scaleY,
  })))
}

/** Evenly spaced dots along a stroke, each with the direction the stroke takes there. */
function ink(stroke: readonly Point[]): Ink[] {
  const total = length(stroke)
  const count = Math.max(2, Math.round(total / STEP))
  const dots: Point[] = []
  let segment = 0
  let passed = 0
  for (let i = 0; i <= count; i++) {
    const want = (total * i) / count
    while (segment < stroke.length - 2 && passed + gap(stroke, segment) < want) passed += gap(stroke, segment++)
    const a = stroke[segment]!
    const b = stroke[segment + 1] ?? a
    const along = gap(stroke, segment) ? Math.min(1, (want - passed) / gap(stroke, segment)) : 0
    dots.push({ x: a.x + (b.x - a.x) * along, y: a.y + (b.y - a.y) * along })
  }
  // Direction over a few dots, so hand tremor does not turn it.
  return dots.map((dot, i) => {
    const before = dots[Math.max(0, i - 2)]!
    const after = dots[Math.min(dots.length - 1, i + 2)]!
    return { ...dot, angle: Math.atan2(after.y - before.y, after.x - before.x) }
  })
}

function gap(stroke: readonly Point[], i: number): number {
  const a = stroke[i]
  const b = stroke[i + 1]
  return a && b ? Math.hypot(b.x - a.x, b.y - a.y) : 0
}

function length(stroke: readonly Point[]): number {
  let sum = 0
  for (let i = 0; i < stroke.length - 1; i++) sum += gap(stroke, i)
  return sum
}
