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
})
