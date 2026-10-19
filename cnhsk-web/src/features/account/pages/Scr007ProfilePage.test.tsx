import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { CurrentUser } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import { getProfile, updateProfile } from '../api/account'
import type { Profile } from '../types'
import Scr007ProfilePage from './Scr007ProfilePage'

vi.mock('../api/account', () => ({
  changePassword: vi.fn(),
  getProfile: vi.fn(),
  updateProfile: vi.fn(),
}))

const learner: CurrentUser = {
  displayName: 'Minh Anh',
  email: 'minh.anh@example.com',
  emailVerified: true,
  id: 1,
  roles: ['LEARNER'],
}

const profile: Profile = {
  avatarUrl: null,
  displayName: 'Minh Anh',
  email: 'minh.anh@example.com',
  emailVerified: true,
  hskLevel: 3,
}

function renderPage(setAuthenticatedUser = vi.fn()) {
  const auth: AuthContextValue = {
    error: null,
    reloadCurrentUser: vi.fn(),
    setAuthenticatedUser,
    signOut: vi.fn(),
    status: 'authenticated',
    user: learner,
  }
  render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter>
        <Scr007ProfilePage />
      </MemoryRouter>
    </AuthContext.Provider>,
  )
  return { setAuthenticatedUser }
}

describe('Scr007ProfilePage', () => {
  beforeEach(() => {
    vi.mocked(getProfile).mockReset()
    vi.mocked(updateProfile).mockReset()
  })

  it('tải hồ sơ rồi điền sẵn vào form', async () => {
    vi.mocked(getProfile).mockResolvedValue(profile)
    renderPage()

    expect(await screen.findByLabelText(/Tên hiển thị/)).toHaveValue('Minh Anh')
    expect(screen.getByLabelText('Cấp HSK mục tiêu')).toHaveValue('3')
    expect(screen.getByText('Đã xác thực email')).toBeVisible()
  })

  it('lỗi tải hồ sơ hiện nút thử lại', async () => {
    vi.mocked(getProfile)
      .mockRejectedValueOnce(
        new ApiError({
          errorCode: 'INTERNAL_ERROR',
          message: 'x',
          requestId: 'req-1',
          status: 500,
        }),
      )
      .mockResolvedValueOnce(profile)
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: /Thử lại/ }))

    expect(await screen.findByLabelText(/Tên hiển thị/)).toHaveValue('Minh Anh')
    expect(getProfile).toHaveBeenCalledTimes(2)
  })

  it('FR-095: validate tên và đường dẫn ảnh trước khi gửi', async () => {
    vi.mocked(getProfile).mockResolvedValue(profile)
    renderPage()
    const user = userEvent.setup()

    const nameInput = await screen.findByLabelText(/Tên hiển thị/)
    await user.clear(nameInput)
    await user.type(nameInput, 'A')
    await user.type(screen.getByLabelText(/Ảnh đại diện/), 'http://anh.example.com/a.png')
    await user.click(screen.getByRole('button', { name: 'Lưu hồ sơ' }))

    expect(screen.getByText('Tên hiển thị cần từ 2–100 ký tự.')).toBeVisible()
    expect(
      screen.getByText('Đường dẫn ảnh phải bắt đầu bằng https:// và không có khoảng trắng.'),
    ).toBeVisible()
    expect(updateProfile).not.toHaveBeenCalled()
  })

  it('lưu thành công gửi giá trị rỗng là null và cập nhật người dùng hiện tại', async () => {
    vi.mocked(getProfile).mockResolvedValue(profile)
    vi.mocked(updateProfile).mockResolvedValue({
      ...profile,
      displayName: 'Minh Anh Mới',
      hskLevel: null,
    })
    const { setAuthenticatedUser } = renderPage()
    const user = userEvent.setup()

    const nameInput = await screen.findByLabelText(/Tên hiển thị/)
    await user.clear(nameInput)
    await user.type(nameInput, '  Minh Anh Mới ')
    await user.selectOptions(screen.getByLabelText('Cấp HSK mục tiêu'), '')
    await user.click(screen.getByRole('button', { name: 'Lưu hồ sơ' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Đã lưu hồ sơ')
    expect(updateProfile).toHaveBeenCalledWith({
      avatarUrl: null,
      displayName: 'Minh Anh Mới',
      hskLevel: null,
    })
    expect(setAuthenticatedUser).toHaveBeenCalledWith({
      ...learner,
      avatarUrl: null,
      displayName: 'Minh Anh Mới',
      hskLevel: null,
    })
    expect(nameInput).toHaveValue('Minh Anh Mới')
  })

  it('lỗi 422 từ máy chủ gắn vào đúng trường', async () => {
    vi.mocked(getProfile).mockResolvedValue(profile)
    vi.mocked(updateProfile).mockRejectedValue(
      new ApiError({
        details: [{ field: 'displayName', message: 'Tên chứa ký tự không hợp lệ.' }],
        errorCode: 'VALIDATION_ERROR',
        message: 'x',
        status: 422,
      }),
    )
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'Lưu hồ sơ' }))

    expect(await screen.findByText('Tên chứa ký tự không hợp lệ.')).toBeVisible()
    expect(screen.getByRole('alert')).toHaveTextContent('Vui lòng kiểm tra lại các trường')
  })
})
