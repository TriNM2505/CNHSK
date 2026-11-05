import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, matchRoutes, MemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { previewRegistry } from '@/app/preview/registry'
import { appRouteDefinitions, findKnownRoute } from '@/app/routeAccess'
import { appRoutes } from '@/app/routes'
import { getCurrentUser } from '@/features/auth/api/getCurrentUser'
import type { CurrentUser, RoleCode } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { getAuthenticatedDestination } from '@/shared/auth/navigation'
import { AdminLayout } from '@/shared/layout'

import { getMyWallet, listMyTransactions, redeemCard } from './api/wallet'
import { creditsWallet, creditsTransactionPage, creditsRedeemResult } from './test-fixtures'

vi.mock('./api/wallet', () => ({
  getMyWallet: vi.fn(),
  listMyTransactions: vi.fn(),
  redeemCard: vi.fn(),
}))

vi.mock('@/features/auth/api/getCurrentUser', () => ({ getCurrentUser: vi.fn() }))

const routes = [
  { path: '/account/credits', scr: 'SCR-030', role: 'LEARNER', layout: 'learner' },
  { path: '/account/credits/history', scr: 'SCR-031', role: 'LEARNER', layout: 'learner' },
  { path: '/admin/card-batches', scr: 'SCR-073', role: 'ADMIN', layout: 'admin' },
  { path: '/admin/plans', scr: 'SCR-074', role: 'ADMIN', layout: 'admin' },
  { path: '/admin/ledger', scr: 'SCR-075', role: 'ADMIN', layout: 'admin' },
] as const

const financialLinks = [
  { label: 'Lô mã thẻ', path: '/admin/card-batches' },
  { label: 'Gói dịch vụ', path: '/admin/plans' },
  { label: 'Sổ cái điểm', path: '/admin/ledger' },
] as const
const roles: RoleCode[] = [
  'LEARNER',
  'ADMIN',
  'CONTENT_EDITOR',
  'TEACHER',
  'MODERATOR',
  'ACCOUNTANT',
]
const getCurrentUserMock = vi.mocked(getCurrentUser)

function screenHeading(scr: string) {
  if (scr === 'SCR-030') return 'Gói dịch vụ và điểm'
  if (scr === 'SCR-031') return 'Lịch sử giao dịch'
  return `Màn ${scr} đang dựng`
}

function user(userRoles: RoleCode[]): CurrentUser {
  return {
    id: 7,
    email: 'credits@example.test',
    displayName: 'Người dùng mẫu',
    emailVerified: false,
    roles: userRoles,
  }
}

function renderRoute(path: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] })
  render(<RouterProvider router={router} />)
  return router
}

function renderAdminLayout(userRoles: RoleCode[]) {
  const auth: AuthContextValue = {
    error: null,
    reloadCurrentUser: vi.fn(),
    setAuthenticatedUser: vi.fn(),
    signOut: vi.fn(),
    status: 'authenticated',
    user: user(userRoles),
  }
  render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={['/admin/ledger']}>
        <AdminLayout>Nội dung quản trị mẫu</AdminLayout>
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

beforeEach(() => {
  vi.mocked(getMyWallet).mockReset().mockResolvedValue(structuredClone(creditsWallet))
  vi.mocked(listMyTransactions)
    .mockReset()
    .mockResolvedValue(structuredClone(creditsTransactionPage))
  vi.mocked(redeemCard).mockReset().mockResolvedValue(structuredClone(creditsRedeemResult))
  getCurrentUserMock
    .mockReset()
    .mockRejectedValue(
      new ApiError({ status: 401, errorCode: 'UNAUTHENTICATED', message: 'Bạn chưa đăng nhập.' }),
    )
})

describe('credits route foundation (FR-001 FE, AT-01 access, §12)', () => {
  it('FR001_resolves exactly five new routes with their approved SCR, role and layout', () => {
    const actual = appRouteDefinitions.filter(({ path }) =>
      routes.some((route) => route.path === path),
    )
    expect(actual).toEqual(
      routes.map(({ path, scr, role, layout }) => ({
        kind: 'screen',
        path,
        scr,
        access: [role],
        layout,
      })),
    )
    for (const { path } of routes) {
      expect(findKnownRoute(path)?.path).toBe(path)
      const matches = matchRoutes(appRoutes, path)
      expect(matches?.[matches.length - 1]?.route.path).toBe(path)
    }
  })

  it.each(routes)(
    'FR001_$role reaches $scr without requiring verified email',
    async ({ path, scr, role }) => {
      getCurrentUserMock.mockResolvedValue(user([role]))
      const router = renderRoute(path)
      expect(await screen.findByRole('heading', { name: screenHeading(scr) })).toBeVisible()
      expect(router.state.location.pathname).toBe(path)
    },
  )

  it.each(routes)(
    'FR001_guest keeps the complete login return destination for $path',
    async ({ path }) => {
      const destination = `${path}?source=credits#sample`
      const router = renderRoute(destination)
      expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeVisible()
      expect(router.state.location.pathname).toBe('/login')
      expect(new URLSearchParams(router.state.location.search).get('redirect')).toBe(destination)
    },
  )

  it.each(
    routes.flatMap((route) =>
      roles.filter((role) => role !== route.role).map((role) => ({ ...route, deniedRole: role })),
    ),
  )('FR001_$deniedRole alone cannot open $path', async ({ path, scr, deniedRole }) => {
    getCurrentUserMock.mockResolvedValue(user([deniedRole]))
    const router = renderRoute(path)
    await waitFor(() => expect(router.state.location.pathname).toBe('/403'))
    expect(screen.queryByRole('heading', { name: screenHeading(scr) })).not.toBeInTheDocument()
  })

  it.each(routes)(
    'FR001_multiple roles grant $path only through an explicitly assigned role',
    async ({ path, scr }) => {
      getCurrentUserMock.mockResolvedValue(user(['ACCOUNTANT', 'LEARNER', 'ADMIN']))
      const router = renderRoute(path)
      expect(await screen.findByRole('heading', { name: screenHeading(scr) })).toBeVisible()
      expect(router.state.location.pathname).toBe(path)
    },
  )

  it.each(routes)(
    'FR001_validates a post-login redirect to $path using the new guard',
    ({ path, role }) => {
      const destination = `${path}?source=credits#sample`
      expect(getAuthenticatedDestination(user([role]), destination)).toEqual({
        type: 'internal',
        to: destination,
      })
      expect(getAuthenticatedDestination(user(['ACCOUNTANT']), destination)).toEqual({
        type: 'internal',
        to: '/403',
      })
    },
  )
})

describe('credits ADMIN navigation (FR-001 FE)', () => {
  it.each([{ userRoles: ['ADMIN'] }, { userRoles: ['ADMIN', 'CONTENT_EDITOR'] }] satisfies {
    userRoles: RoleCode[]
  }[])(
    'FR001_hides paused finance links for $userRoles in desktop and mobile navigation',
    ({ userRoles }) => {
      renderAdminLayout(userRoles)
      for (const name of ['Quản trị', 'Quản trị trên thiết bị di động']) {
        const navigation = within(screen.getByRole('navigation', { name }))
        for (const { label } of financialLinks) {
          expect(navigation.queryByRole('link', { name: label })).not.toBeInTheDocument()
        }
        expect(navigation.getByRole('link', { name: 'Người dùng' })).toHaveAttribute(
          'href',
          '/admin/users',
        )
      }
    },
  )

  it.each(roles.filter((role) => role !== 'ADMIN'))(
    'FR001_hides financial links from %s alone',
    (role) => {
      renderAdminLayout([role])
      for (const { label } of financialLinks)
        expect(screen.queryByRole('link', { name: label })).not.toBeInTheDocument()
    },
  )

  it('FR001_keeps user management navigation working while finance links are hidden', async () => {
    renderAdminLayout(['ADMIN'])
    const navigation = within(screen.getByRole('navigation', { name: 'Quản trị' }))
    for (const { label } of financialLinks) {
      expect(navigation.queryByRole('link', { name: label })).not.toBeInTheDocument()
    }
    expect(navigation.getByRole('link', { name: 'Người dùng' })).not.toHaveAttribute('aria-current')
    await userEvent.click(navigation.getByRole('link', { name: 'Người dùng' }))
    expect(navigation.getByRole('link', { name: 'Người dùng' })).toHaveAttribute(
      'aria-current',
      'page',
    )
  })
})

describe('credits offline catalogs (§12)', () => {
  it('registers the five preview catalogs without duplicate screen or preview IDs', () => {
    const previews = previewRegistry.filter(({ id }) => id.startsWith('credits-'))
    expect(previews.map(({ scr }) => scr)).toEqual(routes.map(({ scr }) => scr))
    expect(new Set(previews.map(({ id }) => id)).size).toBe(previews.length)
  })

  it.each([
    { view: 'credits-batch-samples', title: 'Lô mã mẫu', rows: 3 },
    { view: 'credits-plan-samples', title: 'Gói dịch vụ mẫu', rows: 3 },
    { view: 'credits-ledger-samples', title: 'Sổ cái mẫu', rows: 25 },
  ])('renders $view without authentication or backend calls', async ({ view, title, rows }) => {
    renderRoute(`/__preview/frame/${view}/ready`)
    expect(await screen.findByRole('heading', { name: title })).toBeVisible()
    expect(screen.getAllByRole('listitem')).toHaveLength(rows)
    expect(getCurrentUserMock).not.toHaveBeenCalled()
  })

  it.each([
    {
      view: 'credits-wallet-samples',
      title: 'Gói dịch vụ và điểm',
      table: 'Lượt sử dụng theo tính năng',
      rows: 5,
    },
    {
      view: 'credits-history-samples',
      title: 'Lịch sử giao dịch',
      table: 'Giao dịch điểm',
      rows: 21,
    },
  ])(
    'renders the $view View without authentication or backend calls',
    async ({ view, title, table, rows }) => {
      renderRoute(`/__preview/frame/${view}/ready`)
      expect(await screen.findByRole('heading', { name: title })).toBeVisible()
      const data = within(await screen.findByRole('table', { name: table }))
      expect(data.getAllByRole('row')).toHaveLength(rows)
      expect(getCurrentUserMock).not.toHaveBeenCalled()
      expect(getMyWallet).not.toHaveBeenCalled()
      expect(listMyTransactions).not.toHaveBeenCalled()
      expect(redeemCard).not.toHaveBeenCalled()
    },
  )
})
