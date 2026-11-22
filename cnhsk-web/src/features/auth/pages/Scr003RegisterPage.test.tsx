import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import { register } from '../api/register'
import { signIn } from '../api/signIn'
import type { CurrentUser } from '../types'
import Scr003RegisterPage from './Scr003RegisterPage'

vi.mock('../api/register', () => ({ register: vi.fn() }))
vi.mock('../api/signIn', () => ({ signIn: vi.fn() }))

const learner: CurrentUser = {
  displayName: 'Nguyễn An',
  email: 'learner@example.com',
  emailVerified: false,
  id: 12,
  roles: ['LEARNER'],
}

function LocationProbe() {
  const location = useLocation()
  return (
    <output>{`${location.pathname}${location.search} ${JSON.stringify(location.state)}`}</output>
  )
}

function renderPage(path = '/register') {
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
          <Route element={<LocationProbe />} path="*" />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  )
  return auth
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
    vi.mocked(signIn).mockReset().mockResolvedValue({ user: learner })
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

  it.each([
    ['/register', '/learn null'],
    ['/register?redirect=%2Flearn%2Ftopics', '/learn/topics null'],
  ])('đăng ký từ %s thì đăng nhập luôn và vào thẳng đích', async (path, destination) => {
    vi.mocked(register).mockResolvedValue({
      email: 'learner@example.com',
      emailVerified: false,
      userId: 12,
    })
    const auth = renderPage(path)
    const user = await enterValidRegistration()

    await user.click(screen.getByRole('button', { name: 'Đăng ký' }))

    expect(await screen.findByText(destination)).toBeVisible()
    expect(register).toHaveBeenCalledWith({
      acceptTerms: true,
      confirmPassword: 'HocTieng1',
      displayName: 'Nguyễn An',
      email: 'learner@example.com',
      password: 'HocTieng1',
    })
    expect(signIn).toHaveBeenCalledWith({ email: 'learner@example.com', password: 'HocTieng1' })
    expect(auth.setAuthenticatedUser).toHaveBeenCalledWith(learner)
  })

  it('khoá nút trong lúc đăng ký và đăng nhập, không gửi hai lần', async () => {
    let finishSignIn!: () => void
    vi.mocked(register).mockResolvedValue({
      email: 'learner@example.com',
      emailVerified: false,
      userId: 12,
    })
    vi.mocked(signIn).mockReturnValue(
      new Promise((resolve) => {
        finishSignIn = () => resolve({ user: learner })
      }),
    )
    renderPage()
    const user = await enterValidRegistration()

    await user.click(screen.getByRole('button', { name: 'Đăng ký' }))
    await waitFor(() => expect(signIn).toHaveBeenCalledOnce())
    expect(screen.getByRole('button', { name: /Đang đăng ký/ })).toBeDisabled()

    finishSignIn()
    expect(await screen.findByText('/learn null')).toBeVisible()
    expect(register).toHaveBeenCalledOnce()
  })

  it('tài khoản đã tạo nhưng chưa đăng nhập được thì mời đăng nhập, không báo lỗi đăng ký', async () => {
    vi.mocked(register).mockResolvedValue({
      email: 'learner@example.com',
      emailVerified: false,
      userId: 12,
    })
    vi.mocked(signIn).mockRejectedValue(
      new ApiError({ errorCode: 'LOGIN_SOURCE_BLOCKED', message: 'x', status: 423 }),
    )
    renderPage('/register?redirect=%2Flearn%2Ftopics')
    const user = await enterValidRegistration()

    await user.click(screen.getByRole('button', { name: 'Đăng ký' }))

    expect(
      await screen.findByText('/login?redirect=%2Flearn%2Ftopics {"registered":true}'),
    ).toBeVisible()
  })

  it('mở điều khoản ở tab khác để không mất nội dung đã nhập', () => {
    renderPage()
    const terms = screen.getByRole('link', { name: 'điều khoản sử dụng và chính sách dữ liệu' })
    expect(terms).toHaveAttribute('href', '/terms')
    expect(terms).toHaveAttribute('target', '_blank')
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
