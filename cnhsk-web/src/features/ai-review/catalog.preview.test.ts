import { createElement } from 'react'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'

// eslint-disable-next-line no-restricted-imports -- Verify preview-only original fixtures and real views.
import { previews, reviewQueueSample } from './catalog.preview'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})
describe('offline AI review previews', () => {
  it('uses only canonical IDs, unique preview IDs and original queue contract data', () => {
    expect(new Set(previews.map((entry) => entry.id)).size).toBe(previews.length)
    expect(previews.map((entry) => entry.scr)).toEqual(['SCR-058', 'SCR-058', 'SCR-059'])
    expect(reviewQueueSample.items.some((row) => row.runId === null)).toBe(true)
    expect(new Set(reviewQueueSample.items.map((row) => row.viewedByMe))).toEqual(
      new Set([true, false]),
    )
  })
  it('renders every declared state using real views without network or auth', () => {
    const fetch = vi.fn()
    vi.stubGlobal('fetch', fetch)
    for (const entry of previews)
      for (const state of entry.states) {
        render(createElement(MemoryRouter, null, entry.render(state)))
        expect(
          screen.getByRole('heading', {
            name: entry.scr === 'SCR-058' ? 'Duyệt câu hỏi AI' : 'Chi tiết câu hỏi',
          }),
        ).toBeVisible()
        cleanup()
      }
    expect(fetch).not.toHaveBeenCalled()
  })
})
