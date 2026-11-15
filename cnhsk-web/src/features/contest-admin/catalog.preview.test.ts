import { createElement as h } from 'react'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { previewRegistry } from '@/app/preview/registry'
import { winnerFixtures } from './test-fixtures'

const previews = previewRegistry.filter(({ id }) => id.startsWith('contest-admin-'))
afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})
describe('contest admin previews', () => {
  it('registers exactly the two canonical screen IDs', () => {
    expect(previews.map(({ scr }) => scr)).toEqual(['SCR-070', 'SCR-071'])
  })
  for (const entry of previews)
    for (const state of entry.states ?? [])
      it(`renders ${entry.id} / ${state} with no API requests`, () => {
        const fetch = vi.fn()
        vi.stubGlobal('fetch', fetch)
        render(h(MemoryRouter, null, entry.render(state)))
        expect(document.querySelector('main')).toBeTruthy()
        expect(fetch).not.toHaveBeenCalled()
      })
  it('lets QA inspect archived/locked/published contexts and reveals only fake contacts on click', async () => {
    const fetch = vi.fn()
    vi.stubGlobal('fetch', fetch)
    const entry = previews.find(({ scr }) => scr === 'SCR-071')!
    render(h(MemoryRouter, null, entry.render('ready')))
    await userEvent.selectOptions(
      screen.getByLabelText('Tình huống xem trước', { exact: false }),
      'archived',
    )
    expect(screen.getByRole('alert')).toHaveTextContent('Đề đã lưu trữ')
    await userEvent.selectOptions(
      screen.getByLabelText('Tình huống xem trước', { exact: false }),
      'locked',
    )
    expect(screen.getByLabelText('Tên cuộc thi', { exact: false })).toBeDisabled()
    await userEvent.selectOptions(
      screen.getByLabelText('Tình huống xem trước', { exact: false }),
      'published',
    )
    expect(screen.queryByText(winnerFixtures[0]!.email)).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Hiện email' }))
    expect(await screen.findByText(winnerFixtures[0]!.email)).toBeVisible()
    expect(fetch).not.toHaveBeenCalled()
  })
})
