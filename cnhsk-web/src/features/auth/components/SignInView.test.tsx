import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { SignInView, type SignInViewProps } from './SignInView'

function renderView(overrides: Partial<SignInViewProps> = {}) {
  const props: SignInViewProps = {
    onEmailChange: vi.fn(),
    onPasswordChange: vi.fn(),
    onSubmit: vi.fn(),
    registerTo: '/register?redirect=%2Flearn%2Ftopics',
    state: 'ready',
    values: { email: '', password: '' },
    ...overrides,
  }

  const result = render(
    <MemoryRouter>
      <SignInView {...props} />
    </MemoryRouter>,
  )

  return { ...result, props }
}

describe('SignInView', () => {
  it('dùng autocomplete đúng, PasswordInput và giữ redirect khi đăng ký', async () => {
    const user = userEvent.setup()
    renderView()

    expect(screen.getByLabelText(/Email/)).toHaveAttribute('autocomplete', 'username')
    expect(screen.getByLabelText(/Mật khẩu/)).toHaveAttribute('autocomplete', 'current-password')
    await user.click(screen.getByRole('button', { name: 'Hiện mật khẩu' }))
    expect(screen.getByLabelText(/Mật khẩu/)).toHaveAttribute('type', 'text')
    expect(screen.getByRole('link', { name: 'Đăng ký' })).toHaveAttribute(
      'href',
      '/register?redirect=%2Flearn%2Ftopics',
    )
    expect(screen.getByRole('link', { name: 'Quên mật khẩu?' })).toHaveAttribute(
      'href',
      '/forgot-password',
    )
  })

  it('gửi form qua callback và khoá đúng action khi đang gửi', async () => {
    const user = userEvent.setup()
    const { props, unmount } = renderView()

    await user.click(screen.getByRole('button', { name: 'Đăng nhập' }))
    expect(props.onSubmit).toHaveBeenCalledOnce()

    unmount()
    renderView({ state: 'submitting' })
    expect(screen.getByRole('button', { name: 'Đang đăng nhập' })).toBeDisabled()
    expect(screen.getByLabelText(/Email/)).toBeDisabled()
    expect(screen.getByLabelText(/Mật khẩu/)).toBeDisabled()
  })

  it('hiện lỗi 401 chung và lỗi 422 tại đúng trường', () => {
    renderView({
      fieldErrors: { email: 'Email chưa hợp lệ.' },
      notice: { kind: 'invalid-credentials' },
      state: 'error',
    })

    expect(screen.getByRole('alert')).toHaveTextContent('Email hoặc mật khẩu không đúng')
    expect(screen.getByText('Email chưa hợp lệ.')).toBeVisible()
    expect(screen.getByLabelText(/Email/)).toHaveAttribute('aria-invalid', 'true')
  })

  it('hiện giờ mở chặn và lý do cấm bằng chữ, không chỉ bằng màu', () => {
    renderView({
      notice: { blockedUntil: '2026-10-20T14:30:00+07:00', kind: 'blocked' },
      state: 'error',
      submitDisabled: true,
    })

    expect(screen.getByRole('alert')).toHaveTextContent('Bạn có thể đăng nhập lại từ')
    expect(screen.getByRole('button', { name: 'Đăng nhập' })).toBeDisabled()

    renderView({
      notice: { banReason: 'Vi phạm quy định cộng đồng.', kind: 'banned' },
      state: 'error',
    })
    expect(screen.getByText('Tài khoản của bạn đã bị cấm.')).toBeVisible()
    expect(screen.getByText('Lý do: Vi phạm quy định cộng đồng.')).toBeVisible()
  })
})
