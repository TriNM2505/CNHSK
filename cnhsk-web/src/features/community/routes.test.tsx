import { render, screen } from '@testing-library/react'
import { createMemoryRouter, matchRoutes, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { appRouteDefinitions, findKnownRoute, type RouteAccess } from '@/app/routeAccess'
import { appRoutes } from '@/app/routes'
import { getCurrentUser } from '@/features/auth/api/getCurrentUser'
import type { CurrentUser, RoleCode } from '@/features/auth/types'
import { ApiError } from '@/shared/api'

import { ownPost, publishedPage } from './test-fixtures'

vi.mock('@/features/community/api/posts', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/features/community/api/posts')>()),
  getMyPost: vi.fn().mockResolvedValue(ownPost),
  listMyPosts: vi.fn().mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0 }),
  listPublishedPosts: vi.fn().mockResolvedValue(publishedPage),
}))

vi.mock('@/features/auth/api/getCurrentUser', () => ({ getCurrentUser: vi.fn() }))
vi.mock('@/features/auth/api/signOutCurrentUser', () => ({ signOutCurrentUser: vi.fn() }))

const routes = [
  { path: '/community', url: '/community', scr: 'SCR-039', access: 'public', layout: 'public' },
  {
    path: '/community/posts/new',
    url: '/community/posts/new',
    scr: 'SCR-041',
    access: ['LEARNER'],
    layout: 'learner',
  },
  {
    path: '/community/posts/:id/edit',
    url: '/community/posts/42/edit',
    scr: 'SCR-041',
    access: ['LEARNER'],
    layout: 'learner',
  },
  {
    path: '/community/posts/:id',
    url: '/community/posts/42',
    scr: 'SCR-040',
    access: 'public',
    layout: 'public',
  },
  {
    path: '/moderation/posts',
    url: '/moderation/posts',
    scr: 'SCR-060',
    access: ['MODERATOR'],
    layout: 'admin',
  },
  {
    path: '/moderation/posts/:id',
    url: '/moderation/posts/42',
    scr: 'SCR-061',
    access: ['MODERATOR'],
    layout: 'admin',
  },
  {
    path: '/moderation/reports',
    url: '/moderation/reports',
    scr: 'SCR-062',
    access: ['MODERATOR'],
    layout: 'admin',
  },
  {
    path: '/moderation/reports/:id',
    url: '/moderation/reports/9',
    scr: 'SCR-063',
    access: ['MODERATOR'],
    layout: 'admin',
  },
] satisfies { path: string; url: string; scr: string; access: RouteAccess; layout: string }[]

const getCurrentUserMock = vi.mocked(getCurrentUser)
function user(roles: RoleCode[]): CurrentUser {
  return {
    id: 7,
    displayName: 'Người dùng kiểm thử',
    email: 'route@example.com',
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
      message: 'Bạn chưa đăng nhập',
      requestId: 'req-route',
    }),
  )
})

describe('community route foundation (§12; FR-001 and AT-01/02 access only)', () => {
  it('FR001_registers exactly eight unique paths with approved SCR, access and layout', () => {
    const actual = appRouteDefinitions.filter(
      ({ path }) => path.startsWith('/community') || path.startsWith('/moderation'),
    )
    expect(actual).toEqual(
      routes.map(({ path, scr, access, layout }) => ({
        kind: 'screen',
        path,
        scr,
        access,
        layout,
      })),
    )
    const paths = appRouteDefinitions.map(({ path }) => path)
    expect(new Set(paths).size).toBe(paths.length)
    for (const { path, url } of routes) {
      expect(findKnownRoute(url)?.path).toBe(path)
      const matches = matchRoutes(appRoutes, url)
      expect(matches?.[matches.length - 1]?.route.path).toBe(path)
    }
    expect(actual.findIndex(({ path }) => path === '/community/posts/new')).toBeLessThan(
      actual.findIndex(({ path }) => path === '/community/posts/:id'),
    )
  })

  it.each(routes)(
    'FR001_allowed role reaches $scr screen or fallback at $path',
    async ({ url, scr, access, layout }) => {
      if (Array.isArray(access)) getCurrentUserMock.mockResolvedValue(user(access))
      const router = renderRoute(url)
      if (scr === 'SCR-039') {
        expect(await screen.findByRole('heading', { name: 'Cộng đồng' })).toBeVisible()
      } else if (scr === 'SCR-041' && url.endsWith('/new')) {
        expect(await screen.findByRole('heading', { name: 'Viết bài cộng đồng' })).toBeVisible()
      } else if (scr === 'SCR-041') {
        expect(await screen.findByRole('textbox', { name: 'Tiêu đề' })).toHaveValue(ownPost.title)
      } else {
        expect(await screen.findByRole('heading', { name: `Màn ${scr} đang dựng` })).toBeVisible()
      }
      expect(router.state.location.pathname).toBe(url)
      if (layout === 'learner')
        expect(screen.getByRole('navigation', { name: 'Học tập' })).toBeVisible()
      if (layout === 'admin')
        expect(screen.getByRole('navigation', { name: 'Quản trị' })).toBeVisible()
    },
  )

  it.each(routes.filter(({ access }) => access !== 'public'))(
    'FR001_guest is redirected from $path with the full destination',
    async ({ url }) => {
      const destination = `${url}?source=community#content`
      const router = renderRoute(destination)
      expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeVisible()
      expect(router.state.location.pathname).toBe('/login')
      expect(new URLSearchParams(router.state.location.search).get('redirect')).toBe(destination)
    },
  )

  it.each(routes.filter(({ access }) => access !== 'public'))(
    'FR001_ADMIN alone has no inherited access to $path (AT-02)',
    async ({ url }) => {
      getCurrentUserMock.mockResolvedValue(user(['ADMIN']))
      const router = renderRoute(url)
      expect(await screen.findByRole('heading', { name: 'Không có quyền truy cập' })).toBeVisible()
      expect(router.state.location.pathname).toBe('/403')
    },
  )

  it.each(routes.filter(({ layout }) => layout === 'admin'))(
    'FR001_LEARNER cannot open moderator route $path (AT-02)',
    async ({ url }) => {
      getCurrentUserMock.mockResolvedValue(user(['LEARNER']))
      renderRoute(url)
      expect(await screen.findByRole('heading', { name: 'Không có quyền truy cập' })).toBeVisible()
    },
  )

  it.each(['/community/posts/new', '/community/posts/42/edit'])(
    'FR001_MODERATOR needs explicit LEARNER role to write at %s',
    async (url) => {
      getCurrentUserMock.mockResolvedValue(user(['MODERATOR']))
      const router = renderRoute(url)
      expect(
        await screen.findByRole('heading', { name: 'Khu vực Kiểm duyệt đang được xây dựng' }),
      ).toBeVisible()
      expect(router.state.location.pathname).toBe('/403')
      expect(
        screen.queryByRole('heading', { name: 'Màn SCR-041 đang dựng' }),
      ).not.toBeInTheDocument()
    },
  )
})
