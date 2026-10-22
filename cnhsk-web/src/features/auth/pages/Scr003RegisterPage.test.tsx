import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import { register } from '../api/register'
import { resendVerificationEmail, verifyEmail } from '../api/verifyEmail'
import Scr004VerifyEmailPage from './Scr004VerifyEmailPage'
import Scr003RegisterPage from './Scr003RegisterPage'

vi.mock('../api/register', () => ({ register: vi.fn() }))
vi.mock('../api/verifyEmail', () => ({
  resendVerificationEmail: vi.fn(),
  verifyEmail: vi.fn(),
}))

function VerifyEmailProbe() {
  const location = useLocation()
  return <output>{JSON.stringify(location.state)}</output>
}

function renderPage(path = '/register') {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<Scr003RegisterPage />} path="/register" />
        <Route element={<VerifyEmailProbe />} path="/verify-email" />
      </Routes>
    </MemoryRouter>,
  )
}

function renderRegistrationFlow(path = '/register') {
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
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<Scr003RegisterPage />} path="/register" />
          <Route element={<Scr004VerifyEmailPage />} path="/verify-email" />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

async function enterValidRegistration() {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText(/Tên hiển thị/), '  Nguyễn An  ')
  await user.type(screen.getByLabelText(/Email/), '  Learner@Example.COM ')
  await user.type(screen.getByLabelText(/^Mật khẩu/), 'HocTieng1')
  await user.type(screen.getByLabelText(/Nhập lại mật khẩu/), 'HocTieng1')
  await user.click(screen.getByLabelText(/Tôi đồng ý/))
  return user
}

describe('Scr003RegisterPage', () => {
  beforeEach(() => {
    vi.mocked(register).mockReset()
    vi.mocked(resendVerificationEmail).mockReset()
    vi.mocked(verifyEmail).mockReset()
  })

  it('validate phía client và không gọi API khi form chưa hợp lệ', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: 'Đăng ký' }))

    expect(await screen.findByText('Tên hiển thị cần từ 2–100 ký tự.')).toBeVisible()
    expect(screen.getByText('Vui lòng nhập email.')).toBeVisible()
    expect(screen.getAllByText('Mật khẩu cần 8–72 ký tự, gồm chữ và số.')).toHaveLength(2)
    expect(screen.getByText('Bạn cần đồng ý với điều khoản để đăng ký.')).toBeVisible()
    expect(register).not.toHaveBeenCalled()
  })

  it('gửi đủ dữ liệu đã chuẩn hoá và chuyển sang xác thực email', async () => {
    vi.mocked(register).mockResolvedValue({
      email: 'learner@example.com',
      emailVerified: false,
      userId: 12,
    })
    renderPage('/register?redirect=%2Flearn%2Ftopics')
    const user = await enterValidRegistration()

    await user.click(screen.getByRole('button', { name: 'Đăng ký' }))

    await waitFor(() =>
      expect(register).toHaveBeenCalledWith({
        acceptTerms: true,
        confirmPassword: 'HocTieng1',
        displayName: 'Nguyễn An',
        email: 'learner@example.com',
        password: 'HocTieng1',
      }),
    )
    expect(await screen.findByText(/"email":"learner@example.com"/)).toHaveTextContent(
      '"redirect":"/learn/topics"',
    )
  })

  it('AT-71 / FR-087: đăng ký thành công chuyển tới SCR-004 với email và redirect, chưa tự gửi lại', async () => {
    vi.mocked(register).mockResolvedValue({
      email: 'learner@example.com',
      emailVerified: false,
      userId: 12,
    })
    renderRegistrationFlow('/register?redirect=%2Flearn%2Ftopics')
    const user = await enterValidRegistration()

    await user.click(screen.getByRole('button', { name: 'Đăng ký' }))

    expect(await screen.findByText('learner@example.com')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Bỏ qua, vào học' })).toHaveAttribute(
      'href',
      '/login?redirect=%2Flearn%2Ftopics',
    )
    expect(verifyEmail).not.toHaveBeenCalled()
    expect(resendVerificationEmail).not.toHaveBeenCalled()
  })

  it('gắn lỗi 409 vào email và giữ redirect trên link đăng nhập', async () => {
    vi.mocked(register).mockRejectedValue(
      new ApiError({
        errorCode: 'EMAIL_ALREADY_EXISTS',
        message: 'technical duplicate details',
        status: 409,
      }),
    )
    renderPage('/register?redirect=%2Flearn%2Ftopics')
    const user = await enterValidRegistration()

    await user.click(screen.getByRole('button', { name: 'Đăng ký' }))

    expect(await screen.findByText('Email này đã được sử dụng.')).toBeVisible()
    expect(screen.getAllByRole('link', { name: 'Đăng nhập' })[0]).toHaveAttribute(
      'href',
      '/login?redirect=%2Flearn%2Ftopics',
    )
  })

  it('hiện lỗi field 422 và bỏ qua field kỹ thuật', async () => {
    vi.mocked(register).mockRejectedValue(
      new ApiError({
        details: [
          { field: 'displayName', message: 'Tên hiển thị chưa hợp lệ.' },
          { field: 'internal', message: 'Stack trace không được hiển thị.' },
        ],
        errorCode: 'VALIDATION_FAILED',
        message: 'technical validation details',
        status: 422,
      }),
    )
    renderPage()
    const user = await enterValidRegistration()

    await user.click(screen.getByRole('button', { name: 'Đăng ký' }))

    expect(await screen.findByText('Tên hiển thị chưa hợp lệ.')).toBeVisible()
    expect(screen.queryByText('Stack trace không được hiển thị.')).not.toBeInTheDocument()
  })
})
