import { describe, expect, it } from 'vitest'
import { SPEAKING_RUBRIC, WRITING_RUBRIC } from '@/features/grading/rubrics'
import { emptyDraft, resultErrors, resultPayload, totalScore } from './gradingRules'
import { draft } from './test-fixtures'

describe('teacher grading rubric', () => {
  it('requires every criterion of the rubric and feedback, and totals on a 0–100 scale', () => {
    expect(Object.keys(resultErrors(emptyDraft(), WRITING_RUBRIC))).toHaveLength(5)
    expect(Object.keys(resultErrors(emptyDraft(), SPEAKING_RUBRIC))).toHaveLength(6)
    const value = { ...draft, scores: { C1: '0', C2: '5', C3: '0', C4: '5' } }
    expect(resultErrors(value, WRITING_RUBRIC)).toEqual({})
    expect(totalScore(value, WRITING_RUBRIC)).toBe(50)
    expect(totalScore(draft, WRITING_RUBRIC)).toBe(75)
    const speaking = { ...draft, scores: { C1: '2', C2: '2', C3: '2', C4: '2', C5: '2' } }
    expect(resultErrors(speaking, SPEAKING_RUBRIC)).toEqual({})
    expect(totalScore(speaking, SPEAKING_RUBRIC)).toBe(67)
  })
  it.each(['-1', '6', '1.5', '', 'NaN', 'Infinity'])('rejects writing score %s', (value) => {
    expect(
      resultErrors({ ...draft, scores: { ...draft.scores, C2: value } }, WRITING_RUBRIC),
    ).toMatchObject({
      'scores.C2': expect.any(String),
    })
  })
  it('caps speaking scores at 3', () => {
    const speaking = { ...draft, scores: { C1: '4', C2: '2', C3: '2', C4: '2', C5: '2' } }
    expect(resultErrors(speaking, SPEAKING_RUBRIC)).toHaveProperty('scores.C1')
  })
  it('rejects the same error code twice', () => {
    expect(
      resultErrors({ ...draft, errors: ['E-NP-06', 'E-NP-06'] }, WRITING_RUBRIC),
    ).toHaveProperty('errors')
  })
  it('counts feedback as code points without trimming a valid submitted value', () => {
    const feedback = '😀'.repeat(5000)
    expect(resultErrors({ ...draft, feedback }, WRITING_RUBRIC)).toEqual({})
    expect(resultErrors({ ...draft, feedback: feedback + '字' }, WRITING_RUBRIC)).toHaveProperty(
      'feedback',
    )
    expect(resultErrors({ ...draft, feedback: '\n 　' }, WRITING_RUBRIC)).toHaveProperty('feedback')
    expect(resultPayload({ ...draft, feedback: '  Góp ý\nriêng  ' }, WRITING_RUBRIC).feedback).toBe(
      '  Góp ý\nriêng  ',
    )
  })
  it('allows 50 corrections, rejects 51 and validates each field in code points', () => {
    const item = { original: '字'.repeat(500), corrected: '😀'.repeat(500), note: '' }
    expect(
      resultErrors(
        { ...draft, corrections: Array.from({ length: 50 }, () => item) },
        WRITING_RUBRIC,
      ),
    ).toEqual({})
    expect(
      resultErrors(
        { ...draft, corrections: Array.from({ length: 51 }, () => item) },
        WRITING_RUBRIC,
      ),
    ).toHaveProperty('corrections')
    expect(
      resultErrors(
        { ...draft, corrections: [{ original: ' ', corrected: '\n', note: '😀'.repeat(501) }] },
        WRITING_RUBRIC,
      ),
    ).toMatchObject({
      'corrections.0.original': expect.any(String),
      'corrections.0.corrected': expect.any(String),
      'corrections.0.note': expect.any(String),
    })
  })
  it('creates only contract fields and leaves the draft unchanged', () => {
    const value = { ...structuredClone(draft), score: 999, payout: 999 }
    const before = structuredClone(value),
      payload = resultPayload(value, WRITING_RUBRIC)
    expect(Object.keys(payload).sort()).toEqual([
      'corrections',
      'errors',
      'feedback',
      'rubric',
      'scores',
    ])
    expect(payload.rubric).toBe('R_VIET')
    expect(payload.scores).toEqual({ C1: 4, C2: 4, C3: 4, C4: 3 })
    expect(payload.errors).toEqual(['E-NP-06'])
    expect(value).toEqual(before)
    expect(
      resultPayload(
        { ...draft, corrections: [{ original: '原文', corrected: '改文', note: '' }] },
        WRITING_RUBRIC,
      ).corrections[0]?.note,
    ).toBeNull()
  })
})
