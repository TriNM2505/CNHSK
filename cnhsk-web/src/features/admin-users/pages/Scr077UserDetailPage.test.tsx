import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { CurrentUser } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import {
  banAdminUser,
  getAdminUserDetail,
  grantAdminUserRole,
  revokeAdminUserRole,
  unbanAdminUser,
} from '../api/adminUsers'
import type { AdminUserDetail } from '../types'
import Scr077UserDetailPage from './Scr077UserDetailPage'

vi.mock('../api/adminUsers', () => ({
  banAdminUser: vi.fn(),
  getAdminUserDetail: vi.fn(),
  grantAdminUserRole: vi.fn(),
  revokeAdminUserRole: vi.fn(),
  unbanAdminUser: vi.fn(),
}))

const getAdminUserDetailMock = vi.mocked(getAdminUserDetail)
const banAdminUserMock = vi.mocked(banAdminUser)
const grantAdminUserRoleMock = vi.mocked(grantAdminUserRole)
const revokeAdminUserRoleMock = vi.mocked(revokeAdminUserRole)
const unbanAdminUserMock = vi.mocked(unbanAdminUser)

const currentAdmin: CurrentUser = {
  id: 103,
  displayName: 'Quản trị viên',
  email: 'admin@example.com',
  emailVerified: true,
  roles: ['ADMIN'],
}

const detail: AdminUserDetail = {
  id: 101,
  email: 'minh.anh@example.com',
  displayName: 'Minh Anh',
  roles: [
    { code: 'LEARNER', grantedAt: '2026-10-19T17:30:00Z', grantedBy: 103 },
    { code: 'ADMIN', grantedAt: '2026-10-20T01:00:00Z', grantedBy: null },
  ],
  status: 'ACTIVE',
  emailVerifiedAt: '2026-10-19T17:00:00Z',
  createdAt: '2026-10-18T17:30:00Z',
}

function renderPage({
  currentUser = currentAdmin,
  entry = '/admin/users/101',
  setAuthenticatedUser = vi.fn(),
}: {
  currentUser?: CurrentUser
  entry?: string
  setAuthenticatedUser?: ReturnType<typeof vi.fn>
} = {}) {
  const auth: AuthContextValue = {
    error: null,
    reloadCurrentUser: vi.fn(),
    setAuthenticatedUser,
    signOut: vi.fn(),
    status: 'authenticated',
    user: currentUser,
  }
  const router = createMemoryRouter(
    [
      { element: <Scr077UserDetailPage />, path: '/admin/users/:id' },
      { element: <h1>Danh sách tài khoản</h1>, path: '/admin/users' },
    ],
    { initialEntries: [entry] },
  )

  render(
    <AuthContext.Provider value={auth}>
      <RouterProvider router={router} />
    </AuthContext.Provider>,
  )
  return { router, setAuthenticatedUser }
}

describe('Scr077UserDetailPage', () => {
  beforeEach(() => {
    getAdminUserDetailMock.mockReset()
    banAdminUserMock.mockReset()
    grantAdminUserRoleMock.mockReset()
    revokeAdminUserRoleMock.mockReset()
    unbanAdminUserMock.mockReset()
    getAdminUserDetailMock.mockResolvedValue(detail)
  })

  it('hiện chi tiết, nguồn cấp role, ngày theo múi giờ Việt Nam và chỉ role còn thiếu', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Minh Anh' })).toBeVisible()
    expect(screen.getByText('minh.anh@example.com')).toBeVisible()
    expect(screen.getAllByText('#103').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Không ghi nhận').length).toBeGreaterThan(0)
    expect(screen.getAllByText('20/10/26').length).toBeGreaterThan(0)

    const roleSelect = screen.getByLabelText('Vai trò cần cấp')
    expect(roleSelect).not.toHaveTextContent('Người học')
    expect(roleSelect).not.toHaveTextContent('Quản trị viên')
    expect(roleSelect).toHaveTextContent('Giáo viên')
  })

  it('hiện EmptyState khi API trả 404', async () => {
    getAdminUserDetailMock.mockRejectedValue(
      new ApiError({
        errorCode: 'USER_NOT_FOUND',
        message: 'Not found',
        requestId: 'req-404',
        status: 404,
      }),
    )
    renderPage()

    expect(await screen.findByText('Không tìm thấy tài khoản')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Về danh sách tài khoản' })).toHaveAttribute(
      'href',
      '/admin/users',
    )
  })

  it('giữ panel cấm mở khi validate hoặc đang gửi và chỉ đóng sau thành công', async () => {
    let resolveBan: (() => void) | undefined
    banAdminUserMock.mockReturnValue(
      new Promise<void>((resolve) => {
        resolveBan = resolve
      }),
    )
    renderPage()
    const user = userEvent.setup()

    await screen.findByRole('heading', { name: 'Minh Anh' })
    await user.click(screen.getByRole('button', { name: 'Cấm tài khoản' }))
    const dialog = screen.getByRole('dialog')
    const textarea = screen.getByLabelText('Lý do cấm')
    await user.type(textarea, 'abc')
    await user.click(within(dialog).getByRole('button', { name: 'Cấm tài khoản' }))

    expect(screen.getByText(/ít nhất 5 ký tự/)).toBeVisible()
    expect(within(dialog).getByRole('button', { name: 'Cấm tài khoản' })).toBeEnabled()
    expect(banAdminUserMock).not.toHaveBeenCalled()

    await user.clear(textarea)
    await user.type(textarea, 'Vi phạm điều khoản')
    await user.click(within(dialog).getByRole('button', { name: 'Cấm tài khoản' }))

    expect(screen.getByRole('dialog')).toBeVisible()
    expect(screen.getByLabelText('Lý do cấm')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Huỷ' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Đang xử lý' })).toBeDisabled()

    await act(async () => resolveBan?.())
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(
      screen.getByText('Đã cấm tài khoản và đăng xuất tài khoản khỏi mọi thiết bị.'),
    ).toBeVisible()
    expect(getAdminUserDetailMock).toHaveBeenCalledTimes(2)
  })

  it('gắn lỗi 422 vào textarea và không đóng panel', async () => {
    banAdminUserMock.mockRejectedValue(
      new ApiError({
        details: { reason: 'Lý do không được chấp nhận.' },
        errorCode: 'VALIDATION_ERROR',
        message: 'Invalid',
        requestId: 'req-422',
        status: 422,
      }),
    )
    renderPage()
    const user = userEvent.setup()

    await screen.findByRole('heading', { name: 'Minh Anh' })
    await user.click(screen.getByRole('button', { name: 'Cấm tài khoản' }))
    const dialog = screen.getByRole('dialog')
    await user.type(screen.getByLabelText('Lý do cấm'), 'Vi phạm điều khoản')
    await user.click(within(dialog).getByRole('button', { name: 'Cấm tài khoản' }))

    expect(await screen.findByText('Lý do không được chấp nhận.')).toBeVisible()
    expect(screen.getByRole('dialog')).toBeVisible()
    expect(screen.getByLabelText('Lý do cấm')).toHaveAttribute(
      'aria-describedby',
      expect.stringContaining('ban-reason-error'),
    )
  })

  it('cho phép cấp role cho tài khoản bị cấm và chỉ có hiệu lực sau khi gỡ cấm', async () => {
    const bannedDetail: AdminUserDetail = {
      ...detail,
      roles: [detail.roles[0]!],
      status: 'BANNED',
      bannedAt: '2026-10-20T01:00:00Z',
      banReason: 'Vi phạm điều khoản',
    }
    const updatedDetail: AdminUserDetail = {
      ...bannedDetail,
      roles: [
        ...bannedDetail.roles,
        { code: 'TEACHER', grantedAt: '2026-10-23T08:00:00Z', grantedBy: currentAdmin.id },
      ],
    }
    getAdminUserDetailMock.mockResolvedValueOnce(bannedDetail).mockResolvedValueOnce(updatedDetail)
    grantAdminUserRoleMock.mockResolvedValue()
    renderPage()
    const user = userEvent.setup()

    await screen.findByRole('heading', { name: 'Minh Anh' })
    expect(screen.getByText('Role có hiệu lực sau khi gỡ cấm')).toBeVisible()

    await user.selectOptions(screen.getByLabelText('Vai trò cần cấp'), 'TEACHER')
    await user.click(screen.getByRole('button', { name: 'Cấp vai trò' }))
    const dialog = screen.getByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Cấp vai trò' }))

    const rolesTable = screen.getByRole('table', { name: 'Vai trò đã cấp cho tài khoản' })
    expect(await within(rolesTable).findByText('Giáo viên')).toBeVisible()
    expect(grantAdminUserRoleMock).toHaveBeenCalledWith(101, { roleCode: 'TEACHER' })
    expect(getAdminUserDetailMock).toHaveBeenCalledTimes(2)
    expect(screen.getByText('Role có hiệu lực sau khi gỡ cấm')).toBeVisible()
  })

  it('sau khi tự thu hồi role không phải ADMIN, cập nhật AuthContext từ GET mới nhất', async () => {
    const selfDetail: AdminUserDetail = {
      ...detail,
      id: 103,
      displayName: 'Quản trị viên',
      email: 'admin@example.com',
      roles: [
        { code: 'ADMIN', grantedAt: '2026-10-20T01:00:00Z', grantedBy: null },
        { code: 'LEARNER', grantedAt: '2026-10-20T02:00:00Z', grantedBy: 103 },
      ],
    }
    const refreshed = { ...selfDetail, roles: [selfDetail.roles[0]!] }
    getAdminUserDetailMock.mockResolvedValueOnce(selfDetail).mockResolvedValueOnce(refreshed)
    revokeAdminUserRoleMock.mockResolvedValue()
    const setAuthenticatedUser = vi.fn()
    renderPage({
      currentUser: { ...currentAdmin, roles: ['ADMIN', 'LEARNER'] },
      entry: '/admin/users/103',
      setAuthenticatedUser,
    })
    const user = userEvent.setup()

    await screen.findByRole('heading', { name: 'Quản trị viên' })
    const enabledRevoke = screen
      .getAllByRole('button', { name: 'Thu hồi' })
      .find((button) => !button.hasAttribute('disabled'))
    expect(enabledRevoke).toBeDefined()
    await user.click(enabledRevoke!)
    await user.click(screen.getByRole('button', { name: 'Thu hồi vai trò' }))

    await waitFor(() =>
      expect(setAuthenticatedUser).toHaveBeenCalledWith({
        ...currentAdmin,
        roles: ['ADMIN'],
      }),
    )
    expect(revokeAdminUserRoleMock).toHaveBeenCalledWith(103, 'LEARNER')
    expect(getAdminUserDetailMock).toHaveBeenCalledTimes(2)
  })
})
