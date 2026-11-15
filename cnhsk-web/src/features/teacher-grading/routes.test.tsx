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
import { getAssignedGrading, getGradingQueue, getMyAssignedGrading } from './api/requests'
import { assigned, mine, queue } from './test-fixtures'

vi.mock('@/features/auth/api/getCurrentUser', () => ({ getCurrentUser: vi.fn() }))
vi.mock('./api/requests', () => ({
  getAssignedGrading: vi.fn(),
  getGradingQueue: vi.fn(),
  getMyAssignedGrading: vi.fn(),
  claimGrading: vi.fn(),
  completeGrading: vi.fn(),
  releaseGrading: vi.fn(),
}))
const teacher: CurrentUser = {
  id: 1,
  roles: ['TEACHER'],
  email: 'teacher@example.test',
  displayName: 'Giáo viên kiểm thử',
  emailVerified: true,
}
const routes = [
  ['/teacher/grading', 'SCR-056', 'Hàng đợi chấm bài'],
  ['/teacher/grading/42', 'SCR-057', 'Chấm bài viết'],
] as const
beforeEach(() => {
  vi.mocked(getCurrentUser).mockReset().mockResolvedValue(teacher)
  vi.mocked(getGradingQueue).mockReset().mockResolvedValue(queue)
  vi.mocked(getMyAssignedGrading).mockReset().mockResolvedValue(mine)
  vi.mocked(getAssignedGrading).mockReset().mockResolvedValue(assigned)
})
function page(path: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] })
  render(<RouterProvider router={router} />)
  return router
}
describe('teacher grading route catalogs and access', () => {
  it.each(routes)(
    'registers %s as %s with the exact role, layout and preview states',
    (path, scr) => {
      const match = matchRoutes(
        appRouteDefinitions.map((route) => ({ ...route })),
        path,
      )?.at(-1)?.route
      expect(match).toMatchObject({ access: ['TEACHER'], layout: 'admin', scr })
      expect(previewRegistry.find((entry) => entry.scr === scr)?.states).toEqual([
        'loading',
        'empty',
        'error',
        'ready',
      ])
    },
  )
  it.each(routes)('opens %s for a teacher', async (path, _scr, title) => {
    const router = page(path)
    await screen.findByRole('heading', { level: 1, name: title })
    expect(router.state.location.pathname).toBe(path)
    const desktop = await screen.findByRole('navigation', { name: 'Quản trị' })
    expect(within(desktop).getByRole('link', { name: 'Chấm bài viết' })).toHaveAttribute(
      'href',
      '/teacher/grading',
    )
    expect(within(desktop).getByRole('link', { name: 'Chấm bài viết' })).toHaveAttribute(
      'aria-current',
      'page',
    )
  })
  it.each(routes)('redirects guests before reading private data on %s', async (path) => {
    vi.mocked(getCurrentUser).mockRejectedValue(
      new ApiError({ status: 401, errorCode: 'UNAUTHENTICATED', message: 'Chưa đăng nhập.' }),
    )
    const router = page(path)
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(getGradingQueue).not.toHaveBeenCalled()
    expect(getAssignedGrading).not.toHaveBeenCalled()
  })
  it.each(['LEARNER', 'ADMIN', 'CONTENT_EDITOR', 'MODERATOR', 'ACCOUNTANT'] as RoleCode[])(
    'does not grant teacher access to pure %s',
    async (role) => {
      vi.mocked(getCurrentUser).mockResolvedValue({ ...teacher, roles: [role] })
      const router = page('/teacher/grading/42')
      await waitFor(() => expect(router.state.location.pathname).toBe('/403'))
      expect(getAssignedGrading).not.toHaveBeenCalled()
    },
  )
  it('allows a teacher with learner role to open the exact teacher area through AccountMenu', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue({ ...teacher, roles: ['LEARNER', 'TEACHER'] })
    page('/teacher/grading')
    await screen.findByRole('heading', { name: 'Hàng đợi chấm bài' })
    await userEvent.click(document.querySelector('summary')!)
    const links = screen.getAllByRole('link', { name: 'Chấm bài viết' })
    expect(links.some((link) => link.closest('details'))).toBe(true)
    expect(screen.getByRole('link', { name: 'Học tập' })).toHaveAttribute('href', '/learn/topics')
  })
})
