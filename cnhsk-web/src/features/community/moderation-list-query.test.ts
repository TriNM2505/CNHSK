import { describe, expect, it } from 'vitest'

import {
  formatModerationTimestamp,
  MODERATION_PAGE_SIZE,
  parseModerationPage,
} from './moderation-list-query'

describe('moderation queue query (FR-039, FR-046)', () => {
  it('FR039_uses the server contract page size', () => expect(MODERATION_PAGE_SIZE).toBe(20))
  it.each([
    [null, 0],
    ['0', 0],
    ['1', 1],
    ['002', 2],
    ['9007199254740991', Number.MAX_SAFE_INTEGER],
  ] as const)('FR039_parses nonnegative safe integer %s', (value, expected) => {
    expect(parseModerationPage(value)).toBe(expected)
  })
  it.each([
    '',
    '-1',
    '+1',
    '1.5',
    '1.0',
    '1e2',
    ' 1',
    '1 ',
    'Infinity',
    'NaN',
    'abc',
    '9007199254740992',
  ])('FR046_invalid page %s defaults to zero', (value) => {
    expect(parseModerationPage(value)).toBe(0)
  })
  it('FR039_renders timestamps in Vietnam time and preserves invalid source text', () => {
    expect(formatModerationTimestamp('2026-10-30T03:00:00Z')).toContain('10:00')
    expect(formatModerationTimestamp('invalid-source-value')).toBe('invalid-source-value')
  })
})
