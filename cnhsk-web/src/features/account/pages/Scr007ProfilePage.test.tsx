import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { CurrentUser } from '@/features/auth/types'
import { getUploadSignature, uploadToCloudinary } from '@/features/community/api/media'
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
vi.mock('@/features/community/api/media', () => ({
  getUploadSignature: vi.fn(),
  uploadToCloudinary: vi.fn(),
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
  createdAt: '2022-08-22T10:43:00Z',
  deviceCount: 2,
  displayName: 'Minh Anh',
  email: 'minh.anh@example.com',
  emailVerified: true,
  hskLevel: 3,
  id: 1042,
  planCode: 'FREE',
  planExpiresAt: null,
}

const signature = {
  apiKey: 'key',
  cloudName: 'demo',
  folder: 'cnhsk/avatars',
  signature: 'sig',
  timestamp: 1,
  uploadUrl: 'https://api.cloudinary.com/v1_1/demo/auto/upload',
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
  const view = render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter>
        <Scr007ProfilePage />
      </MemoryRouter>
    </AuthContext.Provider>,
  )
  return { setAuthenticatedUser, container: view.container }
}

function fileInput(container: HTMLElement): HTMLInputElement {
  return container.querySelector('input[type="file"]')!
}

function chooseFile(container: HTMLElement, file: File) {
  fireEvent.change(fileInput(container), { target: { files: [file] } })
}

describe('Scr007ProfilePage', () => {
  beforeEach(() => {
    vi.mocked(getProfile).mockReset()
    vi.mocked(updateProfile).mockReset()
    vi.mocked(getUploadSignature).mockReset()
    vi.mocked(uploadToCloudinary).mockReset()
  })

  it('shows the profile card and read-only account facts', async () => {
    vi.mocked(getProfile).mockResolvedValue(profile)
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Minh Anh' })).toBeVisible()
    expect(screen.getByLabelText('Cấp HSK mục tiêu')).toHaveValue('3')
    expect(screen.queryByText('Email chưa được xác thực.')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Đổi mật khẩu' })).toHaveAttribute('href', '/me/password')

    const facts = screen.getByRole('region', { name: 'Thông tin tài khoản' })
    expect(within(facts).getByText('minh.anh@example.com')).toBeVisible()
    expect(within(facts).getByText('1042')).toBeVisible()
    expect(within(facts).getByText('22.08.2022 | 17:43')).toBeVisible()
    expect(within(facts).getByText('2')).toBeVisible()
    expect(within(facts).getByText('Thành viên')).toBeVisible()
    const plan = within(facts).getByRole('link', { name: /FREE/ })
    expect(plan).toHaveAttribute('href', '/account/credits')
    expect(plan).toHaveTextContent('Miễn phí')
    // Facts are read-only: no inputs in the account card.
    expect(within(facts).queryByRole('textbox')).not.toBeInTheDocument()
  })

  it('marks an active premium plan and asks an unverified user to verify the email', async () => {
    vi.mocked(getProfile).mockResolvedValue({
      ...profile,
      emailVerified: false,
      planCode: 'PREMIUM',
      planExpiresAt: '2099-12-31T10:00:00Z',
    })
    renderPage()

    const facts = await screen.findByRole('region', { name: 'Thông tin tài khoản' })
    const plan = within(facts).getByRole('link', { name: /VIP/ })
    expect(plan).toHaveTextContent('Cao cấp đến 31/12/2099')
    expect(within(facts).getByText('Học viên Premium')).toBeVisible()
    expect(screen.getByText('Email chưa được xác thực.')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Xác thực ngay' })).toHaveAttribute('href', '/verify-email')
  })

  it('keeps HSK 1–3 choices and a saved level outside them', async () => {
    vi.mocked(getProfile).mockResolvedValue({ ...profile, hskLevel: 5 })
    renderPage()

    const select = await screen.findByLabelText('Cấp HSK mục tiêu')
    await waitFor(() => expect(select).toHaveValue('5'))
    const values = Array.from((select as HTMLSelectElement).options).map((option) => option.value)
    expect(values).toEqual(['', '1', '2', '3', '5'])
  })

  it('offers a retry when the profile cannot load', async () => {
    vi.mocked(getProfile)
      .mockRejectedValueOnce(
        new ApiError({ errorCode: 'INTERNAL_ERROR', message: 'x', requestId: 'req-1', status: 500 }),
      )
      .mockResolvedValueOnce(profile)
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: /Thử lại/ }))

    expect(await screen.findByRole('heading', { name: 'Minh Anh' })).toBeVisible()
    expect(getProfile).toHaveBeenCalledTimes(2)
  })

  it('renames in place, saves the trimmed name once and updates the signed-in user', async () => {
    vi.mocked(getProfile).mockResolvedValue(profile)
    vi.mocked(updateProfile).mockResolvedValue({ ...profile, displayName: 'Minh Anh Mới' })
    const { setAuthenticatedUser } = renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Sửa tên hiển thị' }))
    const input = screen.getByRole('textbox', { name: 'Tên hiển thị' })
    expect(input).toHaveValue('Minh Anh')
    await user.clear(input)
    await user.type(input, '  Minh Anh Mới {Enter}')

    expect(await screen.findByRole('heading', { name: 'Minh Anh Mới' })).toBeVisible()
    expect(updateProfile).toHaveBeenCalledTimes(1)
    expect(updateProfile).toHaveBeenCalledWith({ displayName: 'Minh Anh Mới' })
    expect(setAuthenticatedUser).toHaveBeenCalledWith({
      ...learner,
      avatarUrl: null,
      displayName: 'Minh Anh Mới',
      hskLevel: 3,
    })
  })

  it('rejects a too-short name locally and sends nothing for an unchanged name', async () => {
    vi.mocked(getProfile).mockResolvedValue(profile)
    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Sửa tên hiển thị' }))
    const input = screen.getByRole('textbox', { name: 'Tên hiển thị' })
    await user.clear(input)
    await user.type(input, 'A{Enter}')
    expect(screen.getByRole('alert')).toHaveTextContent('Tên hiển thị cần 2–100 ký tự.')
    expect(screen.getByRole('textbox', { name: 'Tên hiển thị' })).toHaveValue('A')

    await user.clear(input)
    await user.type(input, 'Minh Anh{Enter}')
    expect(await screen.findByRole('heading', { name: 'Minh Anh' })).toBeVisible()
    expect(updateProfile).not.toHaveBeenCalled()
  })

  it('shows the server field message when a rename is rejected and keeps the editor open', async () => {
    vi.mocked(getProfile).mockResolvedValue(profile)
    vi.mocked(updateProfile).mockRejectedValue(
      new ApiError({
        details: [{ field: 'displayName', message: 'Tên chứa ký tự không hợp lệ.' }],
        errorCode: 'VALIDATION_ERROR',
        message: 'x',
        status: 422,
      }),
    )
    const { setAuthenticatedUser } = renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Sửa tên hiển thị' }))
    const input = screen.getByRole('textbox', { name: 'Tên hiển thị' })
    await user.clear(input)
    await user.type(input, 'Tên <lạ>{Enter}')

    expect(await screen.findByRole('alert')).toHaveTextContent('Tên chứa ký tự không hợp lệ.')
    expect(screen.getByRole('textbox', { name: 'Tên hiển thị' })).toHaveValue('Tên <lạ>')
    expect(setAuthenticatedUser).not.toHaveBeenCalled()
  })

  it('saves another HSK level on its own, including clearing it', async () => {
    vi.mocked(getProfile).mockResolvedValue(profile)
    vi.mocked(updateProfile).mockResolvedValue({ ...profile, hskLevel: null })
    const { setAuthenticatedUser } = renderPage()
    const user = userEvent.setup()

    await user.selectOptions(await screen.findByLabelText('Cấp HSK mục tiêu'), '')

    await waitFor(() => expect(screen.getByLabelText('Cấp HSK mục tiêu')).toHaveValue(''))
    expect(updateProfile).toHaveBeenCalledWith({ hskLevel: null })
    expect(setAuthenticatedUser).toHaveBeenCalledWith(expect.objectContaining({ hskLevel: null }))
  })

  it('uploads a new photo to Cloudinary with the avatar signature and saves its URL', async () => {
    vi.mocked(getProfile).mockResolvedValue(profile)
    vi.mocked(getUploadSignature).mockResolvedValue(signature)
    vi.mocked(uploadToCloudinary).mockImplementation(async (_file, _signature, onProgress) => {
      onProgress?.(0.5)
      return { kind: 'IMAGE', publicId: 'cnhsk/avatars/a', url: 'https://res.cloudinary.com/demo/a.png' }
    })
    vi.mocked(updateProfile).mockResolvedValue({
      ...profile,
      avatarUrl: 'https://res.cloudinary.com/demo/a.png',
    })
    const { container, setAuthenticatedUser } = renderPage()
    await screen.findByRole('heading', { name: 'Minh Anh' })

    const file = new File(['x'], 'anh.png', { type: 'image/png' })
    chooseFile(container, file)

    await waitFor(() =>
      expect(updateProfile).toHaveBeenCalledWith({ avatarUrl: 'https://res.cloudinary.com/demo/a.png' }),
    )
    expect(getUploadSignature).toHaveBeenCalledWith('avatar')
    expect(uploadToCloudinary).toHaveBeenCalledWith(file, signature, expect.any(Function))
    await waitFor(() =>
      expect(container.querySelector('img[src="https://res.cloudinary.com/demo/a.png"]')).not.toBeNull(),
    )
    expect(setAuthenticatedUser).toHaveBeenCalledWith(
      expect.objectContaining({ avatarUrl: 'https://res.cloudinary.com/demo/a.png' }),
    )
    expect(screen.getByRole('button', { name: 'Đổi ảnh đại diện' })).toBeEnabled()
  })

  it.each([
    ['a non-image file', new File(['x'], 'ghi-chu.txt', { type: 'text/plain' }), 'Chọn một tệp ảnh.'],
    [
      'an image over 5 MB',
      new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'lon.png', { type: 'image/png' }),
      'Ảnh đại diện tối đa 5 MB.',
    ],
  ])('refuses %s before asking for a signature', async (_name, file, message) => {
    vi.mocked(getProfile).mockResolvedValue(profile)
    const { container } = renderPage()
    await screen.findByRole('heading', { name: 'Minh Anh' })

    chooseFile(container, file)

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(getUploadSignature).not.toHaveBeenCalled()
    expect(uploadToCloudinary).not.toHaveBeenCalled()
    expect(updateProfile).not.toHaveBeenCalled()
  })

  it('keeps the old photo and reports a failed upload without saving', async () => {
    vi.mocked(getProfile).mockResolvedValue(profile)
    vi.mocked(getUploadSignature).mockResolvedValue(signature)
    vi.mocked(uploadToCloudinary).mockRejectedValue(new Error('Không tải được anh.png lên.'))
    const { container } = renderPage()
    await screen.findByRole('heading', { name: 'Minh Anh' })

    chooseFile(container, new File(['x'], 'anh.png', { type: 'image/png' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Chưa tải được ảnh lên')
    expect(alert).toHaveTextContent('Không tải được anh.png lên.')
    expect(updateProfile).not.toHaveBeenCalled()
    expect(container.querySelector('img')).toBeNull()
    expect(screen.getByRole('button', { name: 'Đổi ảnh đại diện' })).toBeEnabled()
  })
})
