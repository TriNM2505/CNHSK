import { render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { appRoutes } from '@/app/routes'
import { getCurrentUser } from '@/features/auth/api/getCurrentUser'
import type { RoleCode } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { getAiUsage, listSettings } from './api/settings'
import { settingsFixtures, usageFixture } from './test-fixtures'

vi.mock('@/features/auth/api/getCurrentUser', () => ({ getCurrentUser: vi.fn() }))
vi.mock('./api/settings', () => ({
  getAiUsage: vi.fn(),
  listSettings: vi.fn(),
  getSettingHistory: vi.fn(),
  resetSetting: vi.fn(),
  updateSetting: vi.fn(),
}))
const user = (roles: RoleCode[]) => ({
  id: 7,
  displayName: 'Quản trị kiểm thử',
  email: 'settings@example.test',
  emailVerified: true,
  roles,
})
function open() {
  const router = createMemoryRouter(appRoutes, { initialEntries: ['/admin/settings'] })
  render(<RouterProvider router={router} />)
  return router
}
beforeEach(() => {
  vi.mocked(getCurrentUser)
    .mockReset()
    .mockResolvedValue(user(['ADMIN']))
  vi.mocked(listSettings).mockReset().mockResolvedValue(settingsFixtures)
  vi.mocked(getAiUsage).mockReset().mockResolvedValue(usageFixture)
})
describe('SCR-078 ADMIN route', () => {
  it.each([['ADMIN'], ['ADMIN', 'CONTENT_EDITOR', 'LEARNER']] as RoleCode[][])(
    'opens for %s in the admin layout',
    async (...roles) => {
      vi.mocked(getCurrentUser).mockResolvedValue(user(roles.flat() as RoleCode[]))
      open()
      await screen.findByRole('heading', { name: 'Cài đặt hệ thống' })
      expect(screen.getByRole('navigation', { name: 'Quản trị' })).toBeInTheDocument()
      expect(await screen.findByRole('article', { name: settingsFixtures[0]!.key })).toBeVisible()
    },
  )
  it.each(['CONTENT_EDITOR', 'TEACHER', 'MODERATOR', 'LEARNER', 'ACCOUNTANT'] as const)(
    'denies %s without calling either API',
    async (role) => {
      vi.mocked(getCurrentUser).mockResolvedValue(user([role]))
      const router = open()
      await waitFor(() => expect(router.state.location.pathname).toBe('/403'))
      expect(listSettings).not.toHaveBeenCalled()
      expect(getAiUsage).not.toHaveBeenCalled()
    },
  )
  it('redirects guest to login without calling either API', async () => {
    vi.mocked(getCurrentUser).mockRejectedValue(
      new ApiError({ status: 401, errorCode: 'UNAUTHENTICATED', message: 'Đăng nhập' }),
    )
    const router = open()
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(listSettings).not.toHaveBeenCalled()
    expect(getAiUsage).not.toHaveBeenCalled()
  })
})
