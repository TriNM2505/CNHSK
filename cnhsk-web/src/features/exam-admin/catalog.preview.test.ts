import { createElement as h } from 'react'
import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { previewRegistry } from '@/app/preview/registry'
const previews = previewRegistry.filter(
  (entry) => entry.scr === 'SCR-065' || entry.scr === 'SCR-066',
)
afterEach(() => vi.unstubAllGlobals())
describe('exam preview catalog', () => {
  it('registers every state with unique IDs and keeps existing contest previews', () => {
    const ids = previews.map((entry) => entry.id)
    expect(new Set(ids).size).toBe(ids.length)
    expect(previewRegistry.map((entry) => entry.id)).toEqual(expect.arrayContaining(ids))
    expect(previewRegistry.some((entry) => entry.scr === 'SCR-071')).toBe(true)
    expect(ids).toEqual(
      expect.arrayContaining([
        'scr-065-empty',
        'scr-065-error',
        'scr-066-legacy',
        'scr-066-readonly',
        'scr-066-conflict',
        'scr-066-publication-errors',
      ]),
    )
  })
  it('uses editable self-authored preview state without network traffic', () => {
    const fetch = vi.fn()
    vi.stubGlobal('fetch', fetch)
    render(
      h(
        MemoryRouter,
        null,
        previews.find((entry) => entry.id === 'scr-066-draft')!.render('ready'),
      ),
    )
    fireEvent.change(screen.getByLabelText('Tên đề', { exact: false }), {
      target: { value: 'Xem trước đã sửa' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Lưu nháp' }))
    expect(screen.getByText(/Đã lưu trong bản xem trước/)).toBeVisible()
    expect(fetch).not.toHaveBeenCalled()
  })
})
