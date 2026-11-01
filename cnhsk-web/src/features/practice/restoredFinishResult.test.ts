import { describe, expect, it } from 'vitest'

import type { SessionView } from './types'
import { restoredFinishResult } from './restoredFinishResult'

function session(overrides: Partial<SessionView> = {}): SessionView {
  return {
    answered: [],
    expiresAt: '2026-11-01T10:00:00Z',
    feedback: 'IMMEDIATE',
    items: [],
    refId: 1,
    servedAt: '2026-11-01T09:00:00Z',
    sessionId: 1,
    source: 'TOPIC_PRACTICE',
    status: 'IN_PROGRESS',
    ...overrides,
  }
}

describe('restoredFinishResult', () => {
  it('returns undefined without a submitted session', () => {
    expect(restoredFinishResult(null)).toBeUndefined()
    expect(restoredFinishResult(session())).toBeUndefined()
  })

  it.each(['ABANDONED', 'EXPIRED', 'IN_PROGRESS'] as const)(
    'returns undefined for a %s session',
    (status) => {
      expect(restoredFinishResult(session({ status }))).toBeUndefined()
    },
  )

  it('rebuilds the submitted answer summary without source-specific extra data', () => {
    const result = restoredFinishResult(
      session({
        answered: [
          { answer: 'A', correct: true, correctAnswer: 'A', counted: true, no: 1 },
          { answer: 'B', correct: false, correctAnswer: 'A', counted: true, no: 2 },
          { answer: 'SKIPPED', counted: false, no: 3 },
        ],
        status: 'SUBMITTED',
      }),
    )

    expect(result).toEqual({
      items: [
        { correct: true, correctAnswer: 'A', no: 1, yourAnswer: 'A' },
        { correct: false, correctAnswer: 'A', no: 2, yourAnswer: 'B' },
        { correct: undefined, correctAnswer: undefined, no: 3, yourAnswer: 'SKIPPED' },
      ],
      maxScore: 2,
      percent: 50,
      score: 1,
      sessionId: 1,
      status: 'SUBMITTED',
      suspicious: false,
    })
    expect(result).not.toHaveProperty('extra')
  })

  it('floors the score, keeps skipped answers as items, and uses null percentage with no score', () => {
    const result = restoredFinishResult(
      session({
        answered: [
          { answer: 'A', correct: true, counted: true, no: 1 },
          { answer: 'B', correct: true, counted: true, no: 2 },
          { answer: 'C', correct: false, counted: true, no: 3 },
          { answer: null, correct: null, counted: false, no: 4 },
        ],
        status: 'SUBMITTED',
      }),
    )

    expect(result).toMatchObject({ maxScore: 3, percent: 66, score: 2 })
    expect(result?.items).toContainEqual({ correct: null, correctAnswer: undefined, no: 4, yourAnswer: null })

    const empty = restoredFinishResult(session({ status: 'SUBMITTED' }))
    expect(empty).toMatchObject({ items: [], maxScore: 0, percent: null, score: 0 })
  })

  it('uses only a caller-filtered answer list and leaves the input session unchanged', () => {
    const submitted = session({
      answered: [
        { answer: 'A', correct: true, counted: true, no: 1 },
        { answer: 'B', correct: false, counted: true, no: 2 },
      ],
      status: 'SUBMITTED',
    })
    const before = structuredClone(submitted)
    const filtered = { ...submitted, answered: submitted.answered.filter((answer) => answer.no === 2) }

    expect(restoredFinishResult(filtered)).toMatchObject({
      items: [{ correct: false, no: 2, yourAnswer: 'B' }],
      maxScore: 1,
      percent: 0,
      score: 0,
    })
    expect(submitted).toEqual(before)
  })
})
