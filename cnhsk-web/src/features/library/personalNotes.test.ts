import { describe, expect, it } from 'vitest'

import { ApiError } from '@/shared/api'

import {
  duplicateNoteId,
  newNoteDraft,
  noteDraft,
  noteErrorMessage,
  noteRequest,
  notesSearchParams,
  readNotesSearch,
  snippetParts,
  validateNoteDraft,
  validateNotesSearch,
} from './personalNotes'

describe('personal notes input and contract helpers', () => {
  it.each([1, 2, 100, 101])('validates %i Unicode code points after trim and NFC', (count) => {
    const error = validateNotesSearch(`  ${'𠀀'.repeat(count)}  `, '')
    expect(error === undefined).toBe(count >= 2 && count <= 100)
  })

  it('normalizes search to NFC, permits clearing, and rejects controls', () => {
    expect(validateNotesSearch('a\u0301', '')).toBeDefined()
    expect(validateNotesSearch('  a\u0301b  ', '')).toBeUndefined()
    expect(validateNotesSearch('', '')).toBeUndefined()
    expect(validateNotesSearch('a\nb', '')).toBeDefined()
    expect(
      readNotesSearch(new URLSearchParams({ q: '  a\u0301b  ', tag: 'Thẻ Chính xác' })),
    ).toMatchObject({ q: 'áb', tag: 'Thẻ Chính xác' })
  })

  it.each(['-1', 'abc', '1.5', '9007199254740992'])('rejects unsafe URL page %s', (page) => {
    expect(readNotesSearch(new URLSearchParams({ page })).page).toBe(0)
  })

  it('round-trips query, exact tag, zero-based page and trash tab', () => {
    const search = { q: 'gia đình', tag: 'A & B', page: 2, tab: 'trash' as const }
    expect(readNotesSearch(notesSearchParams(search))).toEqual(search)
    expect(validateNotesSearch('', '𠀀'.repeat(50))).toBeUndefined()
    expect(validateNotesSearch('', '𠀀'.repeat(51))).toBeDefined()
  })

  it('keeps optional title and source metadata when editing', () => {
    const draft = noteDraft({
      id: 3,
      title: null,
      content: 'Nội dung',
      tags: ['a,b'],
      lexemeId: 123,
      sourceType: 'DICTIONARY',
      createdAt: '2026-11-01T00:00:00Z',
      updatedAt: '2026-11-01T00:00:00Z',
    })
    expect(validateNoteDraft(draft)).toBeUndefined()
    expect(noteRequest({ ...draft, tagInput: '  học tập  ' })).toEqual({
      title: null,
      content: 'Nội dung',
      tags: ['a,b', 'học tập'],
      lexemeId: 123,
      sourceType: 'DICTIONARY',
    })
  })

  it.each([
    { content: ' ' },
    { content: 'a'.repeat(10001) },
    { title: 'a'.repeat(201), content: 'a' },
    { content: 'a', tags: Array.from({ length: 11 }, () => 'thẻ') },
    { content: 'a', tagInput: 'a'.repeat(51) },
    { content: 'a\0' },
  ])('validates editor limits without changing the draft', (values) => {
    const draft = { ...newNoteDraft(), ...values }
    const copy = structuredClone(draft)
    expect(validateNoteDraft(draft)).toBeDefined()
    expect(draft).toEqual(copy)
  })

  it('accepts exact editor boundaries', () => {
    expect(
      validateNoteDraft({
        ...newNoteDraft(),
        title: 'a'.repeat(200),
        content: 'a'.repeat(10000),
        tags: Array.from({ length: 10 }, () => 'b'.repeat(50)),
      }),
    ).toBeUndefined()
  })

  it('splits literal keyword matches while preserving raw HTML text', () => {
    const text = '<script>gia đình</script> GIA ĐÌNH <b>a+b</b>'
    const parts = snippetParts(text, 'gia đình')
    expect(parts.filter((part) => part.match).map((part) => part.text)).toEqual([
      'gia đình',
      'GIA ĐÌNH',
    ])
    expect(parts.map((part) => part.text).join('')).toBe(text)
    expect(snippetParts(text, 'a+b').filter((part) => part.match)).toEqual([
      { text: 'a+b', match: true },
    ])
  })

  it.each([undefined, null, {}, { noteId: '12' }, { noteId: -1 }, { noteId: 9007199254740992 }])(
    'does not open malformed duplicate details %j',
    (details) => {
      expect(
        duplicateNoteId(
          new ApiError({ status: 409, errorCode: 'NOTE_DUPLICATE', message: 'Trùng', details }),
        ),
      ).toBeUndefined()
    },
  )

  it('uses the backend duplicate ID and backend limit', () => {
    expect(
      duplicateNoteId(
        new ApiError({
          status: 409,
          errorCode: 'NOTE_DUPLICATE',
          message: 'Trùng',
          details: { noteId: 42 },
        }),
      ),
    ).toBe(42)
    expect(
      noteErrorMessage(
        new ApiError({
          status: 422,
          errorCode: 'LIBRARY_LIMIT_REACHED',
          message: 'Giới hạn',
          details: { kind: 'NOTES', limit: 123 },
        }),
      ),
    ).toContain('123')
    expect(
      noteErrorMessage(
        new ApiError({ status: 422, errorCode: 'LIBRARY_LIMIT_REACHED', message: 'Giới hạn' }),
      ),
    ).not.toContain('1.000')
  })
})
