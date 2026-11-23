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

function renderGuest(initialPath = '/') {
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
      <MemoryRouter initialEntries={[initialPath]}>
        <PublicLayout>Nội dung công khai</PublicLayout>
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

describe('PublicLayout', () => {
  it('shows login and registration actions to guests', () => {
    renderGuest()

    expect(screen.getByRole('link', { name: 'Đăng nhập' })).toHaveAttribute('href', '/login')
    expect(screen.getByRole('link', { name: 'Đăng ký' })).toHaveAttribute('href', '/register')
    expect(screen.getByRole('link', { name: 'Tra từ điển' })).toHaveAttribute('href', '/dictionary')
  })

  it.each([
    ['the page being read', '/dictionary?q=a#x', '?redirect=%2Fdictionary%3Fq%3Da%23x'],
    ['the target already being carried', '/login?redirect=%2Flearn%2Ftopics', '?redirect=%2Flearn%2Ftopics'],
    ['nothing from the landing page', '/#khoa-hoc', ''],
    ['nothing from the email verification page', '/verify-email?token=secret', ''],
    ['nothing from the password reset page', '/reset-password?token=secret', ''],
    ['nothing from an unknown page', '/khong-co-trang', ''],
  ])('keeps %s when a guest signs in or registers from the header', (_name, path, query) => {
    renderGuest(path)

    expect(screen.getByRole('link', { name: 'Đăng nhập' })).toHaveAttribute('href', `/login${query}`)
    expect(screen.getByRole('link', { name: 'Đăng ký' })).toHaveAttribute('href', `/register${query}`)
  })

  it.each([
    ['Tra cứu', 'Video và podcast', '/references/media'],
    ['Tra cứu', 'Tra theo pinyin', '/dictionary?type=PINYIN'],
    ['Cộng đồng', 'Theo chủ đề', '/leaderboards/topics'],
    ['Cộng đồng', 'Cuộc thi', '/contests'],
    ['Giới thiệu', 'Cố vấn chuyên môn', '/#co-van'],
  ])('opens the %s menu for guests and lists %s', async (menu, label, href) => {
    renderGuest()
    expect(screen.queryByRole('link', { name: label })).not.toBeInTheDocument()
    const button = screen.getByRole('button', { name: menu })
    await userEvent.click(button)
    expect(button).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByRole('link', { name: label })).toHaveAttribute('href', href)
  })

  it('marks learning features as needing sign-in and closes the menu with Escape or a click outside', async () => {
    renderGuest()
    const button = screen.getByRole('button', { name: 'Luyện tập' })
    await userEvent.click(button)
    expect(screen.getByText(/Cần đăng nhập/)).toBeVisible()
    await userEvent.keyboard('{Escape}')
    expect(button).toHaveAttribute('aria-expanded', 'false')
    expect(button).toHaveFocus()
    await userEvent.click(button)
    await userEvent.click(screen.getByText('Nội dung công khai'))
    expect(screen.queryByText(/Cần đăng nhập/)).not.toBeInTheDocument()
  })

  it('closes the menu after a link is followed', async () => {
    renderGuest()
    await userEvent.click(screen.getByRole('button', { name: 'Cộng đồng' }))
    await userEvent.click(screen.getByRole('link', { name: 'Bài viết' }))
    expect(screen.queryByRole('link', { name: 'Bài viết' })).not.toBeInTheDocument()
  })

  it('offers the same menus on small screens', async () => {
    renderGuest()
    await userEvent.click(screen.getByRole('button', { name: 'Mở menu' }))
    const mobile = screen.getByRole('navigation', { name: 'Điều hướng trên thiết bị di động' })
    for (const label of ['Giới thiệu', 'Luyện tập', 'Tra cứu', 'Cộng đồng']) {
      expect(within(mobile).getByText(label)).toBeVisible()
    }
    await userEvent.click(screen.getByRole('button', { name: 'Đóng menu' }))
    expect(mobile).not.toBeInTheDocument()
  })

  it('shows the account menu to authenticated users', () => {
    renderLayout(<PublicLayout>Nội dung công khai</PublicLayout>)

    expect(screen.getAllByText('Người học')).toHaveLength(2)
    expect(screen.queryByRole('link', { name: 'Đăng nhập' })).not.toBeInTheDocument()
  })

  it('keeps the full menus for a signed-in learner and adds their own pages', async () => {
    renderLayout(<PublicLayout>Nội dung công khai</PublicLayout>)

    await userEvent.click(screen.getByRole('button', { name: 'Luyện tập' }))
    expect(screen.getByRole('link', { name: 'Bảng học tập' })).toHaveAttribute('href', '/learn')
    expect(screen.getByRole('link', { name: 'Tiến độ học tập' })).toHaveAttribute(
      'href',
      '/learn/progress',
    )
    expect(screen.queryByText(/Cần đăng nhập/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Công việc' })).not.toBeInTheDocument()
  })

  it('lists each staff role’s pages under Công việc, once each', async () => {
    renderLayout(<PublicLayout>Nội dung công khai</PublicLayout>, {
      user: { ...learner, roles: ['TEACHER', 'CONTENT_EDITOR', 'ADMIN'] },
    })

    await userEvent.click(screen.getByRole('button', { name: 'Công việc' }))
    const panel = document.getElementById('public-menu')!
    for (const title of ['Giáo viên', 'Biên tập nội dung', 'Quản trị']) {
      expect(within(panel).getByText(title)).toBeVisible()
    }
    expect(within(panel).getAllByRole('link', { name: 'Kho câu hỏi' })).toHaveLength(1)
    expect(within(panel).getByRole('link', { name: 'Người dùng' })).toHaveAttribute(
      'href',
      '/admin/users',
    )
    expect(within(panel).queryByText('Kiểm duyệt')).not.toBeInTheDocument()
  })
})

describe('LearnerLayout', () => {
  it('keeps desktop destinations and uses four links plus More on mobile', async () => {
    renderLayout(<LearnerLayout gameOrigin="http://localhost:5174">Nội dung học tập</LearnerLayout>)

    const desktopNavigation = screen.getByRole('navigation', { name: 'Học tập' })
    expect(within(desktopNavigation).getAllByRole('link')).toHaveLength(8)
    expect(within(desktopNavigation).getByRole('link', { name: 'Chủ đề' })).toHaveAttribute(
      'aria-current',
      'page',
    )
    // The dashboard sits at /learn, the parent of every study route, so it must not light up there.
    const dashboard = within(desktopNavigation).getByRole('link', { name: 'Bảng học tập' })
    expect(dashboard).toHaveAttribute('href', '/learn')
    expect(dashboard).not.toHaveAttribute('aria-current')
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
    expect(within(supplemental).getByRole('link', { name: 'Chữ Hán' })).toHaveAttribute(
      'href',
      '/learn/characters/recognition',
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
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('tells the user they are still signed in when sign-out fails, then lets them retry', async () => {
    const event = userEvent.setup()
    const { signOut } = renderLayout(<LearnerLayout>Nội dung học tập</LearnerLayout>)
    signOut.mockRejectedValueOnce(new Error('network unavailable'))
    const button = screen.getByRole('button', { name: 'Đăng xuất' })

    await event.click(button)
    expect(await screen.findByRole('alert')).toHaveTextContent('bạn vẫn đang đăng nhập')

    await event.click(button)
    expect(signOut).toHaveBeenCalledTimes(2)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('keeps the desktop sidebar bounded and scrollable for zoom and uses min-h-touch min-w-touch on the More close button', async () => {
    const event = userEvent.setup()
    renderLayout(<LearnerLayout>Nội dung học tập</LearnerLayout>)

    const learningNav = screen.getByRole('navigation', { name: 'Học tập' })
    const stickySidebarContainer = learningNav.parentElement
    expect(stickySidebarContainer).toHaveClass(
      'sticky',
      'top-16',
      'max-h-[calc(100dvh-4rem)]',
      'overflow-y-auto',
    )

    const moreButton = screen.getByRole('button', { name: 'Thêm' })
    await event.click(moreButton)

    const closeButton = screen.getByRole('button', { name: 'Đóng menu bổ sung' })
    expect(closeButton).toHaveClass('min-h-touch', 'min-w-touch')
    expect(closeButton).not.toHaveClass('size-touch')

    await event.click(closeButton)
    expect(screen.queryByRole('navigation', { name: 'Điều hướng bổ sung' })).not.toBeInTheDocument()
    expect(moreButton).toHaveFocus()
  })
})

describe('AdminLayout', () => {
  it('shows settings active for ADMIN on desktop and mobile', () => {
    renderLayout(<AdminLayout>Cài đặt</AdminLayout>, {
      initialPath: '/admin/settings',
      user: { ...learner, roles: ['ADMIN'] },
    })
    for (const name of ['Quản trị', 'Quản trị trên thiết bị di động']) {
      const link = within(screen.getByRole('navigation', { name })).getByRole('link', {
        name: 'Cài đặt hệ thống',
      })
      expect(link).toHaveAttribute('href', '/admin/settings')
      expect(link).toHaveAttribute('aria-current', 'page')
    }
  })

  it('keeps the AdminLayout desktop sidebar bounded and scrollable for zoom', () => {
    renderLayout(<AdminLayout>Cài đặt</AdminLayout>, {
      initialPath: '/admin/settings',
      user: { ...learner, roles: ['ADMIN'] },
    })
    const adminNav = screen.getByRole('navigation', { name: 'Quản trị' })
    expect(adminNav).toHaveClass(
      'sticky',
      'top-16',
      'max-h-[calc(100dvh-4rem)]',
      'overflow-y-auto',
    )
  })
  it.each(['CONTENT_EDITOR', 'LEARNER', 'TEACHER', 'MODERATOR', 'ACCOUNTANT'] as const)(
    'hides settings from %s',
    (role) => {
      renderLayout(<AdminLayout>Nội dung</AdminLayout>, { user: { ...learner, roles: [role] } })
      expect(screen.queryByRole('link', { name: 'Cài đặt hệ thống' })).not.toBeInTheDocument()
    },
  )
  it('shows the prerequisite destination active on desktop and mobile only for CONTENT_EDITOR', () => {
    renderLayout(<AdminLayout>Nội dung tiên quyết</AdminLayout>, {
      initialPath: '/content/topics/prerequisites',
      user: { ...learner, roles: ['CONTENT_EDITOR'] },
    })
    for (const name of ['Quản trị', 'Quản trị trên thiết bị di động']) {
      const link = within(screen.getByRole('navigation', { name })).getByRole('link', {
        name: 'Tiên quyết chủ đề',
      })
      expect(link).toHaveAttribute('href', '/content/topics/prerequisites')
      expect(link).toHaveAttribute('aria-current', 'page')
    }
  })
  it.each(['ADMIN', 'LEARNER', 'TEACHER', 'MODERATOR', 'ACCOUNTANT'] as const)(
    'hides prerequisites from %s alone',
    (role) => {
      renderLayout(<AdminLayout>Nội dung</AdminLayout>, { user: { ...learner, roles: [role] } })
      expect(screen.queryByRole('link', { name: 'Tiên quyết chủ đề' })).not.toBeInTheDocument()
    },
  )
  it('shows the teacher grading menu on desktop and mobile without moderator or editor permissions', () => {
    renderLayout(<AdminLayout>Nội dung chấm bài</AdminLayout>, {
      initialPath: '/teacher/grading/42',
      user: { ...learner, roles: ['TEACHER'] },
    })
    for (const label of ['Quản trị', 'Quản trị trên thiết bị di động']) {
      const nav = screen.getByRole('navigation', { name: label })
      expect(within(nav).getAllByRole('link')).toHaveLength(3)
      expect(within(nav).getByRole('link', { name: 'Kho câu hỏi' })).toHaveAttribute(
        'href',
        '/content/questions',
      )
      expect(within(nav).getByRole('link', { name: 'Duyệt câu hỏi AI' })).toHaveAttribute(
        'href',
        '/content/ai-review',
      )
      expect(within(nav).getByRole('link', { name: 'Chấm bài viết' })).toHaveAttribute(
        'aria-current',
        'page',
      )
      expect(within(nav).queryByRole('link', { name: 'Nhập dữ liệu' })).not.toBeInTheDocument()
    }
  })

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
      expect(within(navigation).getAllByRole('link')).toHaveLength(14)
      // AI review belongs to TEACHER only; CONTENT_EDITOR and ADMIN do not get it.
      expect(within(navigation).queryByRole('link', { name: 'Duyệt câu hỏi AI' })).not.toBeInTheDocument()
      for (const label of [
        'Lô AI',
        'Kho câu hỏi',
        'Quản lý đề thi',
        'Nhập dữ liệu',
        'Tiên quyết chủ đề',
        'Người dùng',
        'Cài đặt hệ thống',
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
    ['TEACHER'],
    ['TEACHER', 'CONTENT_EDITOR'],
    ['ADMIN', 'TEACHER'],
  ] as const)('shows one AI review link on each surface for explicit roles %j', (...roles) => {
    renderLayout(<AdminLayout>Duyệt</AdminLayout>, {
      initialPath: '/content/ai-review',
      user: { ...learner, roles: [...roles] },
    })
    for (const name of ['Quản trị', 'Quản trị trên thiết bị di động']) {
      const nav = within(screen.getByRole('navigation', { name }))
      expect(nav.getAllByRole('link', { name: 'Duyệt câu hỏi AI' })).toHaveLength(1)
      expect(nav.getByRole('link', { name: 'Duyệt câu hỏi AI' })).toHaveAttribute(
        'aria-current',
        'page',
      )
    }
  })
  it.each(['ADMIN', 'LEARNER', 'MODERATOR', 'ACCOUNTANT', 'CONTENT_EDITOR'] as const)(
    'hides AI review from %s alone',
    (role) => {
      renderLayout(<AdminLayout>Duyệt</AdminLayout>, { user: { ...learner, roles: [role] } })
      expect(screen.queryByRole('link', { name: 'Duyệt câu hỏi AI' })).not.toBeInTheDocument()
    },
  )
  it.each([
    [
      ['CONTENT_EDITOR'],
      [
        'Kho câu hỏi',
        'Nhập dữ liệu',
        'Tài nguyên tham khảo',
        'Tiên quyết chủ đề',
        'Cuộc thi',
        'Quản lý đề thi',
      ],
      ['Người dùng', ...financialLabels],
    ],
    [
      ['ADMIN'],
      ['Người dùng', ...financialLabels],
      [
        'Kho câu hỏi',
        'Nhập dữ liệu',
        'Tài nguyên tham khảo',
        'Tiên quyết chủ đề',
        'Cuộc thi',
        'Quản lý đề thi',
      ],
    ],
    [
      ['MODERATOR'],
      ['Bài chờ duyệt', 'Báo cáo vi phạm'],
      ['Kho câu hỏi', 'Người dùng', ...financialLabels],
    ],
    [['TEACHER'], ['Kho câu hỏi'], ['Người dùng', ...financialLabels]],
    [['LEARNER'], [], ['Kho câu hỏi', 'Người dùng', ...financialLabels]],
    [['ACCOUNTANT'], [], ['Kho câu hỏi', 'Người dùng', ...financialLabels]],
    [
      ['CONTENT_EDITOR', 'ADMIN'],
      [
        'Quản lý đề thi',
        'Nhập dữ liệu',
        'Người dùng',
        'Tài nguyên tham khảo',
        'Tiên quyết chủ đề',
        'Cuộc thi',
        ...financialLabels,
      ],
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
  it.each([{ roles: ['TEACHER'] }, { roles: ['LEARNER', 'TEACHER'] }] as const)(
    'shows the teacher entry for exact roles %j',
    async ({ roles }) => {
      renderLayout(<PublicLayout>Nội dung</PublicLayout>, {
        user: { ...learner, roles: [...roles] },
      })
      await userEvent.click(document.querySelector('summary')!)
      expect(screen.getByRole('link', { name: 'Chấm bài viết' })).toHaveAttribute(
        'href',
        '/teacher/grading',
      )
    },
  )
  it('does not show a teacher area to an ordinary learner', async () => {
    renderLayout(<PublicLayout>Nội dung</PublicLayout>)
    await userEvent.click(document.querySelector('summary')!)
    expect(screen.queryByRole('link', { name: 'Chấm bài viết' })).not.toBeInTheDocument()
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
    expect(screen.getByRole('link', { name: 'Học tập' })).toHaveAttribute('href', '/learn')
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
      '/learn',
    )
  })

  it('hides the area group when only one area is available', () => {
    renderLayout(<PublicLayout>Nội dung</PublicLayout>)
    expect(screen.queryByText('Khu vực của bạn')).not.toBeInTheDocument()
  })
})

describe('AI run navigation', () => {
  it.each([['ADMIN'], ['ADMIN', 'LEARNER', 'CONTENT_EDITOR']] as const)(
    'shows ADMIN destination for %j',
    (...groups) => {
      renderLayout(<AdminLayout>Nội dung</AdminLayout>, {
        initialPath: '/admin/ai-runs',
        user: { ...learner, roles: [...groups.flat()] },
      })
      for (const name of ['Quản trị', 'Quản trị trên thiết bị di động']) {
        const link = within(screen.getByRole('navigation', { name })).getByRole('link', {
          name: 'Lô AI',
        })
        expect(link).toHaveAttribute('href', '/admin/ai-runs')
        expect(link).toHaveAttribute('aria-current', 'page')
      }
    },
  )
  it.each(['CONTENT_EDITOR', 'LEARNER', 'TEACHER', 'MODERATOR', 'ACCOUNTANT'] as const)(
    'hides from %s',
    (role) => {
      renderLayout(<AdminLayout>Nội dung</AdminLayout>, { user: { ...learner, roles: [role] } })
      expect(screen.queryByRole('link', { name: 'Lô AI' })).not.toBeInTheDocument()
    },
  )
})
