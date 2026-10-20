import { render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getGrammarPoints } from '../api/grammar'
import type { GrammarSummary } from '../types'
import Scr017GrammarListPage from './Scr017GrammarListPage'

vi.mock('../api/grammar', () => ({ getGrammarPoints: vi.fn() }))

const getGrammarPointsMock = vi.mocked(getGrammarPoints)

const points: GrammarSummary[] = [
  {
    bestPercent: null,
    exerciseCount: 2,
    hskLevel: 2,
    id: 20,
    learned: false,
    text: '因为……所以……',
    titleVi: 'Nguyên nhân và kết quả',
  },
  {
    bestPercent: 90,
    exerciseCount: 4,
    hskLevel: 7,
    id: 70,
    learned: true,
    text: '与其……不如……',
    titleVi: 'So sánh lựa chọn',
  },
  {
    bestPercent: null,
    exerciseCount: 3,
    hskLevel: 9,
    id: 90,
    learned: false,
    text: '尚且……何况……',
    titleVi: 'Quan hệ tăng tiến',
  },
]

function renderPage(path = '/learn/grammar') {
  const router = createMemoryRouter(
    [{ path: '/learn/grammar', element: <Scr017GrammarListPage /> }],
    { initialEntries: [path] },
  )
  return { router, ...render(<RouterProvider router={router} />) }
}

describe('SCR-017 Grammar List', () => {
  beforeEach(() => getGrammarPointsMock.mockReset().mockResolvedValue(points))

  it('gọi API một lần, chia 7 tab và chọn tab đầu tiên có dữ liệu', async () => {
    renderPage()

    const tabs = await screen.findAllByRole('tab')
    expect(tabs).toHaveLength(7)
    expect(screen.getByRole('tab', { name: 'HSK 2 (1)' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: 'HSK 7–9 (2)' })).toBeVisible()
    expect(await screen.findByRole('link', { name: /因为……所以……/ })).toHaveAttribute(
      'href',
      '/learn/grammar/20',
    )
    expect(getGrammarPointsMock).toHaveBeenCalledTimes(1)
    expect(getGrammarPointsMock).toHaveBeenCalledWith(undefined, expect.any(AbortSignal))
  })

  it('chuẩn hoá level sai bằng replace', async () => {
    const { router } = renderPage('/learn/grammar?level=sai')

    await waitFor(() => expect(router.state.location.search).toBe('?level=2'))
    expect(router.state.historyAction).toBe('REPLACE')
  })

  it('giữ tab hợp lệ dù tab đó chưa có dữ liệu', async () => {
    renderPage('/learn/grammar?level=1')

    expect(await screen.findByText('Chưa có điểm ngữ pháp cho cấp này')).toBeVisible()
    expect(screen.getByRole('tab', { name: 'HSK 1 (0)' })).toHaveAttribute('aria-selected', 'true')
  })
})
