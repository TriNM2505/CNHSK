import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import type { CurrentUser } from '@/features/auth/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth/AuthContext'
import { AdminLayout, LearnerLayout, PublicLayout, RoleLayout } from '@/shared/layout'

const learner: CurrentUser = {
  id: 1,
  displayName: 'Người học',
  email: 'learner@example.com',
  emailVerified: true,
  roles: ['LEARNER'],
}

function LocationValue() {
  const location = useLocation()
  return <output aria-label="Đường dẫn hiện tại">{location.pathname}</output>
}

function renderLayout(
  element: React.ReactNode,
  {
    initialPath = '/learn/topics',
    user = learner,
  }: { initialPath?: string; user?: CurrentUser } = {},
) {
  const signOut = vi.fn().mockResolvedValue(undefined)
  const value: AuthContextValue = {
    error: null,
    reloadCurrentUser: vi.fn().mockResolvedValue(undefined),
    setAuthenticatedUser: vi.fn(),
    signOut,
    status: 'authenticated',
    user,
  }

  const result = render(
    <AuthContext.Provider value={value}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route
            element={
              <>
                {element}
                <LocationValue />
              </>
            }
            path="*"
          />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  )

  return { ...result, signOut }
}

describe('PublicLayout', () => {
  it('shows login and registration actions to guests', () => {
    const value: AuthContextValue = {
      error: null,
      reloadCurrentUser: vi.fn().mockResolvedValue(undefined),
      setAuthenticatedUser: vi.fn(),
      signOut: vi.fn().mockResolvedValue(undefined),
      status: 'guest',
      user: null,
    }

    render(
      <AuthContext.Provider value={value}>
        <MemoryRouter>
          <PublicLayout>Nội dung công khai</PublicLayout>
        </MemoryRouter>
      </AuthContext.Provider>,
    )

    expect(screen.getByRole('link', { name: 'Đăng nhập' })).toHaveAttribute('href', '/login')
    expect(screen.getByRole('link', { name: 'Đăng ký' })).toHaveAttribute('href', '/register')
  })

  it('shows the account menu to authenticated users', () => {
    renderLayout(<PublicLayout>Nội dung công khai</PublicLayout>)

    expect(screen.getAllByText('Người học')).toHaveLength(2)
    expect(screen.queryByRole('link', { name: 'Đăng nhập' })).not.toBeInTheDocument()
  })
})

describe('LearnerLayout', () => {
  it('renders four internal destinations and the configured game link', () => {
    renderLayout(<LearnerLayout gameOrigin="http://localhost:5174">Nội dung học tập</LearnerLayout>)

    const desktopNavigation = screen.getByRole('navigation', { name: 'Học tập' })
    expect(within(desktopNavigation).getAllByRole('link')).toHaveLength(5)
    expect(within(desktopNavigation).getByRole('link', { name: 'Chủ đề' })).toHaveAttribute(
      'aria-current',
      'page',
    )
    const gameLink = within(desktopNavigation).getByRole('link', { name: 'Game' })
    expect(gameLink).toHaveAttribute('href', 'http://localhost:5174')
    expect(gameLink).not.toHaveAttribute('target')
  })

  it('shows the verification banner only when email is not verified', () => {
    const { rerender } = renderLayout(
      <LearnerLayout gameOrigin="http://localhost:5174">Nội dung học tập</LearnerLayout>,
      { user: { ...learner, emailVerified: false } },
    )

    expect(screen.getByText('Xác minh email để dùng đầy đủ')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Xác minh email' })).toHaveAttribute(
      'href',
      '/verify-email',
    )

    const value: AuthContextValue = {
      error: null,
      reloadCurrentUser: vi.fn().mockResolvedValue(undefined),
      setAuthenticatedUser: vi.fn(),
      signOut: vi.fn().mockResolvedValue(undefined),
      status: 'authenticated',
      user: learner,
    }
    rerender(
      <AuthContext.Provider value={value}>
        <MemoryRouter>
          <LearnerLayout>Nội dung học tập</LearnerLayout>
        </MemoryRouter>
      </AuthContext.Provider>,
    )

    expect(screen.queryByText('Xác minh email để dùng đầy đủ')).not.toBeInTheDocument()
  })

  it('uses signOut from the authentication context', async () => {
    const event = userEvent.setup()
    const { signOut } = renderLayout(<LearnerLayout>Nội dung học tập</LearnerLayout>)

    await event.click(screen.getByRole('button', { name: 'Đăng xuất' }))

    expect(signOut).toHaveBeenCalledOnce()
  })
})

describe('AdminLayout', () => {
  it.each([
    [['CONTENT_EDITOR'], ['Nhập dữ liệu'], ['Người dùng']],
    [['ADMIN'], ['Người dùng'], ['Nhập dữ liệu']],
    [['CONTENT_EDITOR', 'ADMIN'], ['Nhập dữ liệu', 'Người dùng'], []],
  ] as const)('shows only the areas granted by exact roles %j', (roles, visible, hidden) => {
    renderLayout(<AdminLayout>Nội dung quản trị</AdminLayout>, {
      initialPath: roles.some((role) => role === 'ADMIN') ? '/admin/users' : '/content/imports',
      user: { ...learner, roles: [...roles] },
    })

    const navigation = screen.getByRole('navigation', { name: 'Quản trị' })
    visible.forEach((label) =>
      expect(within(navigation).getByRole('link', { name: label })).toBeVisible(),
    )
    hidden.forEach((label) =>
      expect(within(navigation).queryByRole('link', { name: label })).not.toBeInTheDocument(),
    )
  })
})

describe('RoleLayout', () => {
  it('selects LearnerLayout when the user has LEARNER', () => {
    renderLayout(<RoleLayout>Nội dung đổi mật khẩu</RoleLayout>)
    expect(screen.getByRole('navigation', { name: 'Học tập' })).toBeInTheDocument()
  })

  it('selects AdminLayout for a non-learner administrator', () => {
    renderLayout(<RoleLayout>Nội dung đổi mật khẩu</RoleLayout>, {
      user: { ...learner, roles: ['ADMIN'] },
    })
    expect(screen.getByRole('navigation', { name: 'Quản trị' })).toBeInTheDocument()
  })

  it('selects PublicLayout when the user has no supported area role', () => {
    renderLayout(<RoleLayout>Nội dung đổi mật khẩu</RoleLayout>, {
      user: { ...learner, roles: [] },
    })
    expect(screen.queryByRole('navigation', { name: 'Học tập' })).not.toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'Quản trị' })).not.toBeInTheDocument()
    expect(screen.getByText('Nội dung đổi mật khẩu')).toBeVisible()
  })
})

describe('AccountMenu area switching', () => {
  it('shows all exact areas for LEARNER plus ADMIN and lets the user switch', async () => {
    const event = userEvent.setup()
    renderLayout(<PublicLayout>Nội dung</PublicLayout>, {
      initialPath: '/',
      user: { ...learner, roles: ['LEARNER', 'ADMIN'] },
    })

    const summary = document.querySelector('summary')
    expect(summary).not.toBeNull()
    await event.click(summary!)
    expect(screen.getByText('Khu vực của bạn')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Học tập' })).toHaveAttribute('href', '/learn/topics')
    expect(screen.getByRole('link', { name: 'Quản trị người dùng' })).toHaveAttribute(
      'href',
      '/admin/users',
    )

    await event.click(screen.getByRole('link', { name: 'Quản trị người dùng' }))
    expect(screen.getByRole('status', { name: 'Đường dẫn hiện tại' })).toHaveTextContent(
      '/admin/users',
    )
    await event.click(screen.getByRole('link', { name: 'Học tập' }))
    expect(screen.getByRole('status', { name: 'Đường dẫn hiện tại' })).toHaveTextContent(
      '/learn/topics',
    )
  })

  it('hides the area group when only one area is available', () => {
    renderLayout(<PublicLayout>Nội dung</PublicLayout>)
    expect(screen.queryByText('Khu vực của bạn')).not.toBeInTheDocument()
  })
})
