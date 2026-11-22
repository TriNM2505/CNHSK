import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { RegisterView, type RegisterViewProps } from './RegisterView'

function renderView(overrides: Partial<RegisterViewProps> = {}) {
  const props: RegisterViewProps = {
    loginTo: '/login?redirect=%2Flearn%2Ftopics',
    onAcceptTermsChange: vi.fn(),
    onConfirmPasswordChange: vi.fn(),
    onDisplayNameChange: vi.fn(),
    onEmailChange: vi.fn(),
    onPasswordChange: vi.fn(),
    onSubmit: vi.fn(),
    state: 'ready',
    values: {
      acceptTerms: false,
      confirmPassword: '',
      displayName: '',
      email: '',
      password: '',
    },
    ...overrides,
  }

  const result = render(
    <MemoryRouter>
      <RegisterView {...props} />
    </MemoryRouter>,
  )

  return { ...result, props }
}

describe('RegisterView', () => {
  it('dùng autocomplete đúng và luôn hiện quy tắc mật khẩu', async () => {
    const user = userEvent.setup()
    renderView()

    expect(screen.getByLabelText(/Tên hiển thị/)).toHaveAttribute('autocomplete', 'name')
    expect(screen.getByLabelText(/Email/)).toHaveAttribute('autocomplete', 'email')
    expect(screen.getByLabelText(/^Mật khẩu/)).toHaveAttribute('autocomplete', 'new-password')
    expect(screen.getByLabelText(/Nhập lại mật khẩu/)).toHaveAttribute(
      'autocomplete',
      'new-password',
    )
    expect(screen.getByLabelText(/^Mật khẩu/)).toHaveAccessibleDescription(/8–72 ký tự/)
    expect(screen.getByText('Có số')).toBeVisible()

    const [passwordToggle] = screen.getAllByRole('button', { name: 'Hiện mật khẩu' })
    if (!passwordToggle) throw new Error('Thiếu nút hiện mật khẩu')
    await user.click(passwordToggle)
    expect(screen.getByLabelText(/^Mật khẩu/)).toHaveAttribute('type', 'text')
  })

  it('gửi form, cập nhật checkbox và khoá control khi đang gửi', async () => {
    const user = userEvent.setup()
    const { props, unmount } = renderView()

    await user.click(screen.getByLabelText(/Tôi đồng ý/))
    expect(props.onAcceptTermsChange).toHaveBeenCalledWith(true)
    await user.click(screen.getByRole('button', { name: 'Đăng ký' }))
    expect(props.onSubmit).toHaveBeenCalledOnce()

    unmount()
    renderView({ state: 'submitting' })
    expect(screen.getByRole('button', { name: 'Đang gửi mã xác minh' })).toBeDisabled()
    expect(screen.getByLabelText(/Tên hiển thị/)).toBeDisabled()
    expect(screen.getByLabelText(/Tôi đồng ý/)).toBeDisabled()
  })

  it('hiện lỗi email đã tồn tại kèm link đăng nhập giữ redirect', () => {
    renderView({
      fieldErrors: { email: 'Email này đã được sử dụng.' },
      notice: { kind: 'email-exists' },
      state: 'error',
    })

    expect(screen.getByText('Email này đã được sử dụng.')).toBeVisible()
    expect(screen.getByLabelText(/Email/)).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getAllByRole('link', { name: 'Đăng nhập' })[0]).toHaveAttribute(
      'href',
      '/login?redirect=%2Flearn%2Ftopics',
    )
  })

  it('đưa con trỏ tới ô lỗi đầu tiên sau khi gửi, không giật khi đang sửa ô khác', async () => {
    const user = userEvent.setup()
    const { props, rerender } = renderView()
    const show = (fieldErrors: RegisterViewProps['fieldErrors']) =>
      rerender(
        <MemoryRouter>
          <RegisterView {...props} fieldErrors={fieldErrors} state="error" />
        </MemoryRouter>,
      )

    await user.click(screen.getByRole('button', { name: 'Đăng ký' }))
    show({ confirmPassword: 'Mật khẩu nhập lại chưa khớp.', email: 'Email chưa đúng.' })
    expect(screen.getByLabelText(/Email/)).toHaveFocus()

    await user.click(screen.getByLabelText(/Tên hiển thị/))
    show({ confirmPassword: 'Mật khẩu nhập lại chưa khớp.' })
    expect(screen.getByLabelText(/Tên hiển thị/)).toHaveFocus()
  })

  it('mật khẩu chưa đạt thì chỉ rõ điều kiện còn thiếu, không lặp câu lỗi', () => {
    renderView({
      fieldErrors: { password: 'Mật khẩu cần 8–72 ký tự, gồm chữ và số.' },
      notice: { kind: 'validation' },
      state: 'error',
      values: {
        acceptTerms: false,
        confirmPassword: '',
        displayName: '',
        email: '',
        password: 'abcdefgh',
      },
    })

    expect(screen.getByLabelText(/^Mật khẩu/)).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByText('Có số').closest('li')).toHaveTextContent('chưa đạt')
    expect(screen.getByText('Có chữ').closest('li')).not.toHaveTextContent('chưa đạt')
    expect(screen.queryByText('Mật khẩu cần 8–72 ký tự, gồm chữ và số.')).not.toBeInTheDocument()
  })

  it('hiện lỗi ngay tại field, không thêm thông báo chung lặp ý', () => {
    renderView({
      fieldErrors: {
        acceptTerms: 'Bạn cần đồng ý với điều khoản để đăng ký.',
        confirmPassword: 'Mật khẩu nhập lại chưa khớp.',
      },
      notice: { kind: 'validation' },
      state: 'error',
    })

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByText('Mật khẩu nhập lại chưa khớp.')).toBeVisible()
    expect(screen.getByLabelText(/Nhập lại mật khẩu/)).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText(/Tôi đồng ý/)).toHaveAttribute('aria-invalid', 'true')
  })
})
