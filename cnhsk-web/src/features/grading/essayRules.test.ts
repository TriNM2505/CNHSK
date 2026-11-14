import { describe, expect, it } from 'vitest'
import { codePointLength, countHanzi, trimmedEssay, validSubmission } from './essayRules'
import { policy } from './test-fixtures'

describe('grading essay rules match the approved server rules', () => {
  it('counts only U+4E00 through U+9FFF, excluding Extension A/B, emoji and punctuation', () => {
    expect(countHanzi('\u4e00\u9fff㐀𠀀😀，。 abc123')).toBe(2)
    expect(countHanzi('字'.repeat(49) + '㐀𠀀😀')).toBe(49)
  })
  it.each([
    [49, false],
    [50, true],
    [500, true],
    [501, false],
  ] as const)('validates %i Hanzi as %s', (count, valid) => {
    expect(validSubmission('字'.repeat(count), '', policy)).toBe(valid)
  })
  it('uses policy bounds rather than hard-coding the approved defaults', () => {
    const custom = { ...policy, minHanzi: 2, maxHanzi: 3, maxChars: 4 }
    expect(validSubmission('字字', '', custom)).toBe(true)
    expect(validSubmission('字字字字', '', custom)).toBe(false)
    expect(validSubmission('字字abcd', '', custom)).toBe(false)
  })
  it('counts code points, permits 3000 including astral characters and rejects 3001', () => {
    const text = '字'.repeat(50) + '😀'.repeat(2950)
    expect(text.length).toBeGreaterThan(3000)
    expect(codePointLength(text)).toBe(3000)
    expect(validSubmission(text, '😀'.repeat(500), policy)).toBe(true)
    expect(validSubmission(text + '😀', '', policy)).toBe(false)
    expect(validSubmission('字'.repeat(50), '😀'.repeat(501), policy)).toBe(false)
  })
  it('trims the same boundary whitespace as Java and preserves internal line breaks', () => {
    expect(trimmedEssay('\u001c\u00a0\u3000 字\n词 \u001f\u2007')).toBe('字\n词')
    expect(trimmedEssay('\ufeff字\ufeff')).toBe('\ufeff字\ufeff')
    expect(trimmedEssay('\u0085字\u0085')).toBe('\u0085字\u0085')
  })
  it('never permits submission before policy has loaded', () => {
    expect(validSubmission('字'.repeat(50), '')).toBe(false)
  })
})
