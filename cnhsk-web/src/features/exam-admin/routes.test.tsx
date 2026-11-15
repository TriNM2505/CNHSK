import { render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { appRoutes } from '@/app/routes'
import { getCurrentUser } from '@/features/auth/api/getCurrentUser'
import type { RoleCode } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { getExam, listExams } from './api/exams'
import { examFixture, pageFixture } from './test-fixtures'
vi.mock('@/features/auth/api/getCurrentUser', () => ({ getCurrentUser: vi.fn() }))
vi.mock('./api/exams', () => ({
  listExams: vi.fn(),
  getExam: vi.fn(),
  saveExam: vi.fn(),
  publishExam: vi.fn(),
  archiveExam: vi.fn(),
  cloneExam: vi.fn(),
}))
function user(roles: RoleCode[]) {
  return {
    id: 7,
    displayName: 'Người kiểm thử',
    email: 'exam@example.test',
    emailVerified: true,
    roles,
  }
}
beforeEach(() => {
  vi.mocked(getCurrentUser)
    .mockReset()
    .mockResolvedValue(user(['CONTENT_EDITOR']))
  vi.mocked(getExam).mockReset().mockResolvedValue(examFixture)
  vi.mocked(listExams).mockReset().mockResolvedValue(pageFixture)
})
function open(path: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] })
  render(<RouterProvider router={router} />)
  return router
}
describe('exam management route access', () => {
  it.each(['/content/exams', '/content/exams/new', '/content/exams/2301'])(
    'opens %s for CONTENT_EDITOR in admin layout',
    async (path) => {
      open(path)
      await screen.findByRole('heading', {
        name:
          path === '/content/exams'
            ? 'Quản lý đề thi'
            : path.endsWith('new')
              ? 'Tạo đề nháp'
              : `Đề thi · ${examFixture.code}`,
      })
      expect(screen.getByRole('navigation', { name: 'Quản trị' })).toBeInTheDocument()
      if (path.endsWith('new')) expect(getExam).not.toHaveBeenCalled()
    },
  )
  it.each(['ADMIN', 'TEACHER', 'MODERATOR', 'LEARNER', 'ACCOUNTANT'] as const)(
    'does not grant exam APIs to %s alone',
    async (role) => {
      vi.mocked(getCurrentUser).mockResolvedValue(user([role]))
      const router = open('/content/exams/2301')
      await waitFor(() => expect(router.state.location.pathname).toBe('/403'))
      expect(getExam).not.toHaveBeenCalled()
      expect(listExams).not.toHaveBeenCalled()
    },
  )
  it('redirects a guest to login without requesting exam data', async () => {
    vi.mocked(getCurrentUser).mockRejectedValue(
      new ApiError({ status: 401, errorCode: 'UNAUTHENTICATED', message: 'Đăng nhập' }),
    )
    const router = open('/content/exams/new')
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(getExam).not.toHaveBeenCalled()
    expect(listExams).not.toHaveBeenCalled()
  })
})
