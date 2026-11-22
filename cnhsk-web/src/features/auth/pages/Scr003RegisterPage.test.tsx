import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import { register, sendRegisterCode } from '../api/register'
import { signIn } from '../api/signIn'
import type { CurrentUser } from '../types'
import Scr003RegisterPage from './Scr003RegisterPage'

vi.mock('../api/register', () => ({ register: vi.fn(), sendRegisterCode: vi.fn() }))
vi.mock('../api/signIn', () => ({ signIn: vi.fn() }))

const learner: CurrentUser = {
  displayName: 'Nguyễn An',
  email: 'learner@example.com',
  emailVerified: true,
  id: 12,
  roles: ['LEARNER'],
}

const request = {
  acceptTerms: true,
  confirmPassword: 'HocTieng1',
  displayName: 'Nguyễn An',
  email: 'learner@example.com',
  password: 'HocTieng1',
}

const sent = { email: 'learner@example.com', expiresInSeconds: 600, resendAfterSeconds: 60 }

function tooManyRequests(retryAfterSeconds: number) {
  return new ApiError({
    errorCode: 'TOO_MANY_REQUESTS',
    extra: { retryAfterSeconds },
    message: 'x',
    status: 429,
  })
}

const notSent = new ApiError({ errorCode: 'EMAIL_NOT_SENT', message: 'x', status: 503 })
const notSentMessage = 'Chưa gửi được mã tới email này. Vui lòng kiểm tra địa chỉ rồi thử lại.'

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

async function openCodeStep() {
  const user = await enterValidRegistration()
  await user.click(screen.getByRole('button', { name: 'Đăng ký' }))
  await screen.findByLabelText(/Mã xác minh/)
  return user
}

describe('Scr003RegisterPage', () => {
  beforeEach(() => {
    vi.mocked(sendRegisterCode).mockReset().mockResolvedValue(sent)
    vi.mocked(register)
      .mockReset()
      .mockResolvedValue({ email: 'learner@example.com', emailVerified: true, userId: 12 })
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
  ])('đăng ký từ %s: nhập đúng mã rồi mới tạo tài khoản và vào thẳng đích', async (path, destination) => {
    const auth = renderPage(path)
    const user = await openCodeStep()

    expect(sendRegisterCode).toHaveBeenCalledWith(request)
    expect(register).not.toHaveBeenCalled()
    expect(screen.getByText('Mã 6 số đã được gửi tới learner@example.com.')).toBeVisible()
    expect(screen.getByLabelText(/Mã xác minh/)).toHaveFocus()

    await user.type(screen.getByLabelText(/Mã xác minh/), '123 456')
    await user.click(screen.getByRole('button', { name: 'Xác minh và vào học' }))

    expect(await screen.findByText(destination)).toBeVisible()
    expect(register).toHaveBeenCalledWith({ ...request, code: '123456' })
    expect(signIn).toHaveBeenCalledWith({ email: 'learner@example.com', password: 'HocTieng1' })
    expect(auth.setAuthenticatedUser).toHaveBeenCalledWith(learner)
  })

  it('chưa đủ 6 số thì không gọi máy chủ', async () => {
    renderPage()
    const user = await openCodeStep()

    await user.type(screen.getByLabelText(/Mã xác minh/), '12ab')
    await user.click(screen.getByRole('button', { name: 'Xác minh và vào học' }))

    expect(await screen.findByText('Vui lòng nhập đủ 6 số.')).toBeVisible()
    expect(register).not.toHaveBeenCalled()
  })

  it.each([
    [4, 'Mã chưa đúng. Bạn còn 4 lần thử.'],
    [0, 'Mã đã hết hiệu lực. Vui lòng gửi lại mã mới.'],
  ])('mã bị từ chối, còn %s lần thử', async (remainingAttempts, message) => {
    vi.mocked(register).mockRejectedValue(
      new ApiError({
        errorCode: 'INVALID_VERIFY_TOKEN',
        extra: { remainingAttempts },
        message: 'x',
        status: 400,
      }),
    )
    renderPage()
    const user = await openCodeStep()

    await user.type(screen.getByLabelText(/Mã xác minh/), '000000')
    await user.click(screen.getByRole('button', { name: 'Xác minh và vào học' }))

    expect(await screen.findByText(message)).toBeVisible()
    expect(signIn).not.toHaveBeenCalled()
  })

  it('đếm ngược hết mới cho gửi lại mã', async () => {
    vi.mocked(sendRegisterCode).mockResolvedValue({ ...sent, resendAfterSeconds: 1 })
    renderPage()
    const user = await openCodeStep()

    expect(screen.getByRole('button', { name: 'Gửi lại mã sau 1 giây' })).toBeDisabled()
    await user.click(await screen.findByRole('button', { name: 'Gửi lại mã' }))

    expect(await screen.findByText(/Đã gửi mã mới/)).toBeVisible()
    expect(sendRegisterCode).toHaveBeenCalledTimes(2)
  })

  it('không gửi được mã thì ở lại màn đăng ký và cho thử lại ngay', async () => {
    vi.mocked(sendRegisterCode).mockRejectedValueOnce(notSent)
    renderPage()
    const user = await enterValidRegistration()

    await user.click(screen.getByRole('button', { name: 'Đăng ký' }))
    expect(await screen.findByText(notSentMessage)).toBeVisible()
    expect(screen.queryByLabelText(/Mã xác minh/)).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Đăng ký' }))
    expect(await screen.findByLabelText(/Mã xác minh/)).toBeVisible()
  })

  it('gửi lại không được thì giữ mã đang nhập và cho gửi lại ngay', async () => {
    vi.mocked(sendRegisterCode)
      .mockResolvedValueOnce({ ...sent, resendAfterSeconds: 1 })
      .mockRejectedValueOnce(notSent)
    renderPage()
    const user = await openCodeStep()
    await user.type(screen.getByLabelText(/Mã xác minh/), '1234')

    await user.click(await screen.findByRole('button', { name: 'Gửi lại mã' }))

    expect(await screen.findByText(notSentMessage)).toBeVisible()
    expect(screen.queryByText(/Đã gửi mã mới/)).not.toBeInTheDocument()
    expect(screen.getByLabelText(/Mã xác minh/)).toHaveValue('1234')
    expect(screen.getByRole('button', { name: 'Gửi lại mã' })).toBeEnabled()
  })

  it('sửa thông tin thì giữ nguyên nội dung đã nhập', async () => {
    renderPage()
    const user = await openCodeStep()

    await user.click(screen.getByRole('button', { name: 'Sửa thông tin' }))

    expect(screen.getByLabelText(/Email/)).toHaveValue('Learner@Example.COM')
    expect(screen.getByLabelText(/^Mật khẩu/)).toHaveValue('HocTieng1')
  })

  it('mã vừa gửi chưa quá một phút thì cho nhập luôn', async () => {
    vi.mocked(sendRegisterCode).mockRejectedValue(tooManyRequests(42))
    renderPage()
    await openCodeStep()

    expect(screen.getByRole('button', { name: /Gửi lại mã sau 4[12] giây/ })).toBeDisabled()
  })

  it('yêu cầu quá nhiều mã thì báo thời gian chờ ở màn đăng ký', async () => {
    vi.mocked(sendRegisterCode).mockRejectedValue(tooManyRequests(1800))
    renderPage()
    const user = await enterValidRegistration()

    await user.click(screen.getByRole('button', { name: 'Đăng ký' }))

    expect(await screen.findByText(/thử lại sau 30 phút/)).toBeVisible()
  })

  it('khoá nút trong lúc tạo tài khoản và đăng nhập, không gửi hai lần', async () => {
    let finishSignIn!: () => void
    vi.mocked(signIn).mockReturnValue(
      new Promise((resolve) => {
        finishSignIn = () => resolve({ user: learner })
      }),
    )
    renderPage()
    const user = await openCodeStep()

    await user.type(screen.getByLabelText(/Mã xác minh/), '123456')
    await user.click(screen.getByRole('button', { name: 'Xác minh và vào học' }))
    await waitFor(() => expect(signIn).toHaveBeenCalledOnce())
    expect(screen.getByRole('button', { name: 'Đang tạo tài khoản' })).toBeDisabled()

    finishSignIn()
    expect(await screen.findByText('/learn null')).toBeVisible()
    expect(register).toHaveBeenCalledOnce()
  })

  it('tài khoản đã tạo nhưng chưa đăng nhập được thì mời đăng nhập, không báo lỗi đăng ký', async () => {
    vi.mocked(signIn).mockRejectedValue(
      new ApiError({ errorCode: 'LOGIN_SOURCE_BLOCKED', message: 'x', status: 423 }),
    )
    renderPage('/register?redirect=%2Flearn%2Ftopics')
    const user = await openCodeStep()

    await user.type(screen.getByLabelText(/Mã xác minh/), '123456')
    await user.click(screen.getByRole('button', { name: 'Xác minh và vào học' }))

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
    vi.mocked(sendRegisterCode).mockRejectedValue(
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
    vi.mocked(sendRegisterCode).mockRejectedValue(
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
