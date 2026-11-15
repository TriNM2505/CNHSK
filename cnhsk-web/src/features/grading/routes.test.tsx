import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, matchRoutes, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { previewRegistry } from '@/app/preview/registry'
import { appRouteDefinitions } from '@/app/routeAccess'
import { appRoutes } from '@/app/routes'
import { getCurrentUser } from '@/features/auth/api/getCurrentUser'
import type { CurrentUser, RoleCode } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { getGradingPolicy, getMyGradingRequest, listMyGradingRequests } from './api/requests'
import { pendingRequest, policy, requestPage } from './test-fixtures'

vi.mock('@/features/auth/api/getCurrentUser', () => ({ getCurrentUser: vi.fn() }))
vi.mock('./api/requests', () => ({
  getGradingPolicy: vi.fn(),
  createGradingRequest: vi.fn(),
  listMyGradingRequests: vi.fn(),
  getMyGradingRequest: vi.fn(),
  cancelGradingRequest: vi.fn(),
}))
const learner: CurrentUser = {
  id: 1,
  roles: ['LEARNER'],
  displayName: 'Học viên kiểm thử',
  email: 'fixture@example.test',
  emailVerified: true,
}
const routes = [
  ['/grading/new', 'SCR-032', 'Gửi bài chấm'],
  ['/grading', 'SCR-033', 'Yêu cầu chấm bài của tôi'],
  ['/grading/42', 'SCR-034', 'Chi tiết yêu cầu chấm bài'],
] as const
beforeEach(() => {
  vi.mocked(getCurrentUser).mockReset().mockResolvedValue(learner)
  vi.mocked(getGradingPolicy).mockReset().mockResolvedValue(policy)
  vi.mocked(listMyGradingRequests).mockReset().mockResolvedValue(requestPage)
  vi.mocked(getMyGradingRequest).mockReset().mockResolvedValue(pendingRequest)
})
function page(path: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] })
  render(<RouterProvider router={router} />)
  return router
}
describe('grading screen registration and learner access', () => {
  it.each(routes)('registers %s as %s, with a preview and exact learner layout', (path, scr) => {
    const matches = matchRoutes(
      appRouteDefinitions.map((route) => ({ ...route })),
      path,
    )
    expect(matches?.at(-1)?.route).toMatchObject({ access: ['LEARNER'], layout: 'learner', scr })
    expect(previewRegistry.find((entry) => entry.scr === scr)?.states).toEqual([
      'loading',
      'empty',
      'error',
      'ready',
    ])
  })
  it.each(routes)('opens %s for a learner', async (path, _scr, title) => {
    const router = page(path)
    await screen.findByRole('heading', { name: title, level: 1 })
    expect(router.state.location.pathname).toBe(path)
    expect(await screen.findByRole('navigation', { name: 'Học tập' })).toBeInTheDocument()
  })
  it.each(routes)('blocks guests on %s before requesting private grading data', async (path) => {
    vi.mocked(getCurrentUser).mockRejectedValue(
      new ApiError({ status: 401, errorCode: 'UNAUTHENTICATED', message: 'Chưa đăng nhập.' }),
    )
    const router = page(path)
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(getGradingPolicy).not.toHaveBeenCalled()
    expect(listMyGradingRequests).not.toHaveBeenCalled()
    expect(getMyGradingRequest).not.toHaveBeenCalled()
  })
  it.each(['TEACHER', 'ADMIN', 'CONTENT_EDITOR', 'MODERATOR', 'ACCOUNTANT'] as RoleCode[])(
    'does not give %s learner grading access',
    async (role) => {
      vi.mocked(getCurrentUser).mockResolvedValue({ ...learner, roles: [role] })
      const router = page('/grading/new')
      await waitFor(() => expect(router.state.location.pathname).toBe('/403'))
      expect(getGradingPolicy).not.toHaveBeenCalled()
    },
  )
  it('adds only Chấm bài to learning navigation and keeps Cuộc thi and Bảng xếp hạng', async () => {
    page('/grading')
    const navigation = await screen.findByRole('navigation', { name: 'Học tập' })
    const grading = within(navigation).getByRole('link', { name: 'Chấm bài' })
    expect(grading).toHaveAttribute('href', '/grading')
    expect(grading).toHaveAttribute('aria-current', 'page')
    const links = within(navigation).getAllByRole('link')
    expect(links.at(-1)).toBe(grading)
    expect(links.at(-2)).toHaveAccessibleName('Tài liệu tham khảo')
    expect(
      within(navigation).queryByRole('link', { name: 'Gói dịch vụ và điểm' }),
    ).not.toBeInTheDocument()
    const community = screen.getByRole('navigation', { name: 'Cộng đồng' })
    expect(within(community).getByRole('link', { name: 'Cuộc thi' })).toHaveAttribute(
      'href',
      '/contests',
    )
    expect(within(community).getByRole('link', { name: 'Bảng xếp hạng' })).toHaveAttribute(
      'href',
      '/leaderboards/topics',
    )
    const mobile = screen.getByRole('navigation', { name: 'Học tập trên thiết bị di động' })
    expect(within(mobile).getAllByRole('link')).toHaveLength(4)
    await userEvent.click(within(mobile).getByRole('button', { name: 'Thêm' }))
    const more = screen.getByRole('navigation', { name: 'Điều hướng bổ sung' })
    expect(within(more).getByRole('link', { name: 'Chấm bài' })).toHaveAttribute('href', '/grading')
    expect(within(more).getByRole('link', { name: 'Cuộc thi' })).toBeInTheDocument()
    expect(within(more).getByRole('link', { name: 'Bảng xếp hạng' })).toBeInTheDocument()
  })
})
