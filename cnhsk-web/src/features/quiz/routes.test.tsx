import { render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { appRoutes } from '@/app/routes'
import { appRouteDefinitions } from '@/app/routeAccess'
import { screenRegistry } from '@/app/screenRegistry'
import { previewRegistry } from '@/app/preview/registry'
import { getCurrentUser } from '@/features/auth/api/getCurrentUser'
import type { RoleCode } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { listQuizzes } from './api/quizzes'
import { quizPage } from './test-fixtures'

vi.mock('@/features/auth/api/getCurrentUser', () => ({ getCurrentUser: vi.fn() }))
vi.mock('./api/quizzes', () => ({ listQuizzes: vi.fn(), startQuizAttempt: vi.fn() }))
const learner = {
  id: 1,
  roles: ['LEARNER'] as RoleCode[],
  displayName: 'Học viên',
  email: 'quiz@example.test',
  emailVerified: true,
}

describe('quiz route registration and access', () => {
  beforeEach(() => {
    vi.mocked(getCurrentUser).mockReset().mockResolvedValue(learner)
    vi.mocked(listQuizzes).mockReset().mockResolvedValue(quizPage)
  })
  it.each([
    ['/community/quizzes', 'SCR-043'],
    ['/community/quizzes/:topicId/play', 'SCR-044'],
  ])('registers %s with learner layout and a screen', (path, scr) => {
    expect(appRouteDefinitions.find((route) => route.path === path)).toMatchObject({
      access: ['LEARNER'],
      layout: 'learner',
      scr,
    })
    expect(screenRegistry[scr as keyof typeof screenRegistry]).toBeDefined()
  })
  it('registers the four preview states', () => {
    expect(previewRegistry.find((entry) => entry.scr === 'SCR-043')?.states).toEqual([
      'loading',
      'empty',
      'error',
      'ready',
    ])
  })
  it('opens the real catalogue inside LearnerLayout', async () => {
    const router = createMemoryRouter(appRoutes, { initialEntries: ['/community/quizzes'] })
    render(<RouterProvider router={router} />)
    expect(await screen.findByRole('heading', { name: 'Quiz theo chủ đề', level: 1 })).toBeVisible()
    expect(screen.getByRole('navigation', { name: 'Học tập' })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Cộng đồng' })).toBeInTheDocument()
  })
  it('opens the registered SCR-044 placeholder without making POST from an untrusted URL', async () => {
    const router = createMemoryRouter(appRoutes, {
      initialEntries: ['/community/quizzes/43/play?sessionId=999'],
    })
    render(<RouterProvider router={router} />)
    expect(await screen.findByRole('heading', { name: 'Màn SCR-044 đang dựng' })).toBeVisible()
    expect(listQuizzes).not.toHaveBeenCalled()
  })
  it.each(['ADMIN', 'TEACHER', 'MODERATOR', 'CONTENT_EDITOR', 'ACCOUNTANT'] as RoleCode[])(
    'blocks %s before loading catalogue',
    async (role) => {
      vi.mocked(getCurrentUser).mockResolvedValue({ ...learner, roles: [role] })
      const router = createMemoryRouter(appRoutes, { initialEntries: ['/community/quizzes'] })
      render(<RouterProvider router={router} />)
      await waitFor(() => expect(router.state.location.pathname).toBe('/403'))
      expect(listQuizzes).not.toHaveBeenCalled()
    },
  )
  it('sends guests to login with a return path before loading catalogue', async () => {
    vi.mocked(getCurrentUser).mockRejectedValue(
      new ApiError({ status: 401, errorCode: 'UNAUTHENTICATED', message: 'Chưa đăng nhập.' }),
    )
    const router = createMemoryRouter(appRoutes, { initialEntries: ['/community/quizzes?hsk=2'] })
    render(<RouterProvider router={router} />)
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(router.state.location.search).toContain('redirect=')
    expect(listQuizzes).not.toHaveBeenCalled()
  })
})
