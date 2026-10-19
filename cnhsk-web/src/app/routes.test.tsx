import { act, render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { appRoutes } from '@/app/routes'
import { getCurrentUser } from '@/features/auth/api/getCurrentUser'
import type { CurrentUser } from '@/features/auth/types'
import { ApiError, AUTH_EXPIRED_EVENT } from '@/shared/api'

vi.mock('@/features/auth/api/getCurrentUser', () => ({
  getCurrentUser: vi.fn(),
}))

vi.mock('@/features/auth/api/signOutCurrentUser', () => ({
  signOutCurrentUser: vi.fn(),
}))

const getCurrentUserMock = vi.mocked(getCurrentUser)

const learner: CurrentUser = {
  id: 1,
  email: 'learner@example.com',
  displayName: 'Người học',
  emailVerified: true,
  roles: ['LEARNER'],
}

const admin: CurrentUser = {
  ...learner,
  id: 2,
  email: 'admin@example.com',
  displayName: 'Quản trị viên',
  roles: ['ADMIN'],
}

function unauthenticatedError() {
  return new ApiError({
    errorCode: 'UNAUTHENTICATED',
    message: 'Bạn chưa đăng nhập.',
    requestId: 'req-auth',
    status: 401,
  })
}

function renderRoute(path: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] })
  const result = render(<RouterProvider router={router} />)

  return { ...result, router }
}

beforeEach(() => {
  getCurrentUserMock.mockRejectedValue(unauthenticatedError())
})

describe('application routes', () => {
  it('renders the landing page at the root route', async () => {
    renderRoute('/')

    expect(
      await screen.findByRole('heading', { name: 'Học tiếng Trung rõ ràng, từng bước một' }),
    ).toBeVisible()
  })

  it('renders the not-found page for an unknown route', () => {
    renderRoute('/khong-ton-tai')

    expect(screen.getByRole('heading', { name: 'Không tìm thấy trang' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Về trang chủ' })).toHaveAttribute('href', '/')
  })

  it('sends a guest from a learner route to login with the full redirect', async () => {
    const { router } = renderRoute('/learn/topics?level=2#dang-hoc')

    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/login')
    expect(new URLSearchParams(router.state.location.search).get('redirect')).toBe(
      '/learn/topics?level=2#dang-hoc',
    )
  })

  it('sends a learner without ADMIN role to the access-denied screen', async () => {
    getCurrentUserMock.mockResolvedValue(learner)
    const { router } = renderRoute('/admin/users')

    expect(await screen.findByRole('heading', { name: 'Không có quyền truy cập' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/403')
  })

  it('allows an administrator to open the user list', async () => {
    getCurrentUserMock.mockResolvedValue(admin)
    renderRoute('/admin/users')

    expect(await screen.findByRole('heading', { name: 'Màn SCR-076 đang dựng' })).toBeVisible()
  })

  it('does not infer LEARNER access from the ADMIN role', async () => {
    getCurrentUserMock.mockResolvedValue(admin)
    renderRoute('/me/profile')

    expect(await screen.findByRole('heading', { name: 'Không có quyền truy cập' })).toBeVisible()
  })

  it('allows any authenticated account to open the password screen', async () => {
    getCurrentUserMock.mockResolvedValue({ ...learner, roles: [] })
    renderRoute('/me/password')

    expect(await screen.findByRole('heading', { name: 'Đổi mật khẩu' })).toBeVisible()
    expect(screen.queryByRole('navigation', { name: 'Học tập' })).not.toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'Quản trị' })).not.toBeInTheDocument()
  })

  it('uses the learner layout for the password screen when LEARNER is present', async () => {
    getCurrentUserMock.mockResolvedValue(learner)
    renderRoute('/me/password')

    expect(await screen.findByRole('navigation', { name: 'Học tập' })).toBeInTheDocument()
  })

  it('uses the admin layout for the password screen when only ADMIN is present', async () => {
    getCurrentUserMock.mockResolvedValue(admin)
    renderRoute('/me/password')

    expect(await screen.findByRole('navigation', { name: 'Quản trị' })).toBeInTheDocument()
  })

  it('moves an authenticated user away from a guest-only route using FR-090', async () => {
    getCurrentUserMock.mockResolvedValue(admin)
    const { router } = renderRoute('/login')

    expect(await screen.findByRole('heading', { name: 'Màn SCR-076 đang dựng' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/admin/users')
  })

  it('does not flash the login screen while the current user request is pending', async () => {
    let resolveUser: ((user: CurrentUser) => void) | undefined
    getCurrentUserMock.mockReturnValue(
      new Promise<CurrentUser>((resolve) => {
        resolveUser = resolve
      }),
    )
    renderRoute('/me/profile')

    expect(screen.queryByRole('heading', { name: 'Màn SCR-002 đang dựng' })).not.toBeInTheDocument()

    await act(async () => resolveUser?.(learner))
    expect(await screen.findByRole('heading', { name: 'Thông tin cá nhân' })).toBeVisible()
  })

  it.each([
    ['/learn/topics/topic-1/study', 'Màn SCR-012 đang dựng'],
    ['/learn/topics/topic-1/practice/recognition', 'Màn SCR-012 đang dựng'],
    ['/learn/topics/topic-1/practice/listening', 'Màn SCR-012 đang dựng'],
    ['/learn/topics/topic-1/test', 'Màn SCR-012 đang dựng'],
    ['/learn/pronunciation/stages/3', 'Màn SCR-016 đang dựng'],
    ['/learn/characters/writing/lexeme-1', 'Màn SCR-014 đang dựng'],
    ['/learn/characters/writing/challenge', 'Màn SCR-014 đang dựng'],
  ])('maps child route %s to the correct screen', async (path, heading) => {
    getCurrentUserMock.mockResolvedValue(learner)
    renderRoute(path)

    expect(await screen.findByRole('heading', { name: heading })).toBeVisible()
  })

  it('moves an expired protected session to login', async () => {
    getCurrentUserMock.mockResolvedValue(learner)
    const { router } = renderRoute('/learn/grammar?level=2#lesson-list')
    await screen.findByRole('heading', { name: 'Màn SCR-017 đang dựng' })

    act(() => window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT)))

    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeVisible()
    expect(new URLSearchParams(router.state.location.search).get('redirect')).toBe(
      '/learn/grammar?level=2#lesson-list',
    )
  })

  it('keeps a public route open when the session expires', async () => {
    // Không phụ thuộc màn đã dựng hay chưa: chỉ kiểm route công khai không bị đẩy sang /login.
    getCurrentUserMock.mockResolvedValue(learner)
    const { router } = renderRoute('/reset-password?token=sample')
    await waitFor(() => expect(getCurrentUserMock).toHaveBeenCalled())
    await screen.findByRole('main')

    act(() => window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT)))

    expect(router.state.location.pathname).toBe('/reset-password')
    expect(router.state.location.search).toBe('?token=sample')
  })

  it('always moves a banned account to login with a safe notice', async () => {
    getCurrentUserMock.mockResolvedValue(learner)
    const { router } = renderRoute('/')
    await screen.findByRole('heading', { name: 'Học tiếng Trung rõ ràng, từng bước một' })

    act(() =>
      window.dispatchEvent(
        new CustomEvent(AUTH_EXPIRED_EVENT, {
          detail: { reason: 'ACCOUNT_BANNED', banReason: 'Vi phạm điều khoản sử dụng' },
        }),
      ),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('Tài khoản của bạn đã bị cấm.')
    expect(screen.getByRole('alert')).toHaveTextContent('Lý do: Vi phạm điều khoản sử dụng')
    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.location.search).toBe('')
  })
})
