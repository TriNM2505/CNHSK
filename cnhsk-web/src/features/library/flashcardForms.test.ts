import { describe, expect, it } from 'vitest'

import { ApiError } from '@/shared/api'

import {
  cardRequest,
  characterCount,
  clientPage,
  deckRequest,
  emptyCardDraft,
  emptyDeckDraft,
  flashcardError,
  resourceId,
  reviewChoices,
  validateCardDraft,
  validateDeckDraft,
} from './flashcardForms'

describe('flashcard form and contract helpers', () => {
  it('validates Unicode name boundaries and optional description', () => {
    expect(characterCount('𠀀好')).toBe(2)
    expect(
      validateDeckDraft({
        ...emptyDeckDraft(),
        name: '𠀀'.repeat(100),
        description: 'a'.repeat(500),
      }),
    ).toBeUndefined()
    expect(validateDeckDraft({ ...emptyDeckDraft(), name: '𠀀'.repeat(101) })).toContain('1–100')
    expect(validateDeckDraft({ ...emptyDeckDraft(), name: '  ' })).toBeDefined()
    expect(
      validateDeckDraft({ ...emptyDeckDraft(), name: 'Bộ mới', description: 'a'.repeat(501) }),
    ).toContain('500')
  })

  it('requires a loaded topic and only sends its ID', () => {
    const draft = {
      ...emptyDeckDraft(),
      name: '  Bộ tự soạn  ',
      source: 'topic' as const,
      topicId: '3',
    }
    expect(validateDeckDraft(draft, [{ id: 3, name: 'Chủ đề', totalWords: 10 }])).toBeUndefined()
    expect(validateDeckDraft(draft, [])).toBeDefined()
    expect(deckRequest(draft)).toEqual({ name: 'Bộ tự soạn', description: null, topicId: 3 })
    expect(deckRequest({ ...draft, source: 'empty' })).toEqual({
      name: 'Bộ tự soạn',
      description: null,
    })
  })

  it.each(['0', '-1', 'abc', '1.2', '1e2', '9007199254740992'])(
    'rejects unsafe private resource id %s',
    (id) => expect(resourceId(id)).toBeUndefined(),
  )

  it.each([
    { front: '', back: 'a' },
    { front: ' ', back: 'a' },
    { front: 'a'.repeat(201), back: 'a' },
    { front: 'a', back: '' },
    { front: 'a', back: 'a'.repeat(2001) },
  ])('rejects out-of-contract card draft', (draft) =>
    expect(validateCardDraft(draft)).toBeDefined(),
  )

  it('accepts exact card boundaries and excludes UI/source fields from PATCH payload', () => {
    const draft = { ...emptyCardDraft(), id: 7, front: '𠀀'.repeat(200), back: 'a'.repeat(2000) }
    expect(validateCardDraft(draft)).toBeUndefined()
    expect(cardRequest(draft)).toEqual({ front: draft.front, back: draft.back })
  })

  it('keeps rating values and clamps client pages without sorting data', () => {
    expect(reviewChoices.map(({ rating }) => rating)).toEqual(['AGAIN', 'HARD', 'GOOD', 'EASY'])
    expect(clientPage(2, 21)).toBe(1)
    expect(clientPage(1, 0)).toBe(0)
    expect(clientPage(0, 50)).toBe(0)
  })

  it.each([
    { kind: 'DECKS', limit: 50, unit: 'bộ thẻ' },
    { kind: 'CARDS', limit: 123, unit: 'thẻ trong bộ' },
  ])('uses backend $kind quota details', ({ kind, limit, unit }) => {
    expect(
      flashcardError(
        new ApiError({
          status: 422,
          errorCode: 'LIBRARY_LIMIT_REACHED',
          message: 'Giới hạn',
          details: { kind, limit },
        }),
      ),
    ).toContain(`${limit} ${unit}`)
  })

  it('does not invent a quota limit and preserves server retry delay', () => {
    expect(
      flashcardError(
        new ApiError({ status: 422, errorCode: 'LIBRARY_LIMIT_REACHED', message: 'Giới hạn' }),
      ),
    ).toBe('Đã đạt giới hạn thư viện.')
    expect(
      flashcardError(
        new ApiError({
          status: 429,
          errorCode: 'RATE_LIMITED',
          message: 'Giới hạn',
          extra: { retryAfterSeconds: 45 },
        }),
      ),
    ).toContain('45 giây')
  })
})
