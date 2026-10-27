import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'

import { requestPasswordReset } from '../api/passwordReset'
import Scr005ForgotPasswordPage from './Scr005ForgotPasswordPage'

vi.mock('../api/passwordReset', () => ({
  requestPasswordReset: vi.fn(),
  resetPassword: vi.fn(),
}))

function renderPage() {
  render(
    <MemoryRouter>
      <Scr005ForgotPasswordPage />
    </MemoryRouter>,
  )
}

describe('Scr005ForgotPasswordPage', () => {
  beforeEach(() => {
    vi.mocked(requestPasswordReset).mockReset()
  })

  it('kiểm email phía client và không gọi API khi sai', async () => {
    renderPage()
    await userEvent
      .setup()
      .click(screen.getByRole('button', { name: 'Gửi hướng dẫn đặt lại mật khẩu' }))

    expect(await screen.findByText('Vui lòng nhập email.')).toBeVisible()
    expect(requestPasswordReset).not.toHaveBeenCalled()
  })

  it('FR-038: gửi email đã chuẩn hoá và hiện cùng một thông báo', async () => {
    vi.mocked(requestPasswordReset).mockResolvedValue({
      message: 'Thông báo chung từ API, không hiển thị nguyên văn trong UI.',
    })
    renderPage()
    const user = userEvent.setup()

    await user.type(screen.getByLabelText(/Email/), '  Learner@Example.COM ')
    await user.click(screen.getByRole('button', { name: 'Gửi hướng dẫn đặt lại mật khẩu' }))

    expect(requestPasswordReset).toHaveBeenCalledWith('learner@example.com')
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Nếu learner@example.com có tài khoản trong hệ thống',
    )
    expect(screen.getByRole('status')).not.toHaveTextContent('Thông báo chung từ API')
    expect(screen.getByRole('link', { name: 'Quay lại đăng nhập' })).toHaveAttribute(
      'href',
      '/login',
    )
  })

  it('cho gửi lại với email khác', async () => {
    vi.mocked(requestPasswordReset).mockResolvedValue({
      message: 'Thông báo chung từ API.',
    })
    renderPage()
    const user = userEvent.setup()

    await user.type(screen.getByLabelText(/Email/), 'a@example.com')
    await user.click(screen.getByRole('button', { name: 'Gửi hướng dẫn đặt lại mật khẩu' }))
    await user.click(await screen.findByRole('button', { name: 'Gửi lại với email khác' }))

    expect(screen.getByLabelText(/Email/)).toHaveValue('a@example.com')
  })

  it('lỗi mạng hiện thông báo an toàn kèm mã lỗi', async () => {
    vi.mocked(requestPasswordReset).mockRejectedValue(
      new ApiError({ errorCode: 'INTERNAL_ERROR', message: 'x', requestId: 'req-5', status: 500 }),
    )
    renderPage()
    const user = userEvent.setup()

    await user.type(screen.getByLabelText(/Email/), 'a@example.com')
    await user.click(screen.getByRole('button', { name: 'Gửi hướng dẫn đặt lại mật khẩu' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Mã lỗi: req-5')
  })
})
