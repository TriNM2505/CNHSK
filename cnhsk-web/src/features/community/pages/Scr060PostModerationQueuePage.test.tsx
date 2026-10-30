import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { CurrentUser } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import { getModerationPost, listPendingPosts } from '../api/moderation'
import { formatModerationTimestamp } from '../moderation-list-query'
import { moderationPage } from '../test-fixtures'
import type { ModerationPostPage } from '../types'
import Scr060PostModerationQueuePage from './Scr060PostModerationQueuePage'

const [pendingPost] = moderationPage.items
if (!pendingPost) throw new Error('Post moderation fixture requires a pending post.')

vi.mock('../api/moderation', () => ({ getModerationPost: vi.fn(), listPendingPosts: vi.fn() }))

const moderator: CurrentUser = {
  id: 8,
  displayName: 'Kiểm duyệt',
  email: 'mod@test.invalid',
  emailVerified: true,
  roles: ['MODERATOR'],
}
const auth: AuthContextValue = {
  error: null,
  reloadCurrentUser: vi.fn(async () => undefined),
  setAuthenticatedUser: () => undefined,
  signOut: async () => undefined,
  status: 'authenticated',
  user: moderator,
}

function Location() {
  const location = useLocation()
  return (
    <span aria-label="location">
      {location.pathname}
      {location.search}
    </span>
  )
}
function renderQueue(path = '/moderation/posts', value = auth, state: unknown = null) {
  return render(
    <AuthContext.Provider value={value}>
      <MemoryRouter
        initialEntries={[
          {
            pathname: path.split('?')[0],
            search: path.includes('?') ? `?${path.split('?')[1]}` : '',
            state,
          },
        ]}
      >
        <Routes>
          <Route
            element={
              <>
                <Scr060PostModerationQueuePage />
                <Location />
              </>
            }
            path="/moderation/posts"
          />
          <Route element={<Location />} path="/moderation/posts/:id" />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

describe('SCR-060 post moderation queue', () => {
  beforeEach(() => {
    vi.mocked(listPendingPosts).mockReset().mockResolvedValue(moderationPage)
    vi.mocked(getModerationPost).mockReset()
  })

  it('FR039_requests page zero size20, preserves server ordering and opens summary detail without fetching rows', async () => {
    const user = userEvent.setup()
    vi.mocked(listPendingPosts).mockResolvedValue({
      ...moderationPage,
      items: [
        {
          ...pendingPost,
          id: 92,
          title: 'Đầu từ máy chủ',
          createdAt: '2026-10-30T05:00:00Z',
          updatedAt: '2026-10-31T08:00:00Z',
        },
        {
          ...pendingPost,
          id: 12,
          title: 'Sau từ máy chủ',
          createdAt: '2026-10-29T00:00:00Z',
          updatedAt: '2026-11-02T09:00:00Z',
        },
      ],
      totalElements: 2,
    })
    renderQueue()
    const rows = await screen.findAllByRole('listitem')
    const [first, second] = rows
    if (!first || !second) throw new Error('Expected both pending post rows.')
    expect(within(first).getByText('Đầu từ máy chủ')).toBeVisible()
    expect(within(second).getByText('Sau từ máy chủ')).toBeVisible()
    for (const [row, createdAt, updatedAt] of [
      [first, '2026-10-30T05:00:00Z', '2026-10-31T08:00:00Z'],
      [second, '2026-10-29T00:00:00Z', '2026-11-02T09:00:00Z'],
    ] as const) {
      expect(row.querySelector(`time[datetime="${createdAt}"]`)).toHaveTextContent(formatModerationTimestamp(createdAt))
      expect(row.querySelector(`time[datetime="${updatedAt}"]`)).toHaveTextContent(formatModerationTimestamp(updatedAt))
    }
    expect(listPendingPosts).toHaveBeenCalledWith({ page: 0, size: 20 }, expect.any(AbortSignal))
    expect(within(first).getByText('Tác giả: Người dùng đã rời')).toBeVisible()
    expect(getModerationPost).not.toHaveBeenCalled()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Duyệt$/ })).not.toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: 'Đầu từ máy chủ' }))
    expect(screen.getByLabelText('location')).toHaveTextContent('/moderation/posts/92?page=0')
  })

  it.each(['LEARNER', 'TEACHER', 'CONTENT_EDITOR', 'ACCOUNTANT', 'ADMIN'] as const)(
    'FR060_%s without actual MODERATOR receives no protected data request',
    async (role) => {
      renderQueue('/moderation/posts', { ...auth, user: { ...moderator, roles: [role] } })
      expect(await screen.findByRole('alert')).toBeVisible()
      expect(listPendingPosts).not.toHaveBeenCalled()
    },
  )

  it.each(['guest', 'loading', 'error'] as const)(
    'FR060_auth %s never requests protected queue',
    async (status) => {
      renderQueue('/moderation/posts', { ...auth, status, user: null })
      expect(screen.queryByText(pendingPost.title)).not.toBeInTheDocument()
      expect(listPendingPosts).not.toHaveBeenCalled()
      expect(screen.getByRole(status === 'loading' ? 'status' : 'alert')).toBeVisible()
    },
  )

  it('FR039_uses URL page and one-based shared pagination, then replaces visible rows while new page loads', async () => {
    const user = userEvent.setup()
    let resolve!: (value: ModerationPostPage) => void
    vi.mocked(listPendingPosts)
      .mockResolvedValueOnce({ ...moderationPage, totalElements: 60 })
      .mockReturnValueOnce(
        new Promise((done) => {
          resolve = done
        }),
      )
    renderQueue()
    expect(await screen.findByText('Trang 1 / 3')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Sau' }))
    expect(screen.getByLabelText('location')).toHaveTextContent('?page=1')
    expect(screen.getByRole('status')).toHaveTextContent('Đang tải bài chờ duyệt')
    expect(screen.queryByText(pendingPost.title)).not.toBeInTheDocument()
    await act(async () =>
      resolve({
        ...moderationPage,
        page: 1,
        items: [{ ...pendingPost, id: 43, title: 'Trang thứ hai' }],
        totalElements: 60,
      }),
    )
    expect(await screen.findByText('Trang thứ hai')).toBeVisible()
    expect(screen.getByText('Trang 2 / 3')).toBeVisible()
    expect(listPendingPosts).toHaveBeenLastCalledWith(
      { page: 1, size: 20 },
      expect.any(AbortSignal),
    )
  })

  it.each(['-1', 'abc', '1.0', '9007199254740992', ''])(
    'FR039_invalid URL page %s becomes page0 without sending invalid values',
    async (value) => {
      renderQueue(`/moderation/posts?page=${value}`)
      expect(await screen.findByText(pendingPost.title)).toBeVisible()
      expect(listPendingPosts).toHaveBeenCalledWith({ page: 0, size: 20 }, expect.any(AbortSignal))
      expect(screen.getByLabelText('location')).toHaveTextContent('?page=0')
      expect(listPendingPosts).toHaveBeenCalledTimes(1)
    },
  )

  it('FR060_empty out-of-range page offers return to first and performs a fresh request', async () => {
    const user = userEvent.setup()
    vi.mocked(listPendingPosts)
      .mockResolvedValueOnce({ items: [], page: 7, size: 20, totalElements: 1 })
      .mockResolvedValueOnce(moderationPage)
    renderQueue('/moderation/posts?page=7')
    expect(await screen.findByText('Chưa có bài chờ duyệt')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Về trang đầu' }))
    expect(await screen.findByText(pendingPost.title)).toBeVisible()
    expect(listPendingPosts).toHaveBeenLastCalledWith(
      { page: 0, size: 20 },
      expect.any(AbortSignal),
    )
  })

  it('FR060_network failure is an error with retry, never a fabricated empty queue', async () => {
    const user = userEvent.setup()
    vi.mocked(listPendingPosts)
      .mockRejectedValueOnce(
        new ApiError({ status: 0, errorCode: 'NETWORK_ERROR', message: 'Không thể kết nối.' }),
      )
      .mockResolvedValueOnce(moderationPage)
    renderQueue()
    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể kết nối.')
    expect(screen.queryByText('Chưa có bài chờ duyệt')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText(pendingPost.title)).toBeVisible()
  })

  it('FR060_logout aborts loading and ignores a late protected response', async () => {
    const user = userEvent.setup()
    let resolve!: (value: ModerationPostPage) => void
    vi.mocked(listPendingPosts).mockReturnValue(
      new Promise((done) => {
        resolve = done
      }),
    )
    function Harness() {
      const [value, setValue] = useState(auth)
      return (
        <AuthContext.Provider value={value}>
          <button onClick={() => setValue({ ...auth, status: 'guest', user: null })} type="button">
            Đăng xuất
          </button>
          <Scr060PostModerationQueuePage />
        </AuthContext.Provider>
      )
    }
    render(
      <MemoryRouter>
        <Harness />
      </MemoryRouter>,
    )
    await waitFor(() => expect(listPendingPosts).toHaveBeenCalledOnce())
    const request = vi.mocked(listPendingPosts).mock.calls[0]
    if (!request) throw new Error('Expected a pending post request.')
    const signal = request[1]
    await user.click(screen.getByRole('button', { name: 'Đăng xuất' }))
    expect(signal?.aborted).toBe(true)
    await act(async () => resolve(moderationPage))
    expect(screen.queryByText(pendingPost.title)).not.toBeInTheDocument()
  })

  it('FR039_route page changes ignore a late prior response', async () => {
    const user = userEvent.setup()
    let resolve!: (value: ModerationPostPage) => void
    vi.mocked(listPendingPosts)
      .mockReturnValueOnce(
        new Promise((done) => {
          resolve = done
        }),
      )
      .mockResolvedValueOnce({
        ...moderationPage,
        items: [{ ...pendingPost, title: 'Trang mới' }],
        page: 1,
      })
    function Switch() {
      const navigate = useNavigate()
      return (
        <button onClick={() => navigate('/moderation/posts?page=1')} type="button">
          Trang mới
        </button>
      )
    }
    render(
      <AuthContext.Provider value={auth}>
        <MemoryRouter initialEntries={['/moderation/posts']}>
          <Switch />
          <Scr060PostModerationQueuePage />
        </MemoryRouter>
      </AuthContext.Provider>,
    )
    await user.click(screen.getByRole('button', { name: 'Trang mới' }))
    expect(await screen.findByRole('link', { name: 'Trang mới' })).toBeVisible()
    await act(async () => resolve(moderationPage))
    expect(screen.queryByText(pendingPost.title)).not.toBeInTheDocument()
  })

  it('FR060_conflict flash is shown once, consumed, and queue GET is fresh', async () => {
    renderQueue('/moderation/posts', auth, { moderationFlash: { code: 'postConflict', userId: 8 } })
    expect(await screen.findByText('Bài đã được người khác xử lý')).toBeVisible()
    expect(screen.getAllByText('Bài đã được người khác xử lý')).toHaveLength(1)
    expect(listPendingPosts).toHaveBeenCalledTimes(1)
  })

  it('FR060_does not deliver a flash from a different moderator session', async () => {
    renderQueue('/moderation/posts', auth, {
      moderationFlash: { code: 'postApproved', userId: 99 },
    })
    expect(await screen.findByText(pendingPost.title)).toBeVisible()
    expect(screen.queryByText('Đã duyệt bài viết')).not.toBeInTheDocument()
  })

  it('FR060_auth retry rechecks the session before requesting the queue', async () => {
    const user = userEvent.setup()
    function Harness() {
      const [value, setValue] = useState<AuthContextValue>({
        ...auth,
        status: 'error',
        user: null,
        reloadCurrentUser: async (): Promise<void> => {
          setValue(auth)
        },
      })
      return (
        <AuthContext.Provider value={value}>
          <Scr060PostModerationQueuePage />
        </AuthContext.Provider>
      )
    }
    render(
      <MemoryRouter>
        <Harness />
      </MemoryRouter>,
    )
    expect(listPendingPosts).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Thử lại quyền kiểm duyệt' }))
    expect(await screen.findByText(pendingPost.title)).toBeVisible()
    expect(listPendingPosts).toHaveBeenCalledTimes(1)
  })
})
