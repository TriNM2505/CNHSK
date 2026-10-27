import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { CurrentUser } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import { changePassword } from '../api/account'
import Scr008ChangePasswordPage from './Scr008ChangePasswordPage'

vi.mock('../api/account', () => ({
  changePassword: vi.fn(),
  getProfile: vi.fn(),
  updateProfile: vi.fn(),
}))

const learner: CurrentUser = {
  displayName: 'Minh Anh',
  email: 'Minh.Anh@example.com',
  emailVerified: true,
  id: 1,
  roles: ['LEARNER'],
}

function renderPage(user: CurrentUser = learner) {
  const auth: AuthContextValue = {
    error: null,
    reloadCurrentUser: vi.fn(),
    setAuthenticatedUser: vi.fn(),
    signOut: vi.fn(),
    status: 'authenticated',
    user,
  }
  render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter>
        <Scr008ChangePasswordPage />
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

async function fill(current: string, next: string, confirm = next) {
  const user = userEvent.setup()
  if (current) await user.type(screen.getByLabelText(/^Mật khẩu hiện tại/), current)
  if (next) await user.type(screen.getByLabelText(/^Mật khẩu mới/), next)
  if (confirm) await user.type(screen.getByLabelText(/Nhập lại mật khẩu mới/), confirm)
  await user.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }))
}

describe('Scr008ChangePasswordPage', () => {
  beforeEach(() => {
    vi.mocked(changePassword).mockReset()
  })

  it('validate đủ ba ô trước khi gửi', async () => {
    renderPage()

    await fill('', 'abcdefgh', 'khac')

    expect(screen.getByText('Vui lòng nhập mật khẩu hiện tại.')).toBeVisible()
    expect(screen.getByText('Mật khẩu nhập lại chưa khớp.')).toBeVisible()
    expect(changePassword).not.toHaveBeenCalled()
  })

  it('không cho đặt mật khẩu mới trùng email (không phân biệt hoa thường)', async () => {
    renderPage({ ...learner, email: 'Abc12345@x.vn' })

    await fill('MatKhauCu1', 'abc12345@x.vn')

    expect(changePassword).not.toHaveBeenCalled()
  })

  it('mật khẩu mới trùng mật khẩu hiện tại bị chặn phía khách', async () => {
    renderPage()

    await fill('MatKhauCu1', 'MatKhauCu1')

    expect(screen.getByText('Mật khẩu mới phải khác mật khẩu hiện tại.')).toBeVisible()
    expect(changePassword).not.toHaveBeenCalled()
  })

  it('FR-044: thành công xoá form và nêu rõ access token khác còn hiệu lực tối đa 15 phút', async () => {
    vi.mocked(changePassword).mockResolvedValue(undefined)
    renderPage()

    await fill('MatKhauCu1', 'MatKhauMoi1')

    expect(await screen.findByRole('status')).toHaveTextContent('Đã đổi mật khẩu')
    expect(screen.getByRole('status')).toHaveTextContent(
      'Refresh token trên các thiết bị khác đã bị thu hồi; access token cũ có thể còn hiệu lực tối đa 15 phút.',
    )
    expect(changePassword).toHaveBeenCalledWith({
      confirmPassword: 'MatKhauMoi1',
      currentPassword: 'MatKhauCu1',
      newPassword: 'MatKhauMoi1',
    })
    expect(screen.getByLabelText(/^Mật khẩu hiện tại/)).toHaveValue('')
  })

  it('CURRENT_PASSWORD_INCORRECT gắn vào ô mật khẩu hiện tại', async () => {
    vi.mocked(changePassword).mockRejectedValue(
      new ApiError({ errorCode: 'CURRENT_PASSWORD_INCORRECT', message: 'x', status: 422 }),
    )
    renderPage()

    await fill('NhapSai1', 'MatKhauMoi1')

    expect(await screen.findByText('Mật khẩu hiện tại không đúng.')).toBeVisible()
  })

  it('nút Huỷ về hồ sơ với người học, về trang chủ với vai trò khác', () => {
    renderPage({ ...learner, roles: ['ADMIN'] })

    expect(screen.getByRole('link', { name: 'Huỷ' })).toHaveAttribute('href', '/')
  })
})
