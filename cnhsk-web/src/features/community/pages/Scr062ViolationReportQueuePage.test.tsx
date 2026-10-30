import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { CurrentUser } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import { getReportCase, listPendingReportCases } from '../api/moderation'
import { reportPage } from '../test-fixtures'
import type { ReportCasePage } from '../types'
import Scr062ViolationReportQueuePage from './Scr062ViolationReportQueuePage'

const [pendingCase] = reportPage.items
if (!pendingCase) throw new Error('Report moderation fixture requires a pending case.')

vi.mock('../api/moderation', () => ({ getReportCase: vi.fn(), listPendingReportCases: vi.fn() }))

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
function renderQueue(path = '/moderation/reports', value = auth) {
  return render(
    <AuthContext.Provider value={value}>
      <MemoryRouter initialEntries={[path]}>
        <Scr062ViolationReportQueuePage />
        <Location />
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

describe('SCR-062 violation report queue', () => {
  beforeEach(() => {
    vi.mocked(listPendingReportCases).mockReset().mockResolvedValue(reportPage)
    vi.mocked(getReportCase).mockReset()
  })

  it('FR046_preserves server order and shows grouped safe summaries without reporter identity or detail requests', async () => {
    vi.mocked(listPendingReportCases).mockResolvedValue({
      ...reportPage,
      items: [
        {
          ...pendingCase,
          id: 90,
          reportCount: 3,
          targetPreview: '<img src=x onerror=alert(1)>',
        },
        {
          ...pendingCase,
          id: 2,
          reportCount: 15,
          targetType: 'POST',
          targetId: 42,
          targetPreview: 'Bài bị báo cáo',
        },
      ],
      totalElements: 2,
    })
    const { container } = renderQueue()
    const rows = await screen.findAllByRole('listitem')
    const [first, second] = rows
    if (!first || !second) throw new Error('Expected both report case rows.')
    expect(within(first).getByText('Hồ sơ #90')).toBeVisible()
    expect(within(second).getByText('Hồ sơ #2')).toBeVisible()
    expect(screen.getByText('<img src=x onerror=alert(1)>')).toBeVisible()
    expect(container.querySelector('img')).toBeNull()
    expect(screen.getAllByText('Spam, quảng cáo')).toHaveLength(2)
    expect(screen.getByText('Bình luận #11')).toBeVisible()
    expect(screen.getByText('Bài viết #42')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Hồ sơ #90' })).toHaveAttribute(
      'href',
      '/moderation/reports/90?page=0',
    )
    expect(screen.queryByText('Người báo cáo')).not.toBeInTheDocument()
    expect(getReportCase).not.toHaveBeenCalled()
    expect(listPendingReportCases).toHaveBeenCalledWith(
      { page: 0, size: 20 },
      expect.any(AbortSignal),
    )
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
  })

  it.each(['LEARNER', 'TEACHER', 'CONTENT_EDITOR', 'ACCOUNTANT', 'ADMIN'] as const)(
    'FR062_%s without MODERATOR cannot query report cases',
    async (role) => {
      renderQueue('/moderation/reports', { ...auth, user: { ...moderator, roles: [role] } })
      expect(await screen.findByRole('alert')).toBeVisible()
      expect(listPendingReportCases).not.toHaveBeenCalled()
    },
  )

  it.each(['guest', 'loading', 'error'] as const)(
    'FR062_auth %s does not request report cases',
    (status) => {
      renderQueue('/moderation/reports', { ...auth, status, user: null })
      expect(screen.getByRole(status === 'loading' ? 'status' : 'alert')).toBeVisible()
      expect(listPendingReportCases).not.toHaveBeenCalled()
    },
  )

  it('FR062_empty out-of-range page offers first page with URL preservation', async () => {
    const user = userEvent.setup()
    vi.mocked(listPendingReportCases)
      .mockResolvedValueOnce({ items: [], page: 9, size: 20, totalElements: 1 })
      .mockResolvedValueOnce(reportPage)
    renderQueue('/moderation/reports?page=9')
    expect(await screen.findByText('Không có báo cáo chờ xử lý')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Về trang đầu' }))
    expect(await screen.findByRole('link', { name: 'Hồ sơ #9' })).toBeVisible()
    expect(screen.getByLabelText('location')).toHaveTextContent('?page=0')
    expect(listPendingReportCases).toHaveBeenLastCalledWith(
      { page: 0, size: 20 },
      expect.any(AbortSignal),
    )
  })

  it('FR046_paginated requests retain size20 and never expose old page while loading', async () => {
    const user = userEvent.setup()
    let resolve!: (value: ReportCasePage) => void
    vi.mocked(listPendingReportCases)
      .mockResolvedValueOnce({ ...reportPage, totalElements: 41 })
      .mockReturnValueOnce(
        new Promise((done) => {
          resolve = done
        }),
      )
    renderQueue()
    expect(await screen.findByText('Trang 1 / 3')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Sau' }))
    expect(screen.getByRole('status')).toHaveTextContent('Đang tải báo cáo vi phạm')
    expect(screen.queryByText(pendingCase.targetPreview)).not.toBeInTheDocument()
    await act(async () => resolve({ ...reportPage, page: 1, totalElements: 41 }))
    expect(await screen.findByText('Trang 2 / 3')).toBeVisible()
    expect(listPendingReportCases).toHaveBeenLastCalledWith(
      { page: 1, size: 20 },
      expect.any(AbortSignal),
    )
  })

  it.each(['-2', 'text', '1e2', '9007199254740992'])(
    'FR046_rejects invalid page %s before querying',
    async (page) => {
      renderQueue(`/moderation/reports?page=${page}`)
      expect(await screen.findByText(pendingCase.targetPreview)).toBeVisible()
      expect(listPendingReportCases).toHaveBeenCalledTimes(1)
      expect(listPendingReportCases).toHaveBeenCalledWith(
        { page: 0, size: 20 },
        expect.any(AbortSignal),
      )
      expect(screen.getByLabelText('location')).toHaveTextContent('?page=0')
    },
  )

  it.each([403, 500])(
    'FR062_server status%s displays error with retry separately from empty',
    async (status) => {
      const user = userEvent.setup()
      vi.mocked(listPendingReportCases)
        .mockRejectedValueOnce(
          new ApiError({ status, errorCode: 'FORBIDDEN', message: 'Không tải được hồ sơ.' }),
        )
        .mockResolvedValueOnce(reportPage)
      renderQueue()
      expect(await screen.findByRole('alert')).toHaveTextContent('Không tải được hồ sơ.')
      expect(screen.queryByText('Không có báo cáo chờ xử lý')).not.toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: 'Thử lại' }))
      expect(await screen.findByText(pendingCase.targetPreview)).toBeVisible()
    },
  )

  it('FR062_logout ignores an outstanding report case response', async () => {
    const user = userEvent.setup()
    let resolve!: (value: ReportCasePage) => void
    vi.mocked(listPendingReportCases).mockReturnValue(
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
          <Scr062ViolationReportQueuePage />
        </AuthContext.Provider>
      )
    }
    render(
      <MemoryRouter>
        <Harness />
      </MemoryRouter>,
    )
    await waitFor(() => expect(listPendingReportCases).toHaveBeenCalledOnce())
    const request = vi.mocked(listPendingReportCases).mock.calls[0]
    if (!request) throw new Error('Expected a pending report request.')
    const signal = request[1]
    await user.click(screen.getByRole('button', { name: 'Đăng xuất' }))
    expect(signal?.aborted).toBe(true)
    await act(async () => resolve(reportPage))
    expect(screen.queryByText(pendingCase.targetPreview)).not.toBeInTheDocument()
  })

  it('FR046_route changes discard late results from the previous page', async () => {
    const user = userEvent.setup()
    let resolve!: (value: ReportCasePage) => void
    vi.mocked(listPendingReportCases)
      .mockReturnValueOnce(
        new Promise((done) => {
          resolve = done
        }),
      )
      .mockResolvedValueOnce({
        ...reportPage,
        page: 1,
        items: [{ ...pendingCase, targetPreview: 'Nội dung trang mới' }],
      })
    function Switch() {
      const navigate = useNavigate()
      return (
        <button onClick={() => navigate('/moderation/reports?page=1')} type="button">
          Chuyển trang
        </button>
      )
    }
    render(
      <AuthContext.Provider value={auth}>
        <MemoryRouter initialEntries={['/moderation/reports']}>
          <Switch />
          <Scr062ViolationReportQueuePage />
        </MemoryRouter>
      </AuthContext.Provider>,
    )
    await user.click(screen.getByRole('button', { name: 'Chuyển trang' }))
    expect(await screen.findByText('Nội dung trang mới')).toBeVisible()
    await act(async () => resolve(reportPage))
    expect(screen.queryByText(pendingCase.targetPreview)).not.toBeInTheDocument()
  })

  it('FR062_auth retry only requests report cases after authentication succeeds', async () => {
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
          <Scr062ViolationReportQueuePage />
        </AuthContext.Provider>
      )
    }
    render(
      <MemoryRouter>
        <Harness />
      </MemoryRouter>,
    )
    expect(listPendingReportCases).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Thử lại quyền kiểm duyệt' }))
    expect(await screen.findByText(pendingCase.targetPreview)).toBeVisible()
    expect(listPendingReportCases).toHaveBeenCalledTimes(1)
  })
})
