import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import { signIn } from '../api/signIn'
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
