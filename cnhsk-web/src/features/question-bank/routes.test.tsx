import { render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, matchRoutes, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { previewRegistry } from '@/app/preview/registry'
import { appRouteDefinitions, findKnownRoute } from '@/app/routeAccess'
import { appRoutes } from '@/app/routes'
import { getCurrentUser } from '@/features/auth/api/getCurrentUser'
import type { CurrentUser, RoleCode } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { getAuthenticatedDestination } from '@/shared/auth/navigation'

vi.mock('@/features/auth/api/getCurrentUser', () => ({ getCurrentUser: vi.fn() }))

const routes = [
  { path: '/content/questions', url: '/content/questions', scr: 'SCR-064' },
  { path: '/content/questions/:id', url: '/content/questions/901', scr: 'SCR-059' },
]
const getCurrentUserMock = vi.mocked(getCurrentUser)

function user(roles: RoleCode[]): CurrentUser {
  return {
    id: 7,
    displayName: 'Người dùng kiểm thử',
    email: 'question@example.test',
    emailVerified: true,
    roles,
  }
}

function renderRoute(path: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] })
  render(<RouterProvider router={router} />)
  return router
}

beforeEach(() => {
  getCurrentUserMock.mockReset().mockRejectedValue(
    new ApiError({
      status: 401,
      errorCode: 'UNAUTHENTICATED',
      message: 'Bạn chưa đăng nhập.',
    }),
  )
})

describe('question-bank route foundation (FR-001 FE, §12)', () => {
  it('FR001_resolves both routes to the approved SCR, guard and admin layout', () => {
    expect(appRouteDefinitions.filter(({ path }) => path.startsWith('/content/questions'))).toEqual(
      routes.map(({ path, scr }) => ({
        kind: 'screen',
        path,
        scr,
        access: ['CONTENT_EDITOR', 'TEACHER'],
        layout: 'admin',
      })),
    )
    for (const { path, url } of routes) {
      expect(findKnownRoute(url)?.path).toBe(path)
      const matches = matchRoutes(appRoutes, url)
      expect(matches?.[matches.length - 1]?.route.path).toBe(path)
    }
  })

  it.each(
    routes.flatMap((route) =>
      (['CONTENT_EDITOR', 'TEACHER'] as const).map((role) => ({ ...route, role })),
    ),
  )('FR001_$role can open $scr at $url in the admin layout', async ({ url, scr, role }) => {
    getCurrentUserMock.mockResolvedValue(user([role]))
    const router = renderRoute(url)
    expect(await screen.findByRole('heading', { name: `Màn ${scr} đang dựng` })).toBeVisible()
    expect(screen.getByRole('navigation', { name: 'Quản trị' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe(url)
  })

  it.each(routes)('FR001_guest keeps the full return URL for $scr', async ({ url }) => {
    const destination = `${url}?hskLevel=2&q=sample#labels`
    const router = renderRoute(destination)
    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/login')
    expect(new URLSearchParams(router.state.location.search).get('redirect')).toBe(destination)
  })

  it.each(
    routes.flatMap((route) =>
      (['ADMIN', 'LEARNER', 'MODERATOR', 'ACCOUNTANT'] as const).map((role) => ({
        ...route,
        role,
      })),
    ),
  )('FR001_$role alone cannot open $scr', async ({ url, scr, role }) => {
    getCurrentUserMock.mockResolvedValue(user([role]))
    const router = renderRoute(url)
    await waitFor(() => expect(router.state.location.pathname).toBe('/403'))
    const heading =
      role === 'MODERATOR'
        ? 'Khu vực Kiểm duyệt đang được xây dựng'
        : role === 'ACCOUNTANT'
          ? 'Khu vực Kế toán đang được xây dựng'
          : 'Không có quyền truy cập'
    expect(await screen.findByRole('heading', { name: heading })).toBeVisible()
    expect(screen.queryByText(`Màn ${scr} đang dựng`)).not.toBeInTheDocument()
  })

  it('FR001_accepts an explicitly permitted role on a multi-role account', async () => {
    getCurrentUserMock.mockResolvedValue(user(['ADMIN', 'TEACHER']))
    const router = renderRoute('/content/questions/901')
    expect(await screen.findByRole('heading', { name: 'Màn SCR-059 đang dựng' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/content/questions/901')
  })

  it.each(routes)(
    'FR001_accepts a login redirect to $url only with a permitted role',
    ({ url }) => {
      const destination = `${url}?hskLevel=2#labels`
      for (const role of ['CONTENT_EDITOR', 'TEACHER'] as const) {
        expect(getAuthenticatedDestination(user([role]), destination)).toEqual({
          type: 'internal',
          to: destination,
        })
      }
      expect(getAuthenticatedDestination(user(['ADMIN']), destination)).toEqual({
        type: 'internal',
        to: '/admin/users',
      })
    },
  )

  it('registers exactly the two question-bank sample previews', () => {
    expect(
      previewRegistry.filter(({ id }) => id.startsWith('question-bank-')).map(({ scr }) => scr),
    ).toEqual(['SCR-064', 'SCR-059'])
  })

  it('renders all twelve original samples through the preview route without a backend', async () => {
    renderRoute('/__preview/frame/question-bank-samples/ready')
    expect(await screen.findByRole('heading', { name: 'Kho câu hỏi mẫu' })).toBeVisible()
    expect(screen.getAllByRole('listitem')).toHaveLength(12)
    expect(getCurrentUserMock).not.toHaveBeenCalled()
  })
})
