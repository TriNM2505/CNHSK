import { describe, expect, it } from 'vitest'

import { previewRegistry } from '@/app/preview/registry'

describe('preview registry', () => {
  it('does not contain duplicate ids', () => {
    const ids = previewRegistry.map(({ id }) => id)

    expect(new Set(ids).size).toBe(ids.length)
  })

  it('registers every FE-08 component gallery', () => {
    const ids = previewRegistry.map(({ id }) => id)

    expect(ids).toEqual(
      expect.arrayContaining([
        'component-progress',
        'component-data-table',
        'component-pagination',
        'component-tabs',
        'component-confirm-panel',
        'component-toast',
        'component-empty-state',
        'component-error-state',
        'component-skeleton',
        'component-page-state',
      ]),
    )
  })
})
