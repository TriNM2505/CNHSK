import { act, render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { appRoutes } from '@/app/routes'
import { getCurrentUser } from '@/features/auth/api/getCurrentUser'
import type { CurrentUser } from '@/features/auth/types'
import { getGrammarPoints } from '@/features/grammar/api/grammar'
import { getPronunciationStages } from '@/features/pronunciation/api/pronunciation'
import { getTopic, getTopics } from '@/features/vocab/api/topics'
import type { TopicDetail } from '@/features/vocab/types'
import { ApiError, AUTH_EXPIRED_EVENT } from '@/shared/api'

vi.mock('@/features/auth/api/getCurrentUser', () => ({
  getCurrentUser: vi.fn(),
}))

vi.mock('@/features/auth/api/signOutCurrentUser', () => ({
  signOutCurrentUser: vi.fn(),
}))

vi.mock('@/features/vocab/api/topics', () => ({
  getTopic: vi.fn(),
  getTopics: vi.fn(),
  startTopicStudySession: vi.fn(),
}))

vi.mock('@/features/grammar/api/grammar', () => ({
  getGrammarLesson: vi.fn(),
  getGrammarPoints: vi.fn(),
  startGrammarLesson: vi.fn(),
}))

vi.mock('@/features/pronunciation/api/pronunciation', () => ({
  getPronunciationStages: vi.fn(),
  startPronunciationStageSession: vi.fn(),
}))

const getCurrentUserMock = vi.mocked(getCurrentUser)
const getTopicMock = vi.mocked(getTopic)
const getTopicsMock = vi.mocked(getTopics)
const getGrammarPointsMock = vi.mocked(getGrammarPoints)
const getPronunciationStagesMock = vi.mocked(getPronunciationStages)

const learner: CurrentUser = {
  id: 1,
  email: 'learner@example.com',
  displayName: 'Người học',
  emailVerified: true,
  roles: ['LEARNER'],
}

const admin: CurrentUser = {
  ...learner,
  id: 2,
  email: 'admin@example.com',
  displayName: 'Quản trị viên',
  roles: ['ADMIN'],
}

const contentEditor: CurrentUser = {
  ...learner,
  id: 3,
  email: 'editor@example.com',
  displayName: 'Biên tập viên nội dung',
  roles: ['CONTENT_EDITOR'],
}

const routeTopic: TopicDetail = {
  actions: {
    canPracticeListening: true,
    canPracticeRecognition: true,
    canStudy: false,
    canTakeTest: true,
    retryAvailableAt: null,
    testBlockedReason: null,
  },
  bestTestPercent: null,
  code: 'ROUTE_TOPIC',
  hskLevel: 1,
  id: 1,
  learnedWords: 1,
  name: 'Chủ đề định tuyến',
  percent: 100,
  status: 'IN_PROGRESS',
  totalWords: 1,
  words: [],
}

function unauthenticatedError() {
  return new ApiError({
    errorCode: 'UNAUTHENTICATED',
    message: 'Bạn chưa đăng nhập.',
    requestId: 'req-auth',
    status: 401,
  })
}

function renderRoute(path: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] })
  const result = render(<RouterProvider router={router} />)

  return { ...result, router }
}

beforeEach(() => {
  getCurrentUserMock.mockRejectedValue(unauthenticatedError())
  getTopicMock.mockReset().mockResolvedValue(routeTopic)
  getTopicsMock.mockReset().mockResolvedValue([])
  getGrammarPointsMock.mockReset().mockResolvedValue([])
  getPronunciationStagesMock.mockReset().mockResolvedValue([
    {
      activeAnswered: null,
      activeSessionId: null,
      bestAccuracy: null,
      description: 'Phân biệt âm cuốn lưỡi.',
      id: 303,
      name: 'Âm cuốn lưỡi',
      passPercent: 80,
      playable: true,
      stageIndex: 3,
      state: 'AVAILABLE',
      targetSounds: ['zh', 'ch', 'sh', 'r'],
      unitCount: 10,
    },
  ])
})

describe('application routes', () => {
  it('renders the landing page at the root route', async () => {
    renderRoute('/')

    expect(
      await screen.findByRole('heading', { name: 'Học tiếng Trung rõ ràng, từng bước một' }),
    ).toBeVisible()
  })

  it('renders the not-found page for an unknown route', () => {
    renderRoute('/khong-ton-tai')

    expect(screen.getByRole('heading', { name: 'Không tìm thấy trang' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Về trang chủ' })).toHaveAttribute('href', '/')
  })

  it('sends a guest from a learner route to login with the full redirect', async () => {
    const { router } = renderRoute('/learn/topics?level=2#dang-hoc')

    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/login')
    expect(new URLSearchParams(router.state.location.search).get('redirect')).toBe(
      '/learn/topics?level=2#dang-hoc',
    )
  })

  it('sends a learner without ADMIN role to the access-denied screen', async () => {
    getCurrentUserMock.mockResolvedValue(learner)
    const { router } = renderRoute('/admin/users')

    expect(await screen.findByRole('heading', { name: 'Không có quyền truy cập' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/403')
  })

  it('allows an administrator to open the user list', async () => {
    getCurrentUserMock.mockResolvedValue(admin)
    renderRoute('/admin/users')

    expect(await screen.findByRole('heading', { name: 'Quản lý tài khoản' })).toBeVisible()
  })

  it('allows an administrator to open the real user detail screen', async () => {
    getCurrentUserMock.mockResolvedValue(admin)
    renderRoute('/admin/users/12')

    expect(await screen.findByRole('heading', { name: 'Chi tiết tài khoản' })).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Màn SCR-077 đang dựng' })).not.toBeInTheDocument()
  })

  it('allows a content editor to open the real import report screen', async () => {
    getCurrentUserMock.mockResolvedValue(contentEditor)
    renderRoute('/content/imports/khong-hop-le')

    expect(await screen.findByRole('heading', { name: 'Báo cáo nhập dữ liệu' })).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Màn SCR-069 đang dựng' })).not.toBeInTheDocument()
  })

  it('allows a learner to open the real topic list screen', async () => {
    getCurrentUserMock.mockResolvedValue(learner)
    renderRoute('/learn/topics')

    expect(await screen.findByRole('heading', { name: 'Chủ đề từ vựng' })).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Màn SCR-011 đang dựng' })).not.toBeInTheDocument()
  })

  it('does not infer LEARNER access from the ADMIN role', async () => {
    getCurrentUserMock.mockResolvedValue(admin)
    renderRoute('/me/profile')

    expect(await screen.findByRole('heading', { name: 'Không có quyền truy cập' })).toBeVisible()
  })

  it('allows any authenticated account to open the password screen', async () => {
    getCurrentUserMock.mockResolvedValue({ ...learner, roles: [] })
    renderRoute('/me/password')

    expect(await screen.findByRole('heading', { name: 'Đổi mật khẩu' })).toBeVisible()
    expect(screen.queryByRole('navigation', { name: 'Học tập' })).not.toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'Quản trị' })).not.toBeInTheDocument()
  })

  it('uses the learner layout for the password screen when LEARNER is present', async () => {
    getCurrentUserMock.mockResolvedValue(learner)
    renderRoute('/me/password')

    expect(await screen.findByRole('navigation', { name: 'Học tập' })).toBeInTheDocument()
  })

  it('uses the admin layout for the password screen when only ADMIN is present', async () => {
    getCurrentUserMock.mockResolvedValue(admin)
    renderRoute('/me/password')

    expect(await screen.findByRole('navigation', { name: 'Quản trị' })).toBeInTheDocument()
  })

  it('moves an authenticated user away from a guest-only route using FR-090', async () => {
    getCurrentUserMock.mockResolvedValue(admin)
    const { router } = renderRoute('/login')

    expect(await screen.findByRole('heading', { name: 'Quản lý tài khoản' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/admin/users')
  })

  it('does not flash the login screen while the current user request is pending', async () => {
    let resolveUser: ((user: CurrentUser) => void) | undefined
    getCurrentUserMock.mockReturnValue(
      new Promise<CurrentUser>((resolve) => {
        resolveUser = resolve
      }),
    )
    renderRoute('/me/profile')

    expect(screen.queryByRole('heading', { name: 'Màn SCR-002 đang dựng' })).not.toBeInTheDocument()

    await act(async () => resolveUser?.(learner))
    expect(await screen.findByRole('heading', { name: 'Thông tin cá nhân' })).toBeVisible()
  })

  it.each([
    ['/learn/characters/writing/lexeme-1', /Không tìm thấy chữ/],
    ['/learn/characters/writing/challenge', /thử thách/i],
  ])('maps child route %s to the real SCR-014 screen', async (path, text) => {
    getCurrentUserMock.mockResolvedValue(learner)
    renderRoute(path)

    expect((await screen.findAllByText(text))[0]).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Màn SCR-014 đang dựng' })).not.toBeInTheDocument()
  })

  it('maps the pronunciation route to the real SCR-016 screen', async () => {
    getCurrentUserMock.mockResolvedValue(learner)
    renderRoute('/learn/pronunciation')

    expect(await screen.findByRole('heading', { name: 'Luyện phát âm' })).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Màn SCR-016 đang dựng' })).not.toBeInTheDocument()
  })

  it.each([
    '/learn/topics/1/study',
    '/learn/topics/1/practice/recognition',
    '/learn/topics/1/practice/listening',
    '/learn/topics/1/test',
  ])('maps topic child route %s to the real SCR-012 screen', async (path) => {
    getCurrentUserMock.mockResolvedValue(learner)
    renderRoute(path)

    expect(await screen.findByRole('heading', { name: 'Chủ đề định tuyến' })).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Màn SCR-012 đang dựng' })).not.toBeInTheDocument()
  })

  it('moves an expired protected session to login', async () => {
    getCurrentUserMock.mockResolvedValue(learner)
    const { router } = renderRoute('/learn/grammar?level=2#lesson-list')
    await screen.findByRole('heading', { name: 'Ngữ pháp HSK' })
    expect(screen.queryByRole('heading', { name: 'Màn SCR-017 đang dựng' })).not.toBeInTheDocument()

    act(() => window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT)))

    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeVisible()
    expect(new URLSearchParams(router.state.location.search).get('redirect')).toBe(
      '/learn/grammar?level=2#lesson-list',
    )
  })

  it('keeps a public route open when the session expires', async () => {
    // Không phụ thuộc màn đã dựng hay chưa: chỉ kiểm route công khai không bị đẩy sang /login.
    getCurrentUserMock.mockResolvedValue(learner)
    const { router } = renderRoute('/reset-password?token=sample')
    await waitFor(() => expect(getCurrentUserMock).toHaveBeenCalled())
    await screen.findByRole('main')

    act(() => window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT)))

    expect(router.state.location.pathname).toBe('/reset-password')
    expect(router.state.location.search).toBe('?token=sample')
  })

  it('always moves a banned account to login with a safe notice', async () => {
    getCurrentUserMock.mockResolvedValue(learner)
    const { router } = renderRoute('/')
    await screen.findByRole('heading', { name: 'Học tiếng Trung rõ ràng, từng bước một' })

    act(() =>
      window.dispatchEvent(
        new CustomEvent(AUTH_EXPIRED_EVENT, {
          detail: { reason: 'ACCOUNT_BANNED', banReason: 'Vi phạm điều khoản sử dụng' },
        }),
      ),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('Tài khoản của bạn đã bị cấm.')
    expect(screen.getByRole('alert')).toHaveTextContent('Lý do: Vi phạm điều khoản sử dụng')
    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.location.search).toBe('')
  })
})
