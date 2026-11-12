import { render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, matchRoutes, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { appRouteDefinitions, findKnownRoute } from '@/app/routeAccess'
import { appRoutes } from '@/app/routes'
import { screenRegistry } from '@/app/screenRegistry'
import { getCurrentUser } from '@/features/auth/api/getCurrentUser'
import type { CurrentUser, RoleCode } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { getAuthenticatedDestination } from '@/shared/auth/navigation'

vi.mock('@/features/auth/api/getCurrentUser', () => ({ getCurrentUser: vi.fn() }))

const routes = [
  { path: '/learn', scr: 'SCR-010' },
  { path: '/learn/review', scr: 'SCR-013' },
  { path: '/learn/progress', scr: 'SCR-019' },
] as const
const getCurrentUserMock = vi.mocked(getCurrentUser)

function user(roles: RoleCode[]): CurrentUser {
  return {
    id: 11,
    displayName: 'Người dùng kiểm thử',
    email: 'mastery@example.test',
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
  getCurrentUserMock
    .mockReset()
    .mockRejectedValue(
      new ApiError({ status: 401, errorCode: 'UNAUTHENTICATED', message: 'Bạn chưa đăng nhập.' }),
    )
})

describe('mastery route foundation (FR-001 FE, §12; AT-01 access only)', () => {
  it.each(routes)(
    'registers $path as $scr with LEARNER access and learner layout',
    ({ path, scr }) => {
      expect(appRouteDefinitions.filter((route) => route.path === path)).toEqual([
        { path, scr, kind: 'screen', access: ['LEARNER'], layout: 'learner' },
      ])
      expect(findKnownRoute(path)?.path).toBe(path)
      const matches = matchRoutes(appRoutes, path)
      expect(matches?.[matches.length - 1]?.route.path).toBe(path)
    },
  )


  it('registers the SCR-019 learning progress page', () => {
    expect(screenRegistry['SCR-019']).toBeDefined()
  })

  it('registers the SCR-010 learning dashboard page', () => {
    expect(screenRegistry['SCR-010']).toBeDefined()
  })

  it('registers the SCR-013 due review page', () => {
    expect(screenRegistry['SCR-013']).toBeDefined()
  })

  it.each(routes)(
    'redirects a guest at $path to login with the full destination',
    async ({ path, scr }) => {
      const destination = `${path}?source=mastery#sample`
      const router = renderRoute(destination)
      expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeVisible()
      expect(router.state.location.pathname).toBe('/login')
      expect(new URLSearchParams(router.state.location.search).get('redirect')).toBe(destination)
      expect(
        screen.queryByRole('heading', { name: `Màn ${scr} đang dựng` }),
      ).not.toBeInTheDocument()
    },
  )

  it.each(
    routes.flatMap((route) =>
      (['ADMIN', 'CONTENT_EDITOR', 'TEACHER', 'MODERATOR', 'ACCOUNTANT'] as const).map((role) => ({
        ...route,
        role,
      })),
    ),
  )('denies $role alone at $path', async ({ path, scr, role }) => {
    getCurrentUserMock.mockResolvedValue(user([role]))
    const router = renderRoute(path)
    await waitFor(() => expect(router.state.location.pathname).toBe('/403'))
    expect(screen.queryByRole('heading', { name: `Màn ${scr} đang dựng` })).not.toBeInTheDocument()
  })


  it.each(routes)(
    'validates the post-login destination $path against the new guard',
    ({ path }) => {
      const destination = `${path}?source=mastery#sample`
      expect(getAuthenticatedDestination(user(['LEARNER']), destination)).toEqual({
        type: 'internal',
        to: destination,
      })
      expect(getAuthenticatedDestination(user(['ADMIN']), destination)).toEqual({
        type: 'internal',
        to: '/admin/users',
      })
    },
  )
})
