import { describe, expect, it } from 'vitest'

import { parsePostId, toPostWrite, validatePostForm, type PostFormValues } from './postForm'

const valid: PostFormValues = { title: '  Tiêu đề  ', content: '  Nội dung  ', category: 'QUESTION' }

describe('community post form helpers (FR-007, FR-010, FR-059)', () => {
  it.each([['42', 42], ['0042', 42], ['1', 1], ['9007199254740991', Number.MAX_SAFE_INTEGER]] as const)(
    'FR059_accepts positive safe integer route ID %s', (raw, expected) => {
      expect(parsePostId(raw)).toBe(expected)
    },
  )

  it.each([undefined, '', '0', '-1', '1.0', '1e2', ' 42', '9007199254740992', '999999999999999999999']) (
    'FR059_rejects invalid or unsafe route ID %s', (raw) => {
      expect(parsePostId(raw)).toBeUndefined()
    },
  )

  it('FR059_validates trimmed values and normalizes only the approved fields', () => {
    expect(validatePostForm(valid)).toEqual({})
    expect(toPostWrite(valid, false)).toEqual({ title: 'Tiêu đề', content: 'Nội dung', category: 'QUESTION', submit: false })
  })

  it('FR059_checks title and content boundaries after trim', () => {
    expect(validatePostForm({ title: 'x'.repeat(200), content: 'y'.repeat(50_000), category: 'JOB' })).toEqual({})
    expect(validatePostForm({ title: 'x'.repeat(201), content: 'y'.repeat(50_001), category: 'JOB' })).toMatchObject({ title: expect.any(String), content: expect.any(String) })
    expect(validatePostForm({ title: '   ', content: '\n\t', category: '' })).toMatchObject({ title: expect.any(String), content: expect.any(String), category: expect.any(String) })
  })

  it('FR059_counts Unicode strings using JavaScript string length without claiming code-point equivalence', () => {
    const emoji = '😀'
    expect(emoji.length).toBe(2)
    expect(validatePostForm({ title: emoji, content: '中'.repeat(50_000), category: 'CULTURE' })).toEqual({})
  })

  it('FR059_does not construct a payload from an invalid category', () => {
    expect(toPostWrite({ ...valid, category: '' }, true)).toBeUndefined()
    expect(toPostWrite({ ...valid, category: 'INVALID' as PostFormValues['category'] }, true)).toBeUndefined()
  })
})
