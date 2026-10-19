import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StrictMode } from 'react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'

import { resetPassword } from '../api/passwordReset'
import Scr006ResetPasswordPage from './Scr006ResetPasswordPage'

vi.mock('../api/passwordReset', () => ({
  requestPasswordReset: vi.fn(),
  resetPassword: vi.fn(),
}))

function Probe() {
  const location = useLocation()
  return (
    <output data-testid="location">
      {location.pathname}
      {location.search}|{JSON.stringify(location.state)}
    </output>
  )
}

function renderPage(path: string) {
  render(
    <StrictMode>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route
            element={
              <>
                <Scr006ResetPasswordPage />
                <Probe />
              </>
            }
            path="/reset-password"
          />
          <Route element={<Probe />} path="/login" />
        </Routes>
      </MemoryRouter>
    </StrictMode>,
  )
}

async function fillPasswords(newPassword: string, confirmPassword = newPassword) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText(/^Mật khẩu mới/), newPassword)
  await user.type(screen.getByLabelText(/Nhập lại mật khẩu mới/), confirmPassword)
  await user.click(screen.getByRole('button', { name: 'Đặt lại mật khẩu' }))
}

describe('Scr006ResetPasswordPage', () => {
  beforeEach(() => {
    vi.mocked(resetPassword).mockReset()
  })

  it('không có token → báo liên kết không hợp lệ và mời gửi liên kết mới', () => {
    renderPage('/reset-password')

    expect(screen.getByText('Liên kết đặt lại không hợp lệ')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Gửi liên kết mới' })).toHaveAttribute(
      'href',
      '/forgot-password',
    )
  })

  it('xoá token khỏi URL nhưng vẫn dùng được để gửi', async () => {
    vi.mocked(resetPassword).mockResolvedValue(undefined)
    renderPage('/reset-password?token=tok-1')

    expect(screen.getByTestId('location')).toHaveTextContent(/^\/reset-password\|/)
    await fillPasswords('MatKhauMoi1')

    expect(resetPassword).toHaveBeenCalledWith({
      confirmPassword: 'MatKhauMoi1',
      newPassword: 'MatKhauMoi1',
      token: 'tok-1',
    })
  })

  it('FR-042: thành công chuyển /login kèm cờ đặt lại mật khẩu', async () => {
    vi.mocked(resetPassword).mockResolvedValue(undefined)
    renderPage('/reset-password?token=tok-1')

    await fillPasswords('MatKhauMoi1')

    expect(await screen.findByTestId('location')).toHaveTextContent('/login|{"passwordReset":true}')
  })

  it('validate luật mật khẩu mới và mật khẩu nhập lại', async () => {
    renderPage('/reset-password?token=tok-1')

    await fillPasswords('abcdefgh', 'khac')

    expect(screen.getAllByText('Mật khẩu cần 8–72 ký tự, gồm chữ và số.').length).toBeGreaterThan(1)
    expect(screen.getByText('Mật khẩu nhập lại chưa khớp.')).toBeVisible()
    expect(resetPassword).not.toHaveBeenCalled()
  })

  it('FR-045: PASSWORD_UNCHANGED gắn vào ô mật khẩu mới', async () => {
    vi.mocked(resetPassword).mockRejectedValue(
      new ApiError({ errorCode: 'PASSWORD_UNCHANGED', message: 'x', status: 422 }),
    )
    renderPage('/reset-password?token=tok-1')

    await fillPasswords('MatKhauCu1')

    expect(await screen.findByText('Mật khẩu mới phải khác mật khẩu hiện tại.')).toBeVisible()
  })

  it('INVALID_RESET_TOKEN chuyển sang trạng thái liên kết hỏng', async () => {
    vi.mocked(resetPassword).mockRejectedValue(
      new ApiError({ errorCode: 'INVALID_RESET_TOKEN', message: 'x', status: 400 }),
    )
    renderPage('/reset-password?token=het-han')

    await fillPasswords('MatKhauMoi1')

    expect(await screen.findByText('Liên kết đặt lại không hợp lệ')).toBeVisible()
  })
})
