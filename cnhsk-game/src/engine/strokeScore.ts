/**
 * Stroke grading for Mưa Chữ (writing practice).
 *
 * A drawn stroke is compared with the target stroke of the character:
 *  - where it starts (startDistance)
 *  - how far it drifts (meanDistance)
 *  - whether it goes the same way (direction cosine)
 *  - whether it is roughly the same length (lengthRatio)
 *
 * All inputs are normalised to 0..1 so the thresholds are resolution independent.
 * Pure module — unit tested, no DOM.
 */

import type { Point } from './path'

export interface StrokeGrade {
  ok: boolean
  /** 0..100 overall quality. */
  score: number
  startDistance: number
  meanDistance: number
  direction: number
  lengthRatio: number
  /** Short Vietnamese hint shown when the stroke is rejected. */
  hint: string | null
}

export const STROKE_THRESHOLDS = {
  passScore: 62,
  startTolerance: 0.22,
  driftTolerance: 0.18,
  /** The drawn stroke must be at least this share of the target length. */
  minLengthRatio: 0.55,
  maxLengthRatio: 1.8,
} as const

function clamp01(v: number): number {
  return v < 0 ? 0 : v > 1 ? 1 : v
}

export function meanDistance(a: Point[], b: Point[]): number {
  const n = Math.min(a.length, b.length)
  if (n === 0) return 1
  let total = 0
  for (let i = 0; i < n; i += 1) total += Math.hypot(a[i].x - b[i].x, a[i].y - b[i].y)
  return total / n
}

/** Cosine between the overall direction of the two strokes (-1..1). */
export function directionScore(a: Point[], b: Point[]): number {
  if (a.length < 2 || b.length < 2) return 0
  const va = { x: a[a.length - 1].x - a[0].x, y: a[a.length - 1].y - a[0].y }
  const vb = { x: b[b.length - 1].x - b[0].x, y: b[b.length - 1].y - b[0].y }
  const la = Math.hypot(va.x, va.y)
  const lb = Math.hypot(vb.x, vb.y)
  if (la === 0 || lb === 0) return 0
  return (va.x * vb.x + va.y * vb.y) / (la * lb)
}

function lengthOf(pts: Point[]): number {
  let total = 0
  for (let i = 1; i < pts.length; i += 1) total += Math.hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
  return total
}

export function gradeStroke(target: Point[], drawn: Point[]): StrokeGrade {
  if (drawn.length < 2) {
    return {
      ok: false,
      score: 0,
      startDistance: 1,
      meanDistance: 1,
      direction: 0,
      lengthRatio: 0,
      hint: 'Nét quá ngắn — hãy viết lại nét này.',
    }
  }

  const startDistance = Math.hypot(drawn[0].x - target[0].x, drawn[0].y - target[0].y)
  const drift = meanDistance(target, drawn)
  const direction = directionScore(target, drawn)

  const lenTarget = lengthOf(target)
  const lenDrawn = lengthOf(drawn)
  const lengthRatio = lenTarget === 0 ? 0 : lenDrawn / lenTarget

  const startScore = clamp01(1 - startDistance / (STROKE_THRESHOLDS.startTolerance * 1.6))
  const driftScore = clamp01(1 - drift / (STROKE_THRESHOLDS.driftTolerance * 1.6))
  const dirScore = clamp01((direction + 0.2) / 1.2)
  const lenScore = clamp01(1 - Math.abs(1 - lengthRatio) / 0.9)

  const score = Math.round(
    100 * (0.22 * startScore + 0.42 * driftScore + 0.2 * dirScore + 0.16 * lenScore),
  )

  const withinLength =
    lengthRatio >= STROKE_THRESHOLDS.minLengthRatio &&
    lengthRatio <= STROKE_THRESHOLDS.maxLengthRatio

  const ok =
    score >= STROKE_THRESHOLDS.passScore &&
    startDistance <= STROKE_THRESHOLDS.startTolerance &&
    drift <= STROKE_THRESHOLDS.driftTolerance &&
    withinLength

  let hint: string | null = null
  if (!ok) {
    if (startDistance > STROKE_THRESHOLDS.startTolerance) hint = 'Bắt đầu chưa đúng vị trí — nhìn mũi tên màu đỏ.'
    else if (lengthRatio < STROKE_THRESHOLDS.minLengthRatio) hint = 'Nét còn quá ngắn — viết hết nét rồi nhấc tay.'
    else if (lengthRatio > STROKE_THRESHOLDS.maxLengthRatio) hint = 'Nét vẽ quá dài.'
    else if (drift > STROKE_THRESHOLDS.driftTolerance) hint = 'Nét đi lệch quá nhiều so với mẫu.'
    else if (direction < 0.35) hint = 'Sai hướng nét — hãy xem lại mũi tên chỉ hướng.'
    else hint = 'Nét chưa đạt — thử lại nhé.'
  }

  return { ok, score, startDistance, meanDistance: drift, direction, lengthRatio, hint }
}
