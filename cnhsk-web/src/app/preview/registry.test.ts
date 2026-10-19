import { describe, expect, it } from 'vitest'

import { previewRegistry } from '@/app/preview/registry'

describe('preview registry', () => {
  it('does not contain duplicate ids', () => {
    const ids = previewRegistry.map(({ id }) => id)

    expect(new Set(ids).size).toBe(ids.length)
  })
})
