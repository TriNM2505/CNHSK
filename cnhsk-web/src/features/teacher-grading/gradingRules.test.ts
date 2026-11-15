import { describe, expect, it } from 'vitest'
import { emptyDraft, resultErrors, resultPayload, totalScore } from './gradingRules'
import { draft } from './test-fixtures'

describe('teacher grading rubric', () => {
  it('requires every criterion and feedback, accepts zero and 25, and sums four criteria', () => {
    expect(Object.keys(resultErrors(emptyDraft()))).toHaveLength(5)
    const value = {
      ...draft,
      criteria: { grammar: '0', vocabulary: '25', coherence: '0', writing: '25' },
    }
    expect(resultErrors(value)).toEqual({})
    expect(totalScore(value)).toBe(50)
    expect(totalScore(draft)).toBe(78)
  })
  it.each(['-1', '26', '1.5', '', 'NaN', 'Infinity'])('rejects criterion %s', (value) => {
    expect(
      resultErrors({ ...draft, criteria: { ...draft.criteria, grammar: value } }),
    ).toMatchObject({ 'criteria.grammar': expect.any(String) })
  })
  it('counts feedback as code points without trimming a valid submitted value', () => {
    const feedback = '😀'.repeat(5000)
    expect(resultErrors({ ...draft, feedback })).toEqual({})
    expect(resultErrors({ ...draft, feedback: feedback + '字' })).toHaveProperty('feedback')
    expect(resultErrors({ ...draft, feedback: '\n\u00a0\u3000' })).toHaveProperty('feedback')
    expect(resultPayload({ ...draft, feedback: '  Góp ý\nriêng  ' }).feedback).toBe(
      '  Góp ý\nriêng  ',
    )
  })
  it('allows 50 corrections, rejects 51 and validates each field in code points', () => {
    const item = { original: '字'.repeat(500), corrected: '😀'.repeat(500), note: '' }
    expect(resultErrors({ ...draft, corrections: Array.from({ length: 50 }, () => item) })).toEqual(
      {},
    )
    expect(
      resultErrors({ ...draft, corrections: Array.from({ length: 51 }, () => item) }),
    ).toHaveProperty('corrections')
    expect(
      resultErrors({
        ...draft,
        corrections: [{ original: ' ', corrected: '\n', note: '😀'.repeat(501) }],
      }),
    ).toMatchObject({
      'corrections.0.original': expect.any(String),
      'corrections.0.corrected': expect.any(String),
      'corrections.0.note': expect.any(String),
    })
  })
  it('creates only contract fields and leaves the draft unchanged', () => {
    const value = { ...structuredClone(draft), score: 999, payout: 999 }
    const before = structuredClone(value),
      payload = resultPayload(value)
    expect(Object.keys(payload).sort()).toEqual(['corrections', 'criteria', 'feedback'])
    expect(payload.criteria).toEqual({ grammar: 20, vocabulary: 18, coherence: 21, writing: 19 })
    expect(value).toEqual(before)
    expect(
      resultPayload({ ...draft, corrections: [{ original: '原文', corrected: '改文', note: '' }] })
        .corrections[0]?.note,
    ).toBeNull()
  })
})
