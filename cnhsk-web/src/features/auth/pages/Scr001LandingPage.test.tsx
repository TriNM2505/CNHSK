import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import type { CurrentUser } from '../types'

import Scr001LandingPage from './Scr001LandingPage'

const multiAreaUser: CurrentUser = {
  displayName: 'Người dùng nhiều khu vực',
  email: 'user@example.com',
  emailVerified: true,
  id: 1,
  roles: ['LEARNER', 'CONTENT_EDITOR', 'ADMIN'],
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
        <Scr001LandingPage />
      </MemoryRouter>
    </AuthContext.Provider>,
  )

  return value
}

describe('Scr001LandingPage', () => {
  it('hiện hero và phần giới thiệu trong lúc kiểm tra phiên', () => {
    renderPage({ status: 'loading' })

    expect(
      screen.getByRole('heading', { name: 'Học tiếng Trung rõ ràng, từng bước một' }),
    ).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Học theo cách phù hợp với bạn' })).toBeVisible()
    expect(screen.getByLabelText('Đang kiểm tra phiên đăng nhập')).toBeVisible()
  })

  it('khi kiểm tra phiên lỗi vẫn cho đăng nhập, đăng ký và thử lại', async () => {
    const auth = renderPage({
      error: new ApiError({
        errorCode: 'SESSION_CHECK_FAILED',
        message: 'Không thể kiểm tra phiên.',
        requestId: 'req-landing',
        status: 500,
      }),
      status: 'error',
    })

    expect(screen.getByRole('link', { name: 'Bắt đầu học' })).toHaveAttribute('href', '/register')
    expect(screen.getByRole('link', { name: 'Đăng nhập' })).toHaveAttribute('href', '/login')
    expect(screen.getByText('Chưa kiểm tra được phiên đăng nhập.')).toBeVisible()

    await userEvent.setup().click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(auth.reloadCurrentUser).toHaveBeenCalledOnce()
  })

  it('hiện mọi khu vực của người đã đăng nhập và không gắn link cho khu vực đang dựng', () => {
    renderPage({ status: 'authenticated', user: multiAreaUser })

    expect(screen.getByRole('heading', { name: 'Khu vực của bạn' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Học tập' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Nội dung' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Quản trị' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Mở học tập' })).toHaveAttribute(
      'href',
      '/learn/topics',
    )
  })

  it('các khối giới thiệu không phải liên kết', () => {
    renderPage()

    for (const name of ['Chủ đề', 'Ngữ pháp', 'Phát âm', 'Chữ Hán', 'Game']) {
      expect(screen.getByRole('heading', { name })).toBeVisible()
      expect(screen.queryByRole('link', { name })).not.toBeInTheDocument()
    }
    expect(screen.queryByText(/mức thành thạo/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/bảng xếp hạng/i)).not.toBeInTheDocument()
  })
})
