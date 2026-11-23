import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { LiveSessionContext } from '@/shared/auth/live-session'

import { getNotificationSummary, listNotifications, markNotificationsSeen, type NotificationItem } from './api'
import { NotificationBell } from './NotificationBell'

vi.mock('./api', () => ({
  getNotificationSummary: vi.fn(),
  listNotifications: vi.fn(),
  markNotificationsSeen: vi.fn(),
}))

const item = (overrides: Partial<NotificationItem>): NotificationItem => ({
  actorId: 8,
  actorName: 'Lan',
  at: new Date().toISOString(),
  commentId: null,
  excerpt: null,
  key: 'k',
  postId: 42,
  postTitle: 'Học chữ',
  total: 1,
  type: 'COMMENT',
  unread: true,
  ...overrides,
})

function renderBell(live = true) {
  return render(
    <LiveSessionContext.Provider value={live}>
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>
    </LiveSessionContext.Provider>,
  )
}

describe('NotificationBell', () => {
  beforeEach(() => {
    vi.mocked(getNotificationSummary).mockReset().mockResolvedValue({ unread: 2, tasks: [{ key: 'PENDING_POSTS', count: 3 }] })
    vi.mocked(listNotifications).mockReset()
    vi.mocked(markNotificationsSeen).mockReset().mockResolvedValue(undefined)
  })
  afterEach(() => vi.useRealTimers())

  it('adds unread notifications to pending staff work on the badge', async () => {
    renderBell()
    expect(await screen.findByRole('button', { name: 'Thông báo, 5 mục mới' })).toBeVisible()
    expect(getNotificationSummary).toHaveBeenCalledTimes(1)
  })

  it('opens the latest items, links each to its target and marks unread ones seen', async () => {
    vi.mocked(listNotifications).mockResolvedValue({
      items: [
        item({ key: 'c', type: 'COMMENT', excerpt: 'Hay quá' }),
        item({ key: 'l', type: 'LIKE', total: 3, unread: false }),
        item({ key: 'w', type: 'CONTEST_FINISHED', postId: 5, postTitle: 'Đua chữ', total: 2, commentId: 3 }),
      ],
      tasks: [{ key: 'PENDING_POSTS', count: 3 }],
    })
    renderBell()
    await userEvent.click(await screen.findByRole('button', { name: 'Thông báo, 5 mục mới' }))

    const panel = screen.getByRole('dialog', { name: 'Thông báo' })
    expect(await within(panel).findByRole('link', { name: /Lan đã bình luận bài “Học chữ”/ })).toHaveAttribute(
      'href',
      '/community/posts/42',
    )
    expect(within(panel).getByText('Hay quá')).toBeVisible()
    expect(within(panel).getByRole('link', { name: /Lan và 2 người khác đã thích bài/ })).toBeVisible()
    expect(within(panel).getByRole('link', { name: /Bạn đạt hạng 2 cuộc thi “Đua chữ” và nằm trong top nhận quà/ })).toHaveAttribute(
      'href',
      '/community/contests/5',
    )
    expect(within(within(panel).getByRole('region', { name: 'Việc chờ xử lý' })).getByRole('link', { name: '3 bài viết chờ duyệt' })).toHaveAttribute(
      'href',
      '/moderation/posts',
    )
    expect(within(panel).getAllByLabelText('Chưa đọc')).toHaveLength(2)
    await waitFor(() => expect(markNotificationsSeen).toHaveBeenCalledTimes(1))
    // Only staff work remains on the badge once the notifications are read.
    expect(screen.getByRole('button', { name: 'Thông báo, 3 mục mới' })).toBeVisible()
  })

  it('does not mark anything seen when every item is already read, and shows an empty list', async () => {
    vi.mocked(getNotificationSummary).mockResolvedValue({ unread: 0, tasks: [] })
    vi.mocked(listNotifications).mockResolvedValue({ items: [], tasks: [] })
    renderBell()
    await userEvent.click(await screen.findByRole('button', { name: 'Thông báo' }))
    expect(await screen.findByText('Chưa có thông báo nào.')).toBeVisible()
    expect(markNotificationsSeen).not.toHaveBeenCalled()
  })

  it('reports a failed load inside the panel and closes with Escape', async () => {
    vi.mocked(listNotifications).mockRejectedValue(new Error('offline'))
    renderBell()
    await userEvent.click(await screen.findByRole('button', { name: 'Thông báo, 5 mục mới' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Chưa tải được thông báo.')
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog', { name: 'Thông báo' })).not.toBeInTheDocument()
  })

  it('polls the summary every 30 seconds while the page is visible', async () => {
    vi.useFakeTimers()
    renderBell()
    expect(getNotificationSummary).toHaveBeenCalledTimes(1)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000)
    })
    expect(getNotificationSummary).toHaveBeenCalledTimes(2)
  })

  it('stays offline outside a real session, as in previews', async () => {
    renderBell(false)
    await userEvent.click(screen.getByRole('button', { name: 'Thông báo' }))
    expect(await screen.findByText('Chưa có thông báo nào.')).toBeVisible()
    expect(getNotificationSummary).not.toHaveBeenCalled()
    expect(listNotifications).not.toHaveBeenCalled()
    expect(markNotificationsSeen).not.toHaveBeenCalled()
  })
})
