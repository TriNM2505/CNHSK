import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import type { CurrentUser } from '../types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import Scr079AccessDeniedPage from './Scr079AccessDeniedPage'

const user: CurrentUser = {
  displayName: 'Người dùng mẫu',
  email: 'user@example.com',
  emailVerified: true,
  id: 1,
  roles: [],
}

function renderPage(overrides: Partial<AuthContextValue> = {}) {
  const value: AuthContextValue = {
    error: null,
    reloadCurrentUser: vi.fn().mockResolvedValue(undefined),
    setAuthenticatedUser: vi.fn(),
    signOut: vi.fn().mockResolvedValue(undefined),
    status: 'guest',
    user: null,
    ...overrides,
  }

  render(
    <AuthContext.Provider value={value}>
      <MemoryRouter>
        <Scr079AccessDeniedPage />
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

describe('Scr079AccessDeniedPage', () => {
  it('guest thấy thông báo thiếu quyền, nút đăng nhập và về trang chủ', () => {
    renderPage()

    expect(screen.getByRole('heading', { name: 'Không có quyền truy cập' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Đăng nhập' })).toHaveAttribute('href', '/login')
    expect(screen.getByRole('link', { name: 'Về trang chủ' })).toHaveAttribute('href', '/')
  })

  it.each([
    ['TEACHER', 'Giáo viên'],
    ['MODERATOR', 'Kiểm duyệt'],
    ['ACCOUNTANT', 'Kế toán'],
  ] as const)('role %s chưa có khu vực thấy thông báo đang xây dựng', (role, label) => {
    renderPage({ status: 'authenticated', user: { ...user, roles: [role] } })

    expect(
      screen.getByRole('heading', { name: `Khu vực ${label} đang được xây dựng` }),
    ).toBeVisible()
    expect(screen.getAllByRole('link')).toHaveLength(1)
    expect(screen.getByRole('link', { name: 'Về trang chủ' })).toHaveAttribute('href', '/')
    expect(screen.queryByText('Không có quyền truy cập')).not.toBeInTheDocument()
  })

  it('user có khu vực đã dựng thấy thiếu quyền và có đường về an toàn', () => {
    renderPage({ status: 'authenticated', user: { ...user, roles: ['LEARNER'] } })

    expect(screen.getByRole('heading', { name: 'Không có quyền truy cập' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Về khu vực học tập' })).toHaveAttribute(
      'href',
      '/learn/topics',
    )
  })

  it('user có nhiều khu vực đã dựng về landing để tự chọn, không tự xếp ưu tiên', () => {
    renderPage({
      status: 'authenticated',
      user: { ...user, roles: ['LEARNER', 'CONTENT_EDITOR', 'ADMIN'] },
    })

    expect(screen.getByRole('link', { name: 'Về trang chủ' })).toHaveAttribute('href', '/')
  })
})
