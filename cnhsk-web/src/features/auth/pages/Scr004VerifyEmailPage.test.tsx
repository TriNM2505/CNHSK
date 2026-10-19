import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StrictMode } from 'react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { CurrentUser } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import { resendVerificationEmail, verifyEmail } from '../api/verifyEmail'
import Scr004VerifyEmailPage from './Scr004VerifyEmailPage'

vi.mock('../api/verifyEmail', () => ({
  resendVerificationEmail: vi.fn(),
  verifyEmail: vi.fn(),
}))

const learner: CurrentUser = {
  displayName: 'Nguyễn An',
  email: 'learner@example.com',
  emailVerified: false,
  id: 7,
  roles: ['LEARNER'],
}

function authValue(overrides: Partial<AuthContextValue> = {}): AuthContextValue {
  return {
    error: null,
    reloadCurrentUser: vi.fn(async () => undefined),
    setAuthenticatedUser: vi.fn(),
    signOut: vi.fn(async () => undefined),
    status: 'guest',
    user: null,
    ...overrides,
  }
}

function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{`${location.pathname}${location.search}`}</output>
}

function renderPage(
  entry: string | { pathname: string; state: unknown },
  auth: AuthContextValue = authValue(),
) {
  return render(
    <StrictMode>
      <AuthContext.Provider value={auth}>
        <MemoryRouter initialEntries={[entry]}>
          <Routes>
            <Route
              element={
                <>
                  <Scr004VerifyEmailPage />
                  <LocationProbe />
                </>
              }
              path="/verify-email"
            />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>
    </StrictMode>,
  )
}

describe('Scr004VerifyEmailPage', () => {
  beforeEach(() => {
    vi.mocked(verifyEmail).mockReset()
    vi.mocked(resendVerificationEmail).mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('FR-088: tự xác thực đúng một lần và xoá token khỏi URL', async () => {
    vi.mocked(verifyEmail).mockResolvedValue({ emailVerified: true })
    renderPage('/verify-email?token=abc123')

    expect(await screen.findByText('Email đã được xác thực')).toBeVisible()
    expect(verifyEmail).toHaveBeenCalledTimes(1)
    expect(verifyEmail).toHaveBeenCalledWith('abc123')
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/verify-email$/)
    expect(screen.getByRole('link', { name: 'Đăng nhập để tiếp tục' })).toHaveAttribute(
      'href',
      '/login',
    )
  })

  it('làm mới user đang đăng nhập sau khi xác thực bằng link', async () => {
    vi.mocked(verifyEmail).mockResolvedValue({ emailVerified: true })
    const auth = authValue({ status: 'authenticated', user: learner })
    renderPage('/verify-email?token=abc123', auth)

    await waitFor(() => expect(auth.reloadCurrentUser).toHaveBeenCalledTimes(1))
    expect(screen.getByRole('link', { name: 'Tiếp tục' })).toHaveAttribute('href', '/learn/topics')
  })

  it('FR-011: token hỏng hiện thông báo hết hạn và mời khách đăng nhập để gửi lại', async () => {
    vi.mocked(verifyEmail).mockRejectedValue(
      new ApiError({ errorCode: 'INVALID_VERIFY_TOKEN', message: 'x', status: 400 }),
    )
    renderPage('/verify-email?token=old')

    expect(await screen.findByText('Liên kết đã hết hạn hoặc đã được dùng')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Đăng nhập để gửi lại' })).toHaveAttribute(
      'href',
      '/login?redirect=%2Fverify-email',
    )
  })

  it('lỗi khác hiện mã lỗi và cho thử lại', async () => {
    vi.mocked(verifyEmail)
      .mockRejectedValueOnce(
        new ApiError({
          errorCode: 'INTERNAL_ERROR',
          message: 'x',
          requestId: 'req-9',
          status: 500,
        }),
      )
      .mockResolvedValueOnce({ emailVerified: true })
    renderPage('/verify-email?token=abc')

    expect(await screen.findByText('Mã lỗi: req-9')).toBeVisible()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Thử lại' }))

    expect(await screen.findByText('Email đã được xác thực')).toBeVisible()
    expect(verifyEmail).toHaveBeenCalledTimes(2)
  })

  it('FR-087: sau đăng ký hiện email đã gửi; khách không có nút gửi lại', async () => {
    renderPage({
      pathname: '/verify-email',
      state: { email: 'moi@example.com', redirect: '/learn/grammar' },
    })

    expect(await screen.findByText('moi@example.com')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Gửi lại email xác thực' })).toBeNull()
    expect(screen.getByRole('link', { name: 'Bỏ qua, vào học' })).toHaveAttribute(
      'href',
      '/login?redirect=%2Flearn%2Fgrammar',
    )
    expect(verifyEmail).not.toHaveBeenCalled()
  })

  it('FR-012: người đã đăng nhập gửi lại thành công', async () => {
    vi.mocked(resendVerificationEmail).mockResolvedValue(undefined)
    renderPage('/verify-email', authValue({ status: 'authenticated', user: learner }))

    expect(screen.getByText('learner@example.com')).toBeVisible()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Gửi lại email xác thực' }))

    expect(await screen.findByText('Đã gửi lại. Email mới sẽ đến trong vài phút.')).toBeVisible()
  })

  it('FR-012: 429 khoá nút và đếm ngược theo retryAfterSeconds', async () => {
    vi.mocked(resendVerificationEmail).mockRejectedValue(
      new ApiError({
        errorCode: 'TOO_MANY_REQUESTS',
        extra: { retryAfterSeconds: 90 },
        message: 'x',
        status: 429,
      }),
    )
    renderPage('/verify-email', authValue({ status: 'authenticated', user: learner }))

    await userEvent.setup().click(screen.getByRole('button', { name: 'Gửi lại email xác thực' }))

    const cooldownButton = await screen.findByRole('button', { name: /Gửi lại sau 1:(30|29)/ })
    expect(cooldownButton).toBeDisabled()
  })

  it('409 EMAIL_ALREADY_VERIFIED tải lại user thay vì báo lỗi', async () => {
    vi.mocked(resendVerificationEmail).mockRejectedValue(
      new ApiError({ errorCode: 'EMAIL_ALREADY_VERIFIED', message: 'x', status: 409 }),
    )
    const auth = authValue({ status: 'authenticated', user: learner })
    renderPage('/verify-email', auth)

    await userEvent.setup().click(screen.getByRole('button', { name: 'Gửi lại email xác thực' }))

    await waitFor(() => expect(auth.reloadCurrentUser).toHaveBeenCalled())
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('người đã xác thực thấy trạng thái đã xác thực', () => {
    renderPage(
      '/verify-email',
      authValue({ status: 'authenticated', user: { ...learner, emailVerified: true } }),
    )

    expect(screen.getByText('Email đã được xác thực')).toBeVisible()
  })

  it('đếm ngược tự mở khoá khi hết thời gian chờ', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.mocked(resendVerificationEmail).mockRejectedValue(
      new ApiError({
        errorCode: 'TOO_MANY_REQUESTS',
        extra: { retryAfterSeconds: 2 },
        message: 'x',
        status: 429,
      }),
    )
    renderPage('/verify-email', authValue({ status: 'authenticated', user: learner }))

    await userEvent
      .setup({ advanceTimers: vi.advanceTimersByTime })
      .click(screen.getByRole('button', { name: 'Gửi lại email xác thực' }))
    expect(await screen.findByRole('button', { name: /Gửi lại sau/ })).toBeDisabled()

    await act(async () => {
      vi.advanceTimersByTime(3000)
    })

    expect(screen.getByRole('button', { name: 'Gửi lại email xác thực' })).toBeEnabled()
  })
})
