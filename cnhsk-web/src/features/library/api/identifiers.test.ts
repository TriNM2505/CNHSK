import { describe, expect, it } from 'vitest'

import {
  checkedIdentifiers,
  checkBodyIdentifiers,
  identifierPath,
  isLibraryIdentifier,
} from './identifiers'

describe('library identifiers', () => {
  it.each([1, Number.MAX_SAFE_INTEGER])('accepts safe positive integer %s', (id) => {
    expect(isLibraryIdentifier(id)).toBe(true)
    expect(identifierPath(id)).toBe(String(id))
  })

  it.each([
    0,
    -1,
    1.5,
    NaN,
    Infinity,
    -Infinity,
    Number.MAX_SAFE_INTEGER + 1,
    '1',
    null,
    undefined,
  ])('rejects unsafe identifier %s without coercing it', (id) => {
    expect(isLibraryIdentifier(id)).toBe(false)
    expect(() => identifierPath(id)).toThrow('ID phải là số nguyên dương')
  })

  it('allows absent/null optional links and checks every provided body identifier', () => {
    expect(() => checkBodyIdentifiers({})).not.toThrow()
    expect(() => checkBodyIdentifiers({ lexemeId: null, topicId: null })).not.toThrow()
    expect(() =>
      checkBodyIdentifiers({ lexemeId: 1, topicId: Number.MAX_SAFE_INTEGER }),
    ).not.toThrow()
    expect(() => checkBodyIdentifiers({ lexemeId: 0 })).toThrow()
    expect(() => checkBodyIdentifiers({ topicId: Number.MAX_SAFE_INTEGER + 1 })).toThrow()
  })

  it.each(['items', 'cards', 'segments', 'components', 'containedIn', 'related'])(
    'rejects an unsafe ID nested in %s',
    async (key) => {
      await expect(
        checkedIdentifiers(Promise.resolve({ [key]: [{ id: Number.MAX_SAFE_INTEGER + 1 }] })),
      ).rejects.toMatchObject({ errorCode: 'INVALID_RESPONSE' })
    },
  )

  it('accepts nullable lexeme links and preserves content without inspecting opaque stroke data', async () => {
    const value = {
      id: 1,
      lexemeId: null,
      cards: [{ id: 2, front: '自作' }],
      strokeData: { id: 'opaque' },
    }
    await expect(checkedIdentifiers(Promise.resolve(value))).resolves.toBe(value)
    await expect(checkedIdentifiers(Promise.resolve(undefined))).resolves.toBeUndefined()
  })
})
