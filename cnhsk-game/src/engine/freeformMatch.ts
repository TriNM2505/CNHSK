import type { Point } from './path'

/** Stroke centre lines as hanzi-writer-data stores them: 1024 box, y pointing up from baseline 900. */
export type Medians = readonly (readonly (readonly [number, number])[])[]

/** `joined`: the character is there but with fewer strokes, so the learner may still be adding the last one. */
export type Reading = 'complete' | 'joined' | 'none'

// The limits below were tuned on simulated handwriting, from neat to very untidy, of HSK 1-3 characters: untidy or
// joined writing passes about 95% of the time, a scribble never, another character about 1 time in 25.

/** Dots compared per character. */
const DOTS = 48
/** How much a difference in direction counts next to a difference in place. */
const TURN_WEIGHT = 0.4
/** Dots further apart than this (in spreads of the character) are simply "wrong". */
const FARTHEST = 1.5
/** Largest mean difference over the whole character. */
const MAX_MEAN = 0.5
/** Largest mean difference over any one stroke; keeps a character that lacks a stroke from passing. */
const MAX_STROKE_MEAN = 1.1
const MAX_STROKE_MEAN_JOINED = 1
/** A shorter trace, as a share of the writing's size, is a slip of the pen. */
const SPECK = 0.05
/** Largest mean and single difference between strokes when the writing has exactly the strokes of the character. */
const MAX_LAYOUT_MEAN = 0.3
const MAX_LAYOUT_STROKE = 0.55
/** Beyond this mean difference of the ink itself, the strokes being in place is a coincidence. */
const MAX_LAYOUT_INK = 0.85

/** A tail shorter than this share of its stroke that turns more than this is a hook. */
const HOOK = 0.25
const HOOK_TURN = (50 * Math.PI) / 180

type StrokeShape = { x: number; y: number; angle: number; straightness: number; length: number }

type Dot = { x: number; y: number; angle: number; stroke: number }

/**
 * Reads free handwriting against the one character that was asked for. Dots along the ink are paired one to one with
 * dots along the character, so stroke order and direction, joined or split strokes, and writing small, uneven or
 * off-centre do not matter. Writing with every stroke in its place also passes when the strokes are the wrong length
 * or end in a hook. Slips of the pen are skipped, and a failed attempt left on the pad does not spoil a later good one.
 */
export function readCharacter(drawn: readonly (readonly Point[])[], medians: Medians): Reading {
  const expected = medians.length
  const model = medians.map((median) => median.map(([x, y]) => ({ x, y: 900 - y })))
  const target = toDots(model)
  const written = withoutSpecks(drawn)
  if (!target || written.length < Math.ceil(expected / 2)) return 'none'
  if (written.length < expected) {
    return fits(written, target, MAX_STROKE_MEAN_JOINED) ? 'joined' : 'none'
  }

  const attempts = new Set([expected, Math.min(written.length, expected + 1)])
  if (written.length <= expected + Math.max(1, Math.floor(expected / 3))) attempts.add(written.length)
  // The last strokes of a longer drawing only count on their own after a whole earlier attempt.
  const wholeDrawing = written.length === expected || written.length >= 2 * expected
  const inPlace = written.slice(-expected)
  return [...attempts].some((count) => fits(written.slice(-count), target, MAX_STROKE_MEAN))
    || (wholeDrawing && sameLayout(inPlace, model) && fits(inPlace, target, Infinity, MAX_LAYOUT_INK)) ? 'complete' : 'none'
}

/** Whether each written stroke can be paired with a stroke of the character that lies in the same place and direction. */
function sameLayout(written: readonly (readonly Point[])[], model: readonly (readonly Point[])[]): boolean {
  const mine = toShapes(written)
  const theirs = toShapes(model)
  const cost = mine.map((a) => theirs.map((b) => Math.hypot(a.x - b.x, a.y - b.y)
    // The overall direction of a bent stroke such as 乙 says little.
    + 0.25 * turnBetween(a.angle, b.angle) * Math.min(a.straightness, b.straightness)
    + 0.4 * Math.abs(a.straightness - b.straightness)
    + 0.1 * Math.min(1.5, Math.abs(Math.log(a.length / b.length)))))
  const costs = pairCosts(cost)
  return Math.max(...costs) <= MAX_LAYOUT_STROKE
    && costs.reduce((sum, value) => sum + value, 0) / costs.length <= MAX_LAYOUT_MEAN
}

/** Where each stroke lies in the box around the whole drawing, and which way it runs. */
function toShapes(strokes: readonly (readonly Point[])[]): StrokeShape[] {
  const points = strokes.flat()
  const range = (values: number[]) => ({ middle: (Math.max(...values) + Math.min(...values)) / 2, size: Math.max(...values) - Math.min(...values) })
  const x = range(points.map((p) => p.x))
  const y = range(points.map((p) => p.y))
  const size = Math.max(x.size, y.size)
  // Flat characters such as 一 keep their proportions.
  const scaleX = x.size < 0.3 * size ? size : x.size
  const scaleY = y.size < 0.3 * size ? size : y.size
  return strokes.map((stroke) => {
    const scaled = withoutHook(stroke).map((p) => ({ x: (p.x - x.middle) / scaleX, y: (p.y - y.middle) / scaleY }))
    const total = length(scaled)
    let centreX = 0
    let centreY = 0
    for (let i = 1; i < scaled.length; i++) {
      const a = scaled[i - 1]!
      const b = scaled[i]!
      const share = Math.hypot(b.x - a.x, b.y - a.y) / total
      centreX += share * (a.x + b.x) / 2
      centreY += share * (a.y + b.y) / 2
    }
    const first = scaled[0]!
    const last = scaled[scaled.length - 1]!
    return {
      x: centreX, y: centreY, length: Math.max(total, 0.02),
      angle: Math.atan2(last.y - first.y, last.x - first.x),
      straightness: Math.hypot(last.x - first.x, last.y - first.y) / total,
    }
  })
}

function fits(written: readonly (readonly Point[])[], target: readonly Dot[], maxStrokeMean: number, maxMean = MAX_MEAN): boolean {
  const mine = toDots(written)
  if (!mine) return false
  const costs = pairCosts(mine.map((dot) => target.map((other) => difference(dot, other))))
  if (costs.reduce((sum, cost) => sum + cost, 0) / DOTS > maxMean) return false
  const perStroke = new Map<number, { sum: number; count: number }>()
  target.forEach(({ stroke }, i) => {
    const entry = perStroke.get(stroke) ?? { sum: 0, count: 0 }
    perStroke.set(stroke, { sum: entry.sum + (costs[i] ?? 0), count: entry.count + 1 })
  })
  return [...perStroke.values()].every(({ sum, count }) => sum / count <= maxStrokeMean)
}

function withoutSpecks(drawn: readonly (readonly Point[])[]): (readonly Point[])[] {
  const strokes = drawn.filter((stroke) => length(stroke) > 0)
  const points = strokes.flat()
  const span = (values: number[]) => Math.max(...values) - Math.min(...values)
  const size = points.length ? Math.max(span(points.map((p) => p.x)), span(points.map((p) => p.y))) : 0
  return strokes.filter((stroke) => length(stroke) >= SPECK * size)
}

/** Drops a short tail that turns off sharply: the hook that ends many handwritten strokes. */
function withoutHook(stroke: readonly Point[]): readonly Point[] {
  const total = length(stroke)
  for (let i = 1; i < stroke.length - 1; i++) {
    const tail = length(stroke.slice(i))
    if (tail > HOOK * total) continue
    const before = stroke[Math.max(0, i - 2)]!
    const corner = stroke[i]!
    const last = stroke[stroke.length - 1]!
    const turn = Math.abs(Math.atan2(last.y - corner.y, last.x - corner.x) - Math.atan2(corner.y - before.y, corner.x - before.x)) % (2 * Math.PI)
    if (Math.min(turn, 2 * Math.PI - turn) > HOOK_TURN) return stroke.slice(0, i + 1)
  }
  return stroke
}

/** Evenly spaced dots over all the ink, centred and scaled to a spread of 1; null when there is nothing to compare. */
function toDots(strokes: readonly (readonly Point[])[]): Dot[] | null {
  const total = strokes.reduce((sum, stroke) => sum + length(stroke), 0)
  if (total === 0) return null
  const dots: Dot[] = []
  let passed = 0
  strokes.forEach((points, stroke) => {
    for (let i = 1; i < points.length; i++) {
      const a = points[i - 1]!
      const b = points[i]!
      const step = Math.hypot(b.x - a.x, b.y - a.y)
      const before = points[Math.max(0, i - 2)]!
      const after = points[Math.min(points.length - 1, i + 1)]!
      const angle = Math.atan2(after.y - before.y, after.x - before.x)
      while (dots.length < DOTS && step > 0 && (dots.length + 0.5) * total / DOTS <= passed + step + 1e-9) {
        const along = ((dots.length + 0.5) * total / DOTS - passed) / step
        dots.push({ x: a.x + (b.x - a.x) * along, y: a.y + (b.y - a.y) * along, angle, stroke })
      }
      passed += step
    }
  })
  if (dots.length < DOTS) return null

  const mean = (values: number[]) => values.reduce((sum, value) => sum + value, 0) / values.length
  const centreX = mean(dots.map((dot) => dot.x))
  const centreY = mean(dots.map((dot) => dot.y))
  let spreadX = Math.sqrt(mean(dots.map((dot) => (dot.x - centreX) ** 2)))
  let spreadY = Math.sqrt(mean(dots.map((dot) => (dot.y - centreY) ** 2)))
  const spread = Math.max(spreadX, spreadY)
  // Flat characters such as 一 keep their proportions.
  if (Math.min(spreadX, spreadY) < 0.35 * spread) spreadX = spreadY = spread
  return dots.map((dot) => ({ ...dot, x: (dot.x - centreX) / spreadX, y: (dot.y - centreY) / spreadY }))
}

function difference(a: Dot, b: Dot): number {
  return Math.min(FARTHEST, Math.hypot(a.x - b.x, a.y - b.y)) + TURN_WEIGHT * turnBetween(a.angle, b.angle)
}

/** Strokes have no direction here, so angles half a turn apart are the same line. */
function turnBetween(a: number, b: number): number {
  const turn = Math.abs(a - b) % Math.PI
  return Math.min(turn, Math.PI - turn)
}

/** The cheapest one-to-one pairing of rows and columns (Hungarian algorithm); returns what each column costs. */
function pairCosts(cost: readonly (readonly number[])[]): number[] {
  const size = cost.length
  const rowPrice = new Float64Array(size + 1)
  const columnPrice = new Float64Array(size + 1)
  const rowOf = new Int32Array(size + 1)
  const previous = new Int32Array(size + 1)
  for (let row = 1; row <= size; row++) {
    const least = new Float64Array(size + 1).fill(Infinity)
    const used = new Uint8Array(size + 1)
    rowOf[0] = row
    let column = 0
    do {
      used[column] = 1
      const current = rowOf[column]!
      let delta = Infinity
      let next = 0
      for (let j = 1; j <= size; j++) {
        if (used[j]) continue
        const reduced = cost[current - 1]![j - 1]! - rowPrice[current]! - columnPrice[j]!
        if (reduced < least[j]!) {
          least[j] = reduced
          previous[j] = column
        }
        if (least[j]! < delta) {
          delta = least[j]!
          next = j
        }
      }
      for (let j = 0; j <= size; j++) {
        if (used[j]) {
          rowPrice[rowOf[j]!]! += delta
          columnPrice[j]! -= delta
        } else {
          least[j]! -= delta
        }
      }
      column = next
    } while (rowOf[column] !== 0)
    do {
      const back = previous[column]!
      rowOf[column] = rowOf[back]!
      column = back
    } while (column !== 0)
  }
  return cost.map((_, j) => cost[rowOf[j + 1]! - 1]![j]!)
}

function length(stroke: readonly Point[]): number {
  let sum = 0
  for (let i = 1; i < stroke.length; i++) sum += Math.hypot(stroke[i]!.x - stroke[i - 1]!.x, stroke[i]!.y - stroke[i - 1]!.y)
  return sum
}