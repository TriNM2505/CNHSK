import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import { signIn } from '../api/signIn'
import Scr001LandingPage from './Scr001LandingPage'
import type { CurrentUser } from '../types'
import Scr002SignInPage from './Scr002SignInPage'

vi.mock('../api/signIn', () => ({ signIn: vi.fn() }))

const learner: CurrentUser = {
  displayName: 'Người học',
  email: 'learner@example.com',
  emailVerified: true,
  id: 1,
  roles: ['LEARNER'],
}

function renderPage(initialEntry: string | { pathname: string; state?: unknown } = '/login') {
  const auth: AuthContextValue = {
    error: null,
    reloadCurrentUser: vi.fn(async () => undefined),
    setAuthenticatedUser: vi.fn(),
    signOut: vi.fn(async () => undefined),
    status: 'guest',
    user: null,
  }

  render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route element={<Scr002SignInPage />} path="/login" />
          <Route element={<h1>Danh sách chủ đề</h1>} path="/learn/topics" />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  )

  return auth
}

function renderSignInFlow(initialEntry = '/login') {
  function AuthenticationFlow() {
    const [user, setUser] = useState<CurrentUser | null>(null)
    const auth: AuthContextValue = {
      error: null,
      reloadCurrentUser: vi.fn(async () => undefined),
      setAuthenticatedUser: setUser,
      signOut: vi.fn(async () => undefined),
      status: user ? 'authenticated' : 'guest',
      user,
    }

    return (
      <AuthContext.Provider value={auth}>
        <MemoryRouter initialEntries={[initialEntry]}>
          <Routes>
            <Route element={<Scr002SignInPage />} path="/login" />
            <Route element={<Scr001LandingPage />} path="/" />
            <Route element={<h1>Khu vực quản trị</h1>} path="/admin/users" />
            <Route element={<h1>Danh sách chủ đề</h1>} path="/learn/topics" />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>
    )
  }

  render(<AuthenticationFlow />)
}

async function enterCredentials() {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText(/Email/), 'Learner@Example.COM')
  await user.type(screen.getByLabelText(/Mật khẩu/), 'abc')
  return user
}

describe('Scr002SignInPage', () => {
  beforeEach(() => {
    vi.mocked(signIn).mockReset()
  })

  it('hiện thông báo thành công khi vừa đặt lại mật khẩu (từ SCR-006)', () => {
    renderPage({ pathname: '/login', state: { passwordReset: true } })

    expect(screen.getByRole('status')).toHaveTextContent('Đã đặt lại mật khẩu')
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('đọc navigation state ACCOUNT_BANNED ngay khi mở màn', () => {
    renderPage({
      pathname: '/login',
      state: { authNotice: { banReason: 'Vi phạm nội quy.', reason: 'ACCOUNT_BANNED' } },
    })

    expect(screen.getByRole('alert')).toHaveTextContent('Tài khoản của bạn đã bị cấm.')
    expect(screen.getByRole('alert')).toHaveTextContent('Vi phạm nội quy.')
  })

  it('validate phía client và không gọi API khi form chưa hợp lệ', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: 'Đăng nhập' }))

    expect(await screen.findByText('Vui lòng nhập email.')).toBeVisible()
    expect(screen.getByText('Vui lòng nhập mật khẩu.')).toBeVisible()
    expect(signIn).not.toHaveBeenCalled()
  })

  it('cập nhật user rồi điều hướng sau khi đăng nhập thành công', async () => {
    vi.mocked(signIn).mockResolvedValue({ user: learner })
    const auth = renderPage()
    const user = await enterCredentials()

    await user.click(screen.getByRole('button', { name: 'Đăng nhập' }))

    expect(await screen.findByRole('heading', { name: 'Danh sách chủ đề' })).toBeVisible()
    expect(signIn).toHaveBeenCalledWith({ email: 'learner@example.com', password: 'abc' })
    expect(auth.setAuthenticatedUser).toHaveBeenCalledWith(learner)
  })

  it('AT-72: đăng nhập ADMIN duy nhất đi thẳng tới khu vực quản trị', async () => {
    vi.mocked(signIn).mockResolvedValue({ user: { ...learner, roles: ['ADMIN'] } })
    renderSignInFlow()
    const user = await enterCredentials()

    await user.click(screen.getByRole('button', { name: 'Đăng nhập' }))

    expect(await screen.findByRole('heading', { name: 'Khu vực quản trị' })).toBeVisible()
  })

  it('AT-72: đăng nhập nhiều role mở SCR-001 để người dùng tự chọn khu vực', async () => {
    vi.mocked(signIn).mockResolvedValue({ user: { ...learner, roles: ['LEARNER', 'TEACHER'] } })
    renderSignInFlow()
    const user = await enterCredentials()

    await user.click(screen.getByRole('button', { name: 'Đăng nhập' }))

    expect(await screen.findByRole('heading', { name: 'Khu vực của bạn' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Học tập' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Giáo viên' })).toBeVisible()
  })

  it('AT-72: bỏ redirect nội bộ không được phép và dùng khu vực mặc định', async () => {
    vi.mocked(signIn).mockResolvedValue({ user: learner })
    renderSignInFlow('/login?redirect=%2Fadmin%2Fusers')
    const user = await enterCredentials()

    await user.click(screen.getByRole('button', { name: 'Đăng nhập' }))

    expect(await screen.findByRole('heading', { name: 'Danh sách chủ đề' })).toBeVisible()
  })

  it('hiện thông báo chung khi API trả 401', async () => {
    vi.mocked(signIn).mockRejectedValue(
      new ApiError({
        errorCode: 'INVALID_CREDENTIALS',
        message: 'Thông tin kỹ thuật không dùng',
        status: 401,
      }),
    )
    renderPage()
    const user = await enterCredentials()

    await user.click(screen.getByRole('button', { name: 'Đăng nhập' }))

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('Email hoặc mật khẩu không đúng'),
    )
  })
})
