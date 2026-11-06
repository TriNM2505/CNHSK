import { render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, matchRoutes, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { previewRegistry } from '@/app/preview/registry'
import { appRouteDefinitions, findKnownRoute, type RouteAccess } from '@/app/routeAccess'
import { appRoutes } from '@/app/routes'
import { getCurrentUser } from '@/features/auth/api/getCurrentUser'
import type { CurrentUser, RoleCode } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { getAuthenticatedDestination } from '@/shared/auth/navigation'

import { getLexeme, searchDictionary } from './api/dictionary'
import { listNotes, listTrash } from './api/notes'
import { getDeck, listDecks, listDueCards } from './api/flashcards'
import { getTopics } from '@/features/vocab/api/topics'
import { translate } from './api/translation'

vi.mock('@/features/auth/api/getCurrentUser', () => ({ getCurrentUser: vi.fn() }))
vi.mock('./api/dictionary', () => ({
  getLexeme: vi.fn(),
  searchDictionary: vi.fn(),
  listRadicals: vi.fn(),
  searchByRadical: vi.fn(),
}))
vi.mock('./api/notes', () => ({
  listNotes: vi.fn(),
  listTrash: vi.fn(),
  getNote: vi.fn(),
  createNote: vi.fn(),
  updateNote: vi.fn(),
  deleteNote: vi.fn(),
  restoreNote: vi.fn(),
}))
vi.mock('./api/flashcards', () => ({
  getDeck: vi.fn(),
  listDecks: vi.fn(),
  listDueCards: vi.fn(),
  createDeck: vi.fn(),
  updateDeck: vi.fn(),
  deleteDeck: vi.fn(),
  addCard: vi.fn(),
  updateCard: vi.fn(),
  deleteCard: vi.fn(),
  reviewCard: vi.fn(),
}))
vi.mock('@/features/vocab/api/topics', () => ({ getTopics: vi.fn() }))
vi.mock('./api/translation', () => ({ translate: vi.fn() }))

const routes = [
  { path: '/dictionary', url: '/dictionary', scr: 'SCR-024', access: 'public', layout: 'public' },
  {
    path: '/dictionary/:id',
    url: '/dictionary/101',
    scr: 'SCR-025',
    access: 'public',
    layout: 'public',
  },
  { path: '/translate', url: '/translate', scr: 'SCR-026', access: ['LEARNER'], layout: 'learner' },
  {
    path: '/library/notes',
    url: '/library/notes',
    scr: 'SCR-027',
    access: ['LEARNER'],
    layout: 'learner',
  },
  {
    path: '/library/decks',
    url: '/library/decks',
    scr: 'SCR-028',
    access: ['LEARNER'],
    layout: 'learner',
  },
  {
    path: '/library/decks/:id',
    url: '/library/decks/501',
    scr: 'SCR-029',
    access: ['LEARNER'],
    layout: 'learner',
  },
] satisfies { path: string; url: string; scr: string; access: RouteAccess; layout: string }[]

const privateRoutes = routes.filter(({ access }) => access !== 'public')
const getCurrentUserMock = vi.mocked(getCurrentUser)

function user(roles: RoleCode[]): CurrentUser {
  return {
    id: 7,
    displayName: 'Người dùng kiểm thử',
    email: 'library@example.com',
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
  vi.mocked(getDeck).mockReset().mockResolvedValue({
    id: 501,
    name: 'Bộ thẻ kiểm thử',
    cardCount: 0,
    dueCount: 0,
    createdAt: '2026-11-01T00:00:00Z',
    cards: [],
  })
  vi.mocked(listDecks).mockReset().mockResolvedValue([])
  vi.mocked(listDueCards).mockReset().mockResolvedValue([])
  vi.mocked(getTopics).mockReset().mockResolvedValue([])
  vi.mocked(listNotes).mockReset().mockResolvedValue({ items: [], total: 0 })
  vi.mocked(listTrash).mockReset().mockResolvedValue([])
  vi.mocked(searchDictionary).mockReset().mockResolvedValue({ items: [], total: 0 })
  vi.mocked(getLexeme).mockReset().mockResolvedValue({
    id: 101,
    kind: 'CHAR',
    text: '日',
    meaningVi: 'ngày; mặt trời',
    examples: [],
    components: [],
    containedIn: [],
    related: [],
    strokeData: null,
  })
  getCurrentUserMock
    .mockReset()
    .mockRejectedValue(
      new ApiError({ status: 401, errorCode: 'UNAUTHENTICATED', message: 'Bạn chưa đăng nhập.' }),
    )
})

describe('library route foundation (FR-001 FE, §12, AT-01 access only)', () => {
  it('FR001_resolves the six approved routes to the correct SCR, guard and layout', () => {
    const actual = appRouteDefinitions.filter(({ path }) =>
      ['/dictionary', '/translate', '/library/'].some((prefix) => path.startsWith(prefix)),
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
    for (const { path, url } of routes) {
      expect(findKnownRoute(url)?.path).toBe(path)
      const matches = matchRoutes(appRoutes, url)
      expect(matches?.[matches.length - 1]?.route.path).toBe(path)
    }
  })

  it.each(routes.filter(({ access }) => access === 'public'))(
    'FR001_guest can open $scr at $url',
    async ({ url, scr }) => {
      const router = renderRoute(url)
      expect(
        await screen.findByRole('heading', { name: scr === 'SCR-024' ? 'Tra từ điển' : '日' }),
      ).toBeVisible()
      expect(router.state.location.pathname).toBe(url)
    },
  )

  it.each(routes)('FR001_LEARNER can open $scr at $url', async ({ url, scr }) => {
    getCurrentUserMock.mockResolvedValue(user(['LEARNER']))
    const router = renderRoute(url)
    await waitFor(() => {
      const title =
        scr === 'SCR-024'
          ? 'Tra từ điển'
          : scr === 'SCR-025'
            ? '日'
            : scr === 'SCR-026'
              ? 'Dịch câu'
              : scr === 'SCR-027'
                ? 'Sổ tay cá nhân'
                : scr === 'SCR-028'
                  ? 'Bộ thẻ của tôi'
                  : scr === 'SCR-029'
                    ? 'Bộ thẻ kiểm thử'
                    : `Màn ${scr} đang dựng`
      expect(screen.getByRole('heading', { name: title })).toBeVisible()
    })
    expect(router.state.location.pathname).toBe(url)
  })

  it.each(privateRoutes)(
    'FR001_guest keeps the complete return destination for $url',
    async ({ url }) => {
      const destination = `${url}?source=library#sample`
      const router = renderRoute(destination)
      expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeVisible()
      expect(router.state.location.pathname).toBe('/login')
      expect(new URLSearchParams(router.state.location.search).get('redirect')).toBe(destination)
    },
  )

  it.each(
    privateRoutes.flatMap((route) =>
      (['ADMIN', 'CONTENT_EDITOR', 'TEACHER', 'MODERATOR', 'ACCOUNTANT'] as const).map((role) => ({
        ...route,
        role,
      })),
    ),
  )('FR001_$role alone cannot open $url', async ({ url, role }) => {
    getCurrentUserMock.mockResolvedValue(user([role]))
    const router = renderRoute(url)
    await waitFor(() => expect(router.state.location.pathname).toBe('/403'))
    expect(
      screen.queryByText(`Màn ${routes.find((route) => route.url === url)?.scr} đang dựng`),
    ).not.toBeInTheDocument()
  })

  it('FR001_accepts LEARNER when combined with another role', async () => {
    getCurrentUserMock.mockResolvedValue(user(['ADMIN', 'LEARNER']))
    const router = renderRoute('/library/decks/501')
    expect(await screen.findByRole('heading', { name: 'Bộ thẻ kiểm thử' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/library/decks/501')
  })

  it.each(privateRoutes)(
    'FR001_validates the login redirect to $url against the new access rules',
    ({ url }) => {
      const destination = `${url}?source=library#sample`
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

  it('exposes all six sample catalogs through the existing preview registry', () => {
    expect(previewRegistry.filter(({ id }) => id.startsWith('library-'))).toHaveLength(12)
    expect([
      ...new Set(
        previewRegistry.filter(({ id }) => id.startsWith('library-')).map(({ scr }) => scr),
      ),
    ]).toEqual(routes.map(({ scr }) => scr))
  })

  it.each(['ready', 'loading', 'empty', 'error'])(
    'FR025_renders the existing translation preview in %s without API calls',
    async (state) => {
      renderRoute(`/__preview/frame/library-translation-samples/${state}`)
      expect(await screen.findByRole('heading', { name: 'Dịch câu' })).toBeVisible()
      if (state === 'loading')
        expect(screen.getByRole('form', { name: 'Dịch câu' })).toHaveAttribute('aria-busy', 'true')
      if (state === 'empty')
        expect(screen.getByRole('heading', { name: 'Chưa có bản dịch' })).toBeVisible()
      if (state === 'error')
        expect(screen.getByText('Dịch tạm thời lỗi — lượt đã được hoàn')).toBeVisible()
      if (state === 'ready')
        expect(screen.getByRole('region', { name: 'Kết quả dịch' })).toBeVisible()
      expect(translate).not.toHaveBeenCalled()
      expect(getLexeme).not.toHaveBeenCalled()
      expect(getCurrentUserMock).not.toHaveBeenCalled()
    },
  )

  it('renders the original dictionary samples without a backend', async () => {
    renderRoute('/__preview/frame/library-dictionary-samples/ready')
    expect(await screen.findByRole('heading', { name: 'Tra từ điển' })).toBeVisible()
    expect(screen.getAllByRole('listitem')).toHaveLength(20)
    expect(getCurrentUserMock).not.toHaveBeenCalled()
    expect(searchDictionary).not.toHaveBeenCalled()
    expect(getLexeme).not.toHaveBeenCalled()
  })

  it.each(['library-note-samples', 'library-note-trash'])(
    'renders %s without private API calls',
    async (id) => {
      renderRoute(`/__preview/frame/${id}/ready`)
      expect(await screen.findByRole('heading', { name: 'Sổ tay cá nhân' })).toBeVisible()
      expect(listNotes).not.toHaveBeenCalled()
      expect(listTrash).not.toHaveBeenCalled()
      expect(getCurrentUserMock).not.toHaveBeenCalled()
    },
  )

  it.each(['library-deck-samples', 'library-card-samples', 'library-review-samples'])(
    'renders %s without backend or auth requests',
    async (id) => {
      renderRoute(`/__preview/frame/${id}/ready`)
      expect(
        await screen.findByRole('heading', {
          name: id === 'library-deck-samples' ? 'Bộ thẻ của tôi' : 'Từ dùng mỗi ngày',
        }),
      ).toBeVisible()
      expect(listDecks).not.toHaveBeenCalled()
      expect(getDeck).not.toHaveBeenCalled()
      expect(listDueCards).not.toHaveBeenCalled()
      expect(getCurrentUserMock).not.toHaveBeenCalled()
    },
  )
})
