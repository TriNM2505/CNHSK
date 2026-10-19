import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, Outlet, RouterProvider, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getCurrentUser } from '@/features/auth/api/getCurrentUser'
import { signOutCurrentUser } from '@/features/auth/api/signOutCurrentUser'
import type { CurrentUser } from '@/features/auth/types'
import { ApiError, AUTH_EXPIRED_EVENT } from '@/shared/api'

import { AuthProvider } from './AuthProvider'
import { useCurrentUser } from './useCurrentUser'

vi.mock('@/features/auth/api/getCurrentUser', () => ({
  getCurrentUser: vi.fn(),
}))

vi.mock('@/features/auth/api/signOutCurrentUser', () => ({
  signOutCurrentUser: vi.fn(),
}))

const getCurrentUserMock = vi.mocked(getCurrentUser)
const signOutCurrentUserMock = vi.mocked(signOutCurrentUser)

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
    status: 401,
  })
}

function Probe() {
  const location = useLocation()
  const { reloadCurrentUser, setAuthenticatedUser, signOut, status, user } = useCurrentUser()

  return (
    <main>
      <p>Trạng thái: {status}</p>
      <p>Người dùng: {user?.displayName ?? 'khách'}</p>
      <p>Đường dẫn: {location.pathname}</p>
      <button onClick={() => setAuthenticatedUser(admin)} type="button">
        Đặt người dùng
      </button>
      <button onClick={() => void reloadCurrentUser()} type="button">
        Tải lại
      </button>
      <button onClick={() => void signOut()} type="button">
        Đăng xuất
      </button>
    </main>
  )
}

function TestRoot() {
  return (
    <AuthProvider>
      <Outlet />
    </AuthProvider>
  )
}

function renderProvider(path = '/') {
  const router = createMemoryRouter(
    [
      {
        element: <TestRoot />,
        children: [
          { element: <Probe />, path: '/' },
          { element: <Probe />, path: '/login' },
          { element: <Probe />, handle: { requiresAuthentication: true }, path: '/private' },
        ],
      },
    ],
    { initialEntries: [path] },
  )
  render(<RouterProvider router={router} />)
  return router
}

beforeEach(() => {
  getCurrentUserMock.mockReset()
  signOutCurrentUserMock.mockReset()
})

describe('AuthProvider context actions', () => {
  it('lets a login screen set the authenticated user', async () => {
    getCurrentUserMock.mockRejectedValue(unauthenticatedError())
    renderProvider()
    const user = userEvent.setup()
    await screen.findByText('Trạng thái: guest')

    await user.click(screen.getByRole('button', { name: 'Đặt người dùng' }))

    expect(screen.getByText('Trạng thái: authenticated')).toBeVisible()
    expect(screen.getByText('Người dùng: Quản trị viên')).toBeVisible()
  })

  it('reloads the current user on demand', async () => {
    getCurrentUserMock.mockResolvedValueOnce(learner).mockResolvedValueOnce(admin)
    renderProvider()
    const user = userEvent.setup()
    await screen.findByText('Người dùng: Người học')

    await user.click(screen.getByRole('button', { name: 'Tải lại' }))

    expect(await screen.findByText('Người dùng: Quản trị viên')).toBeVisible()
    expect(getCurrentUserMock).toHaveBeenCalledTimes(2)
  })

  it('clears the client session and returns home even when logout fails', async () => {
    getCurrentUserMock.mockResolvedValue(learner)
    signOutCurrentUserMock.mockRejectedValue(new TypeError('network unavailable'))
    const router = renderProvider('/private')
    const user = userEvent.setup()
    await screen.findByText('Người dùng: Người học')

    await user.click(screen.getByRole('button', { name: 'Đăng xuất' }))

    expect(signOutCurrentUserMock).toHaveBeenCalledOnce()
    expect(await screen.findByText('Trạng thái: guest')).toBeVisible()
    expect(screen.getByText('Người dùng: khách')).toBeVisible()
    expect(router.state.location.pathname).toBe('/')
  })

  it('does not overwrite the forced login destination when logout reports a ban', async () => {
    getCurrentUserMock.mockResolvedValue(learner)
    signOutCurrentUserMock.mockImplementation(async () => {
      window.dispatchEvent(
        new CustomEvent(AUTH_EXPIRED_EVENT, {
          detail: { reason: 'ACCOUNT_BANNED', banReason: 'Vi phạm điều khoản sử dụng' },
        }),
      )
      throw new ApiError({
        errorCode: 'ACCOUNT_BANNED',
        message: 'Tài khoản đã bị cấm.',
        status: 403,
      })
    })
    const router = renderProvider('/private')
    const user = userEvent.setup()
    await screen.findByText('Người dùng: Người học')

    await user.click(screen.getByRole('button', { name: 'Đăng xuất' }))

    expect(router.state.location.pathname).toBe('/login')
  })
})
