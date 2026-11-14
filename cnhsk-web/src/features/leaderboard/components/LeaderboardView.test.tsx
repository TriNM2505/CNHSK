import { fireEvent, render, screen } from '@testing-library/react'
import type { ComponentProps } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { LeaderboardView } from './LeaderboardView'
import type { Leaderboard, LeaderboardEntry } from '../types'

const entries: LeaderboardEntry[] = Array.from({ length: 20 }, (_, index) => ({
  rank: index + 1,
  userId: index + 1,
  displayName: `Người học ${index + 1}`,
  avatarUrl: null,
  score: 100 - index,
  durationMs: index === 0 ? null : 65000,
}))

const data: Leaderboard = {
  type: 'TOPIC',
  ref: '43',
  period: 'week',
  periodStart: '2026-11-09T00:00:00+07:00',
  periodEnd: '2026-11-16T00:00:00+07:00',
  entries,
  myEntry: {
    rank: 128,
    userId: 128,
    displayName: 'Hạng xa',
    avatarUrl: null,
    score: 72,
    durationMs: null,
  },
}

function renderView(overrides: Partial<ComponentProps<typeof LeaderboardView>> = {}) {
  return render(
    <MemoryRouter>
      <LeaderboardView
        kind="topic"
        title="Bảng chủ đề"
        period="week"
        data={data}
        state="ready"
        guest={false}
        selectedId="43"
        onSelect={vi.fn()}
        onPeriodChange={vi.fn()}
        onRetry={vi.fn()}
        {...overrides}
      />
    </MemoryRouter>,
  )
}

describe('LeaderboardView', () => {
  it('shows five entries first, expands the existing response to twenty and shows my rank card', () => {
    const { container } = renderView()
    expect(container.querySelector('table')?.parentElement).toHaveClass('min-[641px]:block')
    expect(container.querySelector('ol[aria-label="Bảng xếp hạng"]')).toHaveClass(
      'min-[641px]:hidden',
    )
    expect(screen.getAllByText(/Người học \d+/).length).toBeGreaterThanOrEqual(5)
    expect(screen.getByText('Hạng của bạn: #128')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Xem thêm' }))
    expect(screen.getAllByText('Người học 20')).toHaveLength(2)
    expect(screen.getByText('Hạng của bạn: #128')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Thu gọn' }))
    expect(screen.queryAllByText('Người học 20')).toHaveLength(0)
  })

  it('replaces the own-rank card with a highlighted row when that row is expanded into view', () => {
    renderView({ data: { ...data, myEntry: entries[9]! } })
    expect(screen.getByText('Hạng của bạn: #10')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Xem thêm' }))
    expect(screen.queryByText('Hạng của bạn: #10')).not.toBeInTheDocument()
    expect(screen.getAllByText('Của bạn').length).toBeGreaterThan(0)
  })

  it('highlights own visible row and offers login to guests', () => {
    renderView({ data: { ...data, myEntry: entries[0]! }, guest: true })
    expect(screen.queryByText('Của bạn')).not.toBeInTheDocument()
    expect(screen.getByText('Đăng nhập để xem hạng của bạn.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Đăng nhập' })).toHaveAttribute('href', '/login')
  })

  it('shows retryable error and missing topic copy without retaining the board', () => {
    const onRetry = vi.fn()
    renderView({ data: undefined, state: 'error', topicMissing: true, onRetry })
    expect(screen.getByText('Chủ đề không tồn tại. Hãy chọn chủ đề khác.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(onRetry).toHaveBeenCalledOnce()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('shows empty and loading states accessibly', () => {
    const { rerender } = renderView({
      state: 'empty',
      data: { ...data, entries: [], myEntry: null },
    })
    expect(screen.getByText('Chưa có ai trong kỳ này')).toBeInTheDocument()
    expect(screen.getByText('Bạn chưa có lượt hợp lệ trong kỳ này.')).toBeInTheDocument()
    rerender(
      <MemoryRouter>
        <LeaderboardView
          kind="game"
          title="Game"
          period="week"
          state="loading"
          guest
          onSelect={vi.fn()}
          onPeriodChange={vi.fn()}
          onRetry={vi.fn()}
        />
      </MemoryRouter>,
    )
    expect(screen.getByRole('status', { name: 'Đang tải bảng xếp hạng' })).toBeInTheDocument()
  })
})
