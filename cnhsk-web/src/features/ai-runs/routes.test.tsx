import { render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { appRoutes } from '@/app/routes'
import { getCurrentUser } from '@/features/auth/api/getCurrentUser'
import type { RoleCode } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { listAiRuns, triggerAiRun } from './api/ai-runs'
vi.mock('@/features/auth/api/getCurrentUser', () => ({ getCurrentUser: vi.fn() }))
vi.mock('./api/ai-runs', () => ({ listAiRuns: vi.fn(), triggerAiRun: vi.fn() }))
const user = (roles: RoleCode[]) => ({
  id: 7,
  displayName: 'Admin',
  email: 'admin@example.test',
  emailVerified: true,
  roles,
})
function open() {
  const router = createMemoryRouter(appRoutes, { initialEntries: ['/admin/ai-runs'] })
  render(<RouterProvider router={router} />)
  return router
}
beforeEach(() => {
  vi.mocked(getCurrentUser)
    .mockReset()
    .mockResolvedValue(user(['ADMIN']))
  vi.mocked(listAiRuns).mockReset().mockResolvedValue({
    enabled: false,
    embeddingReady: false,
    monthTokens: 0,
    items: [],
    page: 0,
    size: 20,
    totalElements: 0,
  })
  vi.mocked(triggerAiRun).mockReset()
})
it.each([['ADMIN'], ['ADMIN', 'CONTENT_EDITOR', 'LEARNER']] as RoleCode[][])(
  'opens ADMIN history for %s',
  async (...roles) => {
    vi.mocked(getCurrentUser).mockResolvedValue(user(roles.flat()))
    open()
    await screen.findByRole('heading', { name: 'Lô AI' })
    await screen.findByText('Chưa có lô phù hợp')
    expect(screen.getAllByRole('link', { name: 'Lô AI' })).toHaveLength(2)
  },
)
it.each(['CONTENT_EDITOR', 'TEACHER', 'MODERATOR', 'LEARNER', 'ACCOUNTANT'] as const)(
  'denies %s before API calls',
  async (role) => {
    vi.mocked(getCurrentUser).mockResolvedValue(user([role]))
    const router = open()
    await waitFor(() => expect(router.state.location.pathname).toBe('/403'))
    expect(listAiRuns).not.toHaveBeenCalled()
    expect(triggerAiRun).not.toHaveBeenCalled()
  },
)
it('redirects guest to login before API calls', async () => {
  vi.mocked(getCurrentUser).mockRejectedValue(
    new ApiError({ status: 401, errorCode: 'UNAUTHENTICATED', message: 'Login' }),
  )
  const router = open()
  await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
  expect(new URLSearchParams(router.state.location.search).get('redirect')).toBe('/admin/ai-runs')
  expect(listAiRuns).not.toHaveBeenCalled()
  expect(triggerAiRun).not.toHaveBeenCalled()
})
