import { describe, expect, it } from 'vitest'

import { ApiError } from '@/shared/api'
import {
  availableActions,
  draftOf,
  fieldErrors,
  inputOf,
  newDraft,
  validateDraft,
} from './referenceForm'
import { draftReference, publishedReference } from './test-fixtures'

describe('reference form rules', () => {
  it.each([
    ['DRAFT', ['publish', 'hide']],
    ['PUBLISHED', ['hide']],
    ['HIDDEN', ['unhide', 'publish']],
  ] as const)('only offers changing actions for %s', (status, actions) =>
    expect(availableActions(status)).toEqual(actions),
  )
  it('copies only input fields and preserves null/defaults without server fields', () => {
    const draft = draftOf(draftReference)
    draft.tags.push('mới')
    expect(draftReference.tags).not.toContain('mới')
    const body = inputOf(draftOf(draftReference))
    expect(body).toMatchObject({
      thumbnailUrl: null,
      sortOrder: 0,
      author: null,
      publisher: null,
      publishedYear: null,
      isAffiliate: false,
    })
    expect(Object.keys(body).sort()).toEqual(
      [
        'kind',
        'title',
        'description',
        'url',
        'thumbnailUrl',
        'hskMin',
        'hskMax',
        'tags',
        'sortOrder',
        'author',
        'publisher',
        'publishedYear',
        'isAffiliate',
      ].sort(),
    )
  })
  it('clears book fields in non-book payload while retaining their draft', () => {
    const draft = { ...draftOf(publishedReference), kind: 'PODCAST' as const }
    expect(inputOf(draft)).toMatchObject({
      author: null,
      publisher: null,
      publishedYear: null,
      isAffiliate: false,
    })
    expect(inputOf({ ...draft, kind: 'BOOK' })).toMatchObject({
      author: publishedReference.author,
      isAffiliate: true,
    })
    expect(inputOf({ ...draft, url: ' https://example.test/x ' }).url).toBe(
      ' https://example.test/x ',
    )
  })
  it('preserves existing HSK 4–9 values and validates numeric limits', () => {
    const draft = draftOf({ ...publishedReference, hskMin: 4, hskMax: 9 })
    expect(validateDraft(draft)).toEqual({})
    expect(inputOf(draft)).toMatchObject({ hskMin: 4, hskMax: 9 })
    expect(
      validateDraft({ ...draft, sortOrder: '2147483648', publishedYear: '2101', hskMax: '2' }),
    ).toMatchObject({
      sortOrder: expect.any(String),
      publishedYear: expect.any(String),
      hskMax: expect.any(String),
    })
  })
  it('validates code-point lengths, tags and required fields without requiring author for a draft', () => {
    expect(validateDraft(newDraft())).toMatchObject({
      title: expect.any(String),
      url: expect.any(String),
    })
    const valid = {
      ...draftOf(draftReference),
      kind: 'BOOK' as const,
      author: '',
      title: '😀'.repeat(200),
    }
    expect(validateDraft(valid)).toEqual({})
    expect(
      validateDraft({ ...valid, title: valid.title + '😀', tags: ['x'.repeat(31)] }),
    ).toMatchObject({ title: expect.any(String), tags: expect.any(String) })
    expect(
      validateDraft({ ...valid, tags: Array.from({ length: 11 }, () => 'tag') }),
    ).toHaveProperty('tags')
  })
  it.each([
    'MALFORMED_URL',
    'NON_HTTPS_LINK',
    'DOMAIN_NOT_ALLOWED',
    'PUBLISH_INVALID_RESOURCE',
    'VALIDATION_FAILED',
  ])('maps %s only to recognized fields', (errorCode) => {
    const details = [
      { field: 'url', message: 'Liên kết không hợp lệ.' },
      { field: 'unknown', message: 'không dùng' },
      { field: '__proto__', message: 'không dùng' },
      { field: 'title', message: 1 },
    ]
    expect(fieldErrors(new ApiError({ status: 422, errorCode, message: 'Lỗi.', details }))).toEqual(
      { url: 'Liên kết không hợp lệ.' },
    )
  })
  it('keeps non-field and unexpected errors for the general error message', () => {
    expect(
      fieldErrors(
        new ApiError({
          status: 403,
          errorCode: 'FORBIDDEN',
          message: 'Cấm.',
          details: [{ field: 'title', message: 'x' }],
        }),
      ),
    ).toEqual({})
    expect(
      fieldErrors(
        new ApiError({
          status: 422,
          errorCode: 'VALIDATION_FAILED',
          message: 'Lỗi.',
          details: { field: 'title' },
        }),
      ),
    ).toEqual({})
  })
})
