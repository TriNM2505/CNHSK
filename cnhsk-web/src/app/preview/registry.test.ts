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

  it('registers all three layout galleries as full-frame previews', () => {
    const layouts = previewRegistry.filter(({ id }) => id.startsWith('layout-'))

    expect(layouts.map(({ id }) => id)).toEqual(['layout-public', 'layout-learner', 'layout-admin'])
    expect(layouts.every(({ fullFrame }) => fullFrame)).toBe(true)
  })

  it('includes practice previews from its feature catalog', () => {
    const practiceIds = previewRegistry
      .filter(({ id }) => id.startsWith('practice-'))
      .map(({ id }) => id)

    expect(practiceIds).toEqual([
      'practice-choice-question',
      'practice-pinyin-input',
      'practice-audio-button',
      'practice-study-card',
      'practice-session-progress',
      'practice-session-summary',
    ])
  })
})
