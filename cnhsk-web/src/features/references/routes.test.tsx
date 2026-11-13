import { render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, matchRoutes, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { previewRegistry } from '@/app/preview/registry'
import { appRouteDefinitions } from '@/app/routeAccess'
import { appRoutes } from '@/app/routes'
import { getCurrentUser } from '@/features/auth/api/getCurrentUser'
import type { CurrentUser } from '@/features/auth/types'
import { ApiError } from '@/shared/api'

import { getReference, listReferences } from './api/references'
import { bookPage, bookReference, mediaPage } from './test-fixtures'

vi.mock('@/features/auth/api/getCurrentUser', () => ({ getCurrentUser: vi.fn() }))
vi.mock('./api/references', () => ({
  listReferences: vi.fn(),
  getReference: vi.fn(),
  reportReference: vi.fn(),
}))

const routes = [
  ['/references/media', 'SCR-053', 'Tài liệu nghe nhìn'],
  ['/references/books', 'SCR-054', 'Danh mục sách tham khảo'],
  ['/references/books/503', 'SCR-055', 'Chi tiết sách tham khảo'],
] as const

const learner: CurrentUser = {
  id: 1,
  roles: ['LEARNER'],
  displayName: 'Học viên kiểm thử',
  email: 'learner@example.com',
  emailVerified: true,
}

describe('references routes and registration', () => {
  beforeEach(() => {
    vi.mocked(getCurrentUser)
      .mockReset()
      .mockRejectedValue(
        new ApiError({ status: 401, errorCode: 'UNAUTHENTICATED', message: 'Chưa đăng nhập.' }),
      )
    vi.mocked(getReference).mockReset().mockResolvedValue(bookReference)
    vi.mocked(listReferences)
      .mockReset()
      .mockImplementation(async ({ kind }) => (kind === 'BOOK' ? bookPage : mediaPage))
  })
  it.each(routes)('registers %s as public by-role screen %s', (url, scr) => {
    const matches = matchRoutes(
      appRouteDefinitions.map((route) => ({ ...route })),
      url,
    )
    expect(matches?.at(-1)?.route).toMatchObject({ access: 'public', layout: 'by-role', scr })
    const preview = previewRegistry.find((entry) => entry.scr === scr)
    expect(preview?.states).toEqual(['loading', 'empty', 'error', 'ready'])
  })
  it.each(routes)(
    'allows a guest into %s without redirect and uses PublicLayout',
    async (url, _scr, title) => {
      const router = createMemoryRouter(appRoutes, { initialEntries: [url] })
      render(<RouterProvider router={router} />)
      await waitFor(() =>
        expect(screen.getByRole('heading', { name: title, level: 1 })).toBeVisible(),
      )
      expect(router.state.location.pathname).toBe(url)
      expect(screen.getByRole('link', { name: 'Đăng nhập' })).toBeVisible()
      expect(screen.queryByRole('navigation', { name: 'Học tập' })).not.toBeInTheDocument()
    },
  )
  it.each(routes)('allows a learner into %s and uses LearnerLayout', async (url, _scr, title) => {
    vi.mocked(getCurrentUser).mockResolvedValue(learner)
    const router = createMemoryRouter(appRoutes, { initialEntries: [url] })
    render(<RouterProvider router={router} />)
    await waitFor(() =>
      expect(screen.getByRole('heading', { name: title, level: 1 })).toBeVisible(),
    )
    expect(router.state.location.pathname).toBe(url)
    expect(await screen.findByRole('navigation', { name: 'Học tập' })).toBeInTheDocument()
    expect(
      screen.getByRole('navigation', { name: 'Học tập trên thiết bị di động' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Đăng nhập' })).not.toBeInTheDocument()
  })
})
