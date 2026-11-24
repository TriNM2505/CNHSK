import { describe, expect, it } from 'vitest'

import { currentPronunciationPath } from '../paths'

describe('old pronunciation links', () => {
  it.each([
    ['/learn/pronunciation', '/learn/listening'],
    ['/learn/pronunciation/', '/learn/listening'],
    ['/learn/pronunciation/stages/3', '/learn/listening/stages/3'],
    ['/learn/pronunciation/speaking', '/learn/speaking'],
  ])('moves %s to %s', (from, to) => {
    expect(currentPronunciationPath(from)).toBe(to)
  })

  it('leaves the current paths alone', () => {
    expect(currentPronunciationPath('/learn/listening')).toBeUndefined()
    expect(currentPronunciationPath('/learn/speaking')).toBeUndefined()
  })
})
