import { lazy } from 'react'
import { afterEach, describe, expect, expectTypeOf, it, vi } from 'vitest'

import type { PreviewEntry } from '@/app/preview/types'
import { screens } from './screens'

afterEach(() => {
  vi.doUnmock('@/features/community/screens')
  vi.doUnmock('@/features/community/catalog.preview')
  vi.resetModules()
})

describe('community catalog foundation (§10, §12)', () => {
  it('FR057_production catalogs are empty and defer screens to the existing fallback', async () => {
    const { previews } = await import('./catalog.preview')
    expect(screens).toEqual({})
    expect(previews).toEqual([])
    expectTypeOf(previews).toMatchTypeOf<readonly PreviewEntry[]>()
  })

  it('FR057_app registries consume the feature catalogs when test-only entries are supplied', async () => {
    const testScreen = lazy(async () => ({ default: () => null }))
    const testPreview: PreviewEntry = {
      group: 'screen',
      id: 'community-test-wiring',
      scr: 'SCR-039',
      title: 'Test-only catalog entry',
      render: () => null,
    }
    vi.resetModules()
    vi.doMock('@/features/community/screens', () => ({ screens: { 'SCR-039': testScreen } }))
    vi.doMock('@/features/community/catalog.preview', () => ({ previews: [testPreview] }))
    const [{ screenRegistry }, { previewRegistry }] = await Promise.all([
      import('@/app/screenRegistry'),
      import('@/app/preview/registry'),
    ])
    expect(screenRegistry['SCR-039']).toBe(testScreen)
    expect(previewRegistry.filter(({ id }) => id === testPreview.id)).toEqual([testPreview])
    const ids = previewRegistry.map(({ id }) => id)
    expect(new Set(ids).size).toBe(ids.length)
  }, 15_000)
})
