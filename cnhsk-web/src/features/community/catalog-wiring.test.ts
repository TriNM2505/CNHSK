import { createElement, lazy } from 'react'
import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, expectTypeOf, it, vi } from 'vitest'

import type { PreviewEntry } from '@/app/preview/types'
import { screens } from './screens'

afterEach(() => {
  vi.doUnmock('@/features/community/screens')
  vi.doUnmock('@/features/community/catalog.preview')
  vi.resetModules()
})

describe('community catalog foundation (§10, §12)', () => {
  it('FR057_T16 registers only SCR-039/SCR-041 and supplies unique preview states', async () => {
    const { previews } = await import('./catalog.preview')
    expect(Object.keys(screens)).toEqual(['SCR-039', 'SCR-041'])
    expect(previews.map(({ id }) => id)).toEqual(expect.arrayContaining([
      'scr-039-public-ready', 'scr-039-public-empty', 'scr-039-public-loading', 'scr-039-public-error',
      'scr-039-mine-statuses', 'scr-041-create', 'scr-041-edit-approved', 'scr-041-edit-rejected',
      'scr-041-edit-hidden', 'scr-041-validation-error', 'scr-041-submitting',
    ]))
    expect(new Set(previews.map(({ id }) => id)).size).toBe(previews.length)
    expectTypeOf(previews).toMatchTypeOf<readonly PreviewEntry[]>()
    const minePreview = previews.find(({ id }) => id === 'scr-039-mine-statuses')!
    const mineView = render(createElement(MemoryRouter, null, minePreview.render('ready')))
    const draftCard = screen.getAllByRole('article').find((card) => within(card).queryByRole('heading', { name: 'Bản nháp mẫu' }))
    expect(draftCard).toBeDefined()
    expect(within(draftCard as HTMLElement).getByText('Bản nháp')).toBeVisible()
    mineView.unmount()
  })

  it('FR059_SCR-041 previews render inside learner layout without router/auth context errors', async () => {
    const { previews } = await import('./catalog.preview')
    const editorPreviews = previews.filter(({ scr }) => scr === 'SCR-041')
    expect(editorPreviews).toHaveLength(6)
    for (const preview of editorPreviews) {
      const view = render(createElement(MemoryRouter, null, preview.render('ready')))
      expect(screen.getByRole('navigation', { name: 'Học tập' })).toBeInTheDocument()
      expect(screen.getByRole('main')).toBeInTheDocument()
      view.unmount()
    }
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
