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
    lastStudiedAt: null,
    learned: false,
    structure: null,
    text: '因为……所以……',
    titleVi: 'Nguyên nhân và kết quả',
  },
  {
    bestPercent: 90,
    exerciseCount: 4,
    hskLevel: 7,
    id: 70,
    lastStudiedAt: null,
    learned: true,
    structure: null,
    text: '与其……不如……',
    titleVi: 'So sánh lựa chọn',
  },
  {
    bestPercent: 0,
    exerciseCount: 1,
    hskLevel: 8,
    id: 80,
    lastStudiedAt: null,
    learned: true,
    structure: null,
    text: '宁可……也不……',
    titleVi: 'Lựa chọn nhấn mạnh',
  },
  {
    bestPercent: null,
    exerciseCount: 3,
    hskLevel: 9,
    id: 90,
    lastStudiedAt: null,
    learned: false,
    structure: null,
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

  it('gọi API một lần, chia 7 tab và chọn tab đầu tiên có dữ liệu khi HSK 1 rỗng', async () => {
    renderPage()

    const tabs = await screen.findAllByRole('tab')
    expect(tabs).toHaveLength(7)
    expect(screen.getByRole('tab', { name: 'HSK 2 (1)' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: 'HSK 7–9 (3)' })).toBeVisible()
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

  it('gộp cấp 7, 8, 9 và hiển thị đúng trạng thái học cùng điểm cao nhất', async () => {
    renderPage('/learn/grammar?level=7-9')

    expect(await screen.findByRole('tab', { name: 'HSK 7–9 (3)' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    expect(screen.getByText('Điểm cao nhất: 90%')).toBeVisible()
    expect(screen.getByText('Điểm cao nhất: 0%')).toBeVisible()
    expect(screen.getAllByText('Đã học')).toHaveLength(2)

    const unlearnedRow = screen.getByRole('link', { name: /尚且……何况……/ })
    expect(unlearnedRow).not.toHaveTextContent('Đã học')
    expect(unlearnedRow).not.toHaveTextContent('Điểm cao nhất:')
  })
})
