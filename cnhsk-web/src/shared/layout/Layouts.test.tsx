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

const financialLinks = [
  { label: 'Lô mã thẻ', mobileLabel: 'Lô thẻ', to: '/admin/card-batches' },
  { label: 'Gói dịch vụ', mobileLabel: 'Gói', to: '/admin/plans' },
  { label: 'Sổ cái điểm', mobileLabel: 'Sổ cái', to: '/admin/ledger' },
] as const
const financialLabels = financialLinks.map(({ label }) => label)

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
    expect(screen.getByRole('link', { name: 'Tài liệu tham khảo' })).toHaveAttribute(
      'href',
      '/references/media',
    )
    expect(screen.getByRole('link', { name: 'Bảng xếp hạng' })).toHaveAttribute(
      'href',
      '/leaderboards/topics',
    )
  })

  it('shows the account menu to authenticated users', () => {
    renderLayout(<PublicLayout>Nội dung công khai</PublicLayout>)

    expect(screen.getAllByText('Người học')).toHaveLength(2)
    expect(screen.queryByRole('link', { name: 'Đăng nhập' })).not.toBeInTheDocument()
  })
})

describe('LearnerLayout', () => {
  it('keeps desktop destinations and uses four links plus More on mobile', async () => {
    renderLayout(<LearnerLayout gameOrigin="http://localhost:5174">Nội dung học tập</LearnerLayout>)

    const desktopNavigation = screen.getByRole('navigation', { name: 'Học tập' })
    expect(within(desktopNavigation).getAllByRole('link')).toHaveLength(7)
    expect(within(desktopNavigation).getByRole('link', { name: 'Chủ đề' })).toHaveAttribute(
      'aria-current',
      'page',
    )
    const gameLink = within(desktopNavigation).getByRole('link', { name: 'Game' })
    expect(gameLink).toHaveAttribute('href', 'http://localhost:5174')
    expect(gameLink).not.toHaveAttribute('target')
    const mobileNavigation = screen.getByRole('navigation', {
      name: 'Học tập trên thiết bị di động',
    })
    expect(
      within(desktopNavigation).getByRole('link', { name: 'Tài liệu tham khảo' }),
    ).toHaveAttribute('href', '/references/media')
    expect(within(mobileNavigation).getAllByRole('link')).toHaveLength(4)
    const more = within(mobileNavigation).getByRole('button', { name: 'Thêm' })
    expect(more).toHaveAttribute('aria-expanded', 'false')
    expect(more).toHaveAttribute('aria-controls', 'learner-more-menu')
    await userEvent.click(more)
    expect(more).toHaveAttribute('aria-expanded', 'true')
    const supplemental = screen.getByRole('navigation', { name: 'Điều hướng bổ sung' })
    expect(within(supplemental).getByRole('link', { name: 'Tài liệu tham khảo' })).toHaveAttribute(
      'href',
      '/references/media',
    )
    expect(within(supplemental).getByRole('link', { name: 'Game' })).toHaveAttribute(
      'href',
      'http://localhost:5174',
    )
    expect(within(supplemental).getByRole('link', { name: 'Quiz' })).toHaveAttribute(
      'href',
      '/community/quizzes',
    )
    expect(within(supplemental).getByRole('link', { name: 'Cuộc thi' })).toHaveAttribute(
      'href',
      '/contests',
    )
    expect(within(supplemental).getByRole('link', { name: 'Quiz' })).toHaveAttribute(
      'aria-label',
      'Quiz',
    )
    expect(supplemental.querySelector('span[aria-label]')).toBeNull()
    await userEvent.keyboard('{Escape}')
    expect(more).toHaveAttribute('aria-expanded', 'false')
    expect(more).toHaveFocus()
    expect(screen.queryByRole('navigation', { name: 'Điều hướng bổ sung' })).not.toBeInTheDocument()
  })

  it('keeps Quiz active on play routes and closes More after navigation', async () => {
    renderLayout(<LearnerLayout>Nội dung</LearnerLayout>, {
      initialPath: '/community/quizzes/43/play',
    })
    const community = screen.getByRole('navigation', { name: 'Cộng đồng' })
    expect(within(community).getAllByRole('link')).toHaveLength(3)
    expect(within(community).getByRole('link', { name: 'Quiz' })).toHaveAttribute(
      'aria-current',
      'page',
    )
    expect(within(community).getByRole('link', { name: 'Bảng xếp hạng' })).toHaveAttribute(
      'href',
      '/leaderboards/topics',
    )
    expect(within(community).getByRole('link', { name: 'Cuộc thi' })).toHaveAttribute(
      'href',
      '/contests',
    )
    const more = screen.getByRole('button', { name: 'Thêm' })
    await userEvent.click(more)
    const supplemental = screen.getByRole('navigation', { name: 'Điều hướng bổ sung' })
    expect(within(supplemental).getByRole('link', { name: 'Quiz' })).toHaveAttribute(
      'aria-current',
      'page',
    )
    await userEvent.click(within(supplemental).getByRole('link', { name: 'Quiz' }))
    expect(screen.getByRole('status', { name: 'Đường dẫn hiện tại' })).toHaveTextContent(
      '/community/quizzes',
    )
    expect(more).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('navigation', { name: 'Điều hướng bổ sung' })).not.toBeInTheDocument()
  })

  it('FR015_AT20 keeps Cuộc thi active in Community and closes More after navigation', async () => {
    renderLayout(<LearnerLayout>Nội dung</LearnerLayout>, {
      initialPath: '/contests/2101/attempt',
    })
    const community = screen.getByRole('navigation', { name: 'Cộng đồng' })
    expect(within(community).getByRole('link', { name: 'Cuộc thi' })).toHaveAttribute(
      'aria-current',
      'page',
    )
    expect(within(community).getByRole('link', { name: 'Quiz' })).not.toHaveAttribute(
      'aria-current',
    )
    const more = screen.getByRole('button', { name: 'Thêm' })
    await userEvent.click(more)
    const supplemental = screen.getByRole('navigation', { name: 'Điều hướng bổ sung' })
    const contestLink = within(supplemental).getByRole('link', { name: 'Cuộc thi' })
    expect(contestLink).toHaveAttribute('href', '/contests')
    expect(contestLink).toHaveAttribute('aria-current', 'page')
    await userEvent.click(contestLink)
    expect(screen.getByRole('status', { name: 'Đường dẫn hiện tại' })).toHaveTextContent(
      '/contests',
    )
    expect(more).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('navigation', { name: 'Điều hướng bổ sung' })).not.toBeInTheDocument()
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
  it('shows moderator destinations on desktop and mobile, follows links and marks detail prefixes active', async () => {
    const event = userEvent.setup()
    renderLayout(<AdminLayout>Nội dung kiểm duyệt</AdminLayout>, {
      initialPath: '/moderation/posts/42',
      user: { ...learner, roles: ['MODERATOR'] },
    })
    const desktop = screen.getByRole('navigation', { name: 'Quản trị' })
    const mobile = screen.getByRole('navigation', { name: 'Quản trị trên thiết bị di động' })
    for (const navigation of [desktop, mobile]) {
      expect(within(navigation).getAllByRole('link')).toHaveLength(2)
      expect(within(navigation).getByRole('link', { name: 'Bài chờ duyệt' })).toHaveAttribute(
        'href',
        '/moderation/posts',
      )
      expect(within(navigation).getByRole('link', { name: 'Bài chờ duyệt' })).toHaveAttribute(
        'aria-current',
        'page',
      )
      expect(within(navigation).getByRole('link', { name: 'Báo cáo vi phạm' })).toHaveAttribute(
        'href',
        '/moderation/reports',
      )
      expect(within(navigation).getByRole('link', { name: 'Báo cáo vi phạm' })).not.toHaveAttribute(
        'aria-current',
      )
    }
    await event.click(within(desktop).getByRole('link', { name: 'Báo cáo vi phạm' }))
    expect(screen.getByRole('status', { name: 'Đường dẫn hiện tại' })).toHaveTextContent(
      '/moderation/reports',
    )
    for (const navigation of [desktop, mobile])
      expect(within(navigation).getByRole('link', { name: 'Báo cáo vi phạm' })).toHaveAttribute(
        'aria-current',
        'page',
      )
    await event.click(within(mobile).getByRole('link', { name: 'Bài chờ duyệt' }))
    expect(screen.getByRole('status', { name: 'Đường dẫn hiện tại' })).toHaveTextContent(
      '/moderation/posts',
    )
  })
  it('combines moderator navigation with existing exact-role destinations', () => {
    renderLayout(<AdminLayout>Nội dung quản trị</AdminLayout>, {
      initialPath: '/moderation/reports/9',
      user: { ...learner, roles: ['LEARNER', 'MODERATOR', 'CONTENT_EDITOR', 'ADMIN'] },
    })
    for (const name of ['Quản trị', 'Quản trị trên thiết bị di động']) {
      const navigation = screen.getByRole('navigation', { name })
      expect(within(navigation).getAllByRole('link')).toHaveLength(9)
      for (const label of [
        'Nhập dữ liệu',
        'Người dùng',
        'Bài chờ duyệt',
        'Báo cáo vi phạm',
        'Tài nguyên tham khảo',
        'Cuộc thi',
        ...financialLabels,
      ])
        expect(within(navigation).getByRole('link', { name: label })).toBeInTheDocument()
      expect(within(navigation).getByRole('link', { name: 'Báo cáo vi phạm' })).toHaveAttribute(
        'aria-current',
        'page',
      )
    }
  })
  it.each(['LEARNER', 'ADMIN', 'CONTENT_EDITOR', 'TEACHER', 'ACCOUNTANT'] as const)(
    'does not grant moderator destinations to %s',
    (role) => {
      renderLayout(<AdminLayout>Nội dung quản trị</AdminLayout>, {
        user: { ...learner, roles: [role] },
      })
      expect(screen.queryByRole('link', { name: 'Bài chờ duyệt' })).not.toBeInTheDocument()
      expect(screen.queryByRole('link', { name: 'Báo cáo vi phạm' })).not.toBeInTheDocument()
    },
  )
  it.each([
    [
      ['CONTENT_EDITOR'],
      ['Nhập dữ liệu', 'Tài nguyên tham khảo', 'Cuộc thi'],
      ['Người dùng', ...financialLabels],
    ],
    [
      ['ADMIN'],
      ['Người dùng', ...financialLabels],
      ['Nhập dữ liệu', 'Tài nguyên tham khảo', 'Cuộc thi'],
    ],
    [['MODERATOR'], ['Bài chờ duyệt', 'Báo cáo vi phạm'], ['Người dùng', ...financialLabels]],
    [['TEACHER'], [], ['Người dùng', ...financialLabels]],
    [['LEARNER'], [], ['Người dùng', ...financialLabels]],
    [['ACCOUNTANT'], [], ['Người dùng', ...financialLabels]],
    [
      ['CONTENT_EDITOR', 'ADMIN'],
      ['Nhập dữ liệu', 'Người dùng', 'Tài nguyên tham khảo', 'Cuộc thi', ...financialLabels],
      [],
    ],
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

  it.each(financialLinks)(
    'opens $to and marks its prefixes active on desktop and mobile',
    async ({ label, mobileLabel, to }) => {
      renderLayout(<AdminLayout>Nội dung tài chính</AdminLayout>, {
        initialPath: `${to}/detail`,
        user: { ...learner, roles: ['ADMIN'] },
      })
      for (const name of ['Quản trị', 'Quản trị trên thiết bị di động']) {
        const navigation = within(screen.getByRole('navigation', { name }))
        const link = navigation.getByRole('link', { name: label })
        expect(link).toHaveAttribute('href', to)
        expect(link).toHaveAttribute('aria-current', 'page')
        if (name === 'Quản trị trên thiết bị di động') expect(link).toHaveTextContent(mobileLabel)
      }
      await userEvent.click(
        within(screen.getByRole('navigation', { name: 'Quản trị' })).getByRole('link', {
          name: label,
        }),
      )
      expect(screen.getByRole('status', { name: 'Đường dẫn hiện tại' })).toHaveTextContent(to)
    },
  )
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
  it.each([
    { roles: ['LEARNER'], visible: true },
    { roles: ['LEARNER', 'ADMIN'], visible: true },
    { roles: ['ADMIN'], visible: false },
  ] as const)('shows credits only when LEARNER is assigned: $roles', async ({ roles, visible }) => {
    renderLayout(<PublicLayout>Nội dung</PublicLayout>, {
      user: { ...learner, roles: [...roles] },
    })
    await userEvent.click(document.querySelector('summary')!)
    if (visible) {
      const link = screen.getByRole('link', { name: 'Gói dịch vụ và điểm' })
      expect(link).toBeVisible()
      expect(link).toHaveAttribute('href', '/account/credits')
      await userEvent.click(link)
      expect(screen.getByRole('status', { name: 'Đường dẫn hiện tại' })).toHaveTextContent(
        '/account/credits',
      )
    } else {
      expect(screen.queryByRole('link', { name: 'Gói dịch vụ và điểm' })).not.toBeInTheDocument()
    }
  })

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
