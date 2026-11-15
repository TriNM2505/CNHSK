import { createElement as h } from 'react'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { previewRegistry } from '@/app/preview/registry'

const previews = previewRegistry.filter((entry) => entry.scr === 'SCR-078')
afterEach(() => vi.unstubAllGlobals())
describe('SCR-078 preview catalog', () => {
  it('registers unique IDs covering form, audit and all usage states', () => {
    const ids = previews.map((entry) => entry.id)
    expect(new Set(ids).size).toBe(ids.length)
    expect(ids).toEqual(
      expect.arrayContaining([
        'scr-078-ready',
        'scr-078-loading',
        'scr-078-empty',
        'scr-078-error',
        'scr-078-confirming',
        'scr-078-reset',
        'scr-078-history',
        'scr-078-usageUnavailable',
        'scr-078-usageMissing',
      ]),
    )
    expect(previewRegistry.some((entry) => entry.scr === 'SCR-067')).toBe(true)
  })
  it.each(['ready', 'confirming', 'history', 'usageUnavailable', 'usageMissing'])(
    'renders %s without HTTP',
    (state) => {
      const fetch = vi.fn()
      vi.stubGlobal('fetch', fetch)
      render(
        h(
          MemoryRouter,
          null,
          previews.find((entry) => entry.id === `scr-078-${state}`)!.render('ready'),
        ),
      )
      expect(screen.getByRole('heading', { name: 'Cài đặt hệ thống' })).toBeVisible()
      expect(fetch).not.toHaveBeenCalled()
    },
  )
})
