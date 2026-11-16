import { describe, expect, it } from 'vitest'
import { fallDurationMs } from './arcade'

const timing = { fallStartMs: 6000, fallStepMs: 150, fallMinMs: 3000 }

describe('fallDurationMs', () => {
  it.each([
    [0, 6000],
    [10, 4500],
    [19, 3150],
    [20, 3000],
    [25, 3000],
  ])('AT-20: item %i falls for %i ms', (index, expected) => {
    expect(fallDurationMs(index, timing)).toBe(expected)
  })

  it('uses the timing supplied by the server', () => {
    expect(fallDurationMs(2, { fallStartMs: 5000, fallStepMs: 200, fallMinMs: 2500 })).toBe(4600)
  })
})
