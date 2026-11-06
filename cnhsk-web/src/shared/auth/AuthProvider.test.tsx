import { act, cleanup, render, screen } from '@testing-library/react'
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

const privateSessionKeys = [
  'cnhsk:private-session:scr041:1:new',
  'cnhsk:private-session:another-feature:1:42',
] as const
const unrelatedSessionKey = 'cnhsk:preferences'
const similarSessionKey = 'cnhsk:private-session-other:1:new'

function seedPrivateSessionStorage() {
  for (const key of privateSessionKeys) sessionStorage.setItem(key, 'private recovery data')
  sessionStorage.setItem(unrelatedSessionKey, 'keep preferences')
  sessionStorage.setItem(similarSessionKey, 'keep similar prefix')
  localStorage.setItem(privateSessionKeys[0], 'keep local storage')
}

function expectUnrelatedStorageRetained() {
  expect(sessionStorage.getItem(unrelatedSessionKey)).toBe('keep preferences')
  expect(sessionStorage.getItem(similarSessionKey)).toBe('keep similar prefix')
  expect(localStorage.getItem(privateSessionKeys[0])).toBe('keep local storage')
}

function expectPrivateSessionStorageRetained() {
  for (const key of privateSessionKeys) {
    expect(sessionStorage.getItem(key)).toBe('private recovery data')
  }
  expectUnrelatedStorageRetained()
}

function expectOnlyPrivateSessionStorageCleared() {
  for (const key of privateSessionKeys) expect(sessionStorage.getItem(key)).toBeNull()
  expectUnrelatedStorageRetained()
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
  sessionStorage.clear()
  localStorage.clear()
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
    seedPrivateSessionStorage()

    await user.click(screen.getByRole('button', { name: 'Đăng xuất' }))

    expect(signOutCurrentUserMock).toHaveBeenCalledOnce()
    expect(await screen.findByText('Trạng thái: guest')).toBeVisible()
    expect(screen.getByText('Người dùng: khách')).toBeVisible()
    expect(router.state.location.pathname).toBe('/')
    expectOnlyPrivateSessionStorageCleared()
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

describe('AuthProvider private session storage', () => {
  it('clears only private session keys after explicit logout succeeds', async () => {
    getCurrentUserMock.mockResolvedValue(learner)
    signOutCurrentUserMock.mockResolvedValue(undefined)
    renderProvider('/private')
    const user = userEvent.setup()
    await screen.findByText('Người dùng: Người học')
    seedPrivateSessionStorage()

    await user.click(screen.getByRole('button', { name: 'Đăng xuất' }))

    await screen.findByText('Trạng thái: guest')
    expectOnlyPrivateSessionStorageCleared()
  })

  it('clears only private session keys when login replaces an authenticated user', async () => {
    getCurrentUserMock.mockResolvedValue(learner)
    renderProvider()
    const user = userEvent.setup()
    await screen.findByText('Người dùng: Người học')
    seedPrivateSessionStorage()

    await user.click(screen.getByRole('button', { name: 'Đặt người dùng' }))

    expect(screen.getByText('Người dùng: Quản trị viên')).toBeVisible()
    expectOnlyPrivateSessionStorageCleared()
  })

  it('preserves the previous identity through loading and clears when reload returns another user', async () => {
    let resolveReload!: (nextUser: CurrentUser) => void
    const reloadedUser = new Promise<CurrentUser>((resolve) => {
      resolveReload = resolve
    })
    getCurrentUserMock.mockResolvedValueOnce(learner).mockReturnValueOnce(reloadedUser)
    renderProvider()
    const user = userEvent.setup()
    await screen.findByText('Người dùng: Người học')
    seedPrivateSessionStorage()

    await user.click(screen.getByRole('button', { name: 'Tải lại' }))

    expect(screen.getByText('Trạng thái: loading')).toBeVisible()
    expect(screen.getByText('Người dùng: khách')).toBeVisible()
    expectPrivateSessionStorageRetained()

    await act(async () => resolveReload(admin))

    expect(screen.getByText('Người dùng: Quản trị viên')).toBeVisible()
    expectOnlyPrivateSessionStorageCleared()
  })

  it('retains private session keys during first hydration', async () => {
    let resolveProfile!: (nextUser: CurrentUser) => void
    const currentUser = new Promise<CurrentUser>((resolve) => {
      resolveProfile = resolve
    })
    getCurrentUserMock.mockReturnValue(currentUser)
    seedPrivateSessionStorage()
    renderProvider()

    expect(screen.getByText('Trạng thái: loading')).toBeVisible()
    expectPrivateSessionStorageRetained()

    await act(async () => resolveProfile(learner))

    expect(screen.getByText('Người dùng: Người học')).toBeVisible()
    expectPrivateSessionStorageRetained()
  })

  it('retains private session keys when the first authenticated user follows a guest response', async () => {
    getCurrentUserMock.mockRejectedValue(unauthenticatedError())
    seedPrivateSessionStorage()
    renderProvider()
    const user = userEvent.setup()
    await screen.findByText('Trạng thái: guest')
    expectPrivateSessionStorageRetained()

    await user.click(screen.getByRole('button', { name: 'Đặt người dùng' }))

    expect(screen.getByText('Người dùng: Quản trị viên')).toBeVisible()
    expectPrivateSessionStorageRetained()
  })

  it('retains private session keys when the provider unmounts and hydrates again after a page reload', async () => {
    getCurrentUserMock.mockResolvedValue(learner)
    renderProvider()
    await screen.findByText('Người dùng: Người học')
    seedPrivateSessionStorage()

    cleanup()
    expectPrivateSessionStorageRetained()
    renderProvider()

    await screen.findByText('Người dùng: Người học')
    expectPrivateSessionStorageRetained()
  })

  it('retains private session keys when login sets the same authenticated user', async () => {
    getCurrentUserMock.mockResolvedValue(admin)
    renderProvider()
    const user = userEvent.setup()
    await screen.findByText('Người dùng: Quản trị viên')
    seedPrivateSessionStorage()

    await user.click(screen.getByRole('button', { name: 'Đặt người dùng' }))

    expectPrivateSessionStorageRetained()
  })

  it('retains private session keys while reloading the same user with a changed profile', async () => {
    let resolveReload!: (nextUser: CurrentUser) => void
    const reloadedUser = new Promise<CurrentUser>((resolve) => {
      resolveReload = resolve
    })
    getCurrentUserMock.mockResolvedValueOnce(learner).mockReturnValueOnce(reloadedUser)
    renderProvider()
    const user = userEvent.setup()
    await screen.findByText('Người dùng: Người học')
    seedPrivateSessionStorage()

    await user.click(screen.getByRole('button', { name: 'Tải lại' }))

    expect(screen.getByText('Trạng thái: loading')).toBeVisible()
    expectPrivateSessionStorageRetained()

    await act(async () => resolveReload({ ...learner, displayName: 'Tên mới' }))

    expect(screen.getByText('Người dùng: Tên mới')).toBeVisible()
    expectPrivateSessionStorageRetained()
  })

  it('retains private session keys when a profile reload returns unauthenticated', async () => {
    getCurrentUserMock.mockResolvedValueOnce(learner).mockRejectedValueOnce(unauthenticatedError())
    renderProvider()
    const user = userEvent.setup()
    await screen.findByText('Người dùng: Người học')
    seedPrivateSessionStorage()

    await user.click(screen.getByRole('button', { name: 'Tải lại' }))

    await screen.findByText('Trạng thái: guest')
    expectPrivateSessionStorageRetained()
  })

  it.each([
    { name: 'expiry', detail: undefined },
    { name: 'ban', detail: { reason: 'ACCOUNT_BANNED' } },
  ])('retains private session keys on $name events', async ({ detail }) => {
    getCurrentUserMock.mockResolvedValue(learner)
    renderProvider('/private')
    await screen.findByText('Người dùng: Người học')
    seedPrivateSessionStorage()

    act(() => {
      window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT, { detail }))
    })

    await screen.findByText('Trạng thái: guest')
    expectPrivateSessionStorageRetained()
  })
})
