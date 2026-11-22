import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { appRoutes } from './routes'

const games = [
  ['char-rain', 'CHAR_RAIN', 'Lọ Mực'],
  ['pinyin-match', 'PINYIN_MATCH', 'Ghép Pinyin'],
  ['radical-match', 'RADICAL_MATCH', 'Ghép Bộ Thủ'],
  ['char-catch', 'CHAR_CATCH', 'Bắt Chữ'],
  ['brush-stroke', 'BRUSH_STROKE', 'Mưa Chữ'],
  ['flying-dart', 'FLYING_DART', 'Phi Đao'],
] as const

beforeEach(() => {
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      .mockImplementation(async () =>
        Response.json({ error_code: 'UNAUTHENTICATED' }, { status: 401 }),
      ),
  )
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/'
})

function open(path: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] })
  render(<RouterProvider router={router} />)
  return router
}

describe('game application routes (FR-027)', () => {
  it('opens the public Hub at the root, including query strings', () => {
    open('/?source=web')
    expect(
      screen.getByRole('heading', { level: 1, name: 'Luyện tiếng Trung qua trò chơi' }),
    ).toBeVisible()
    expect(screen.getByRole('link', { name: 'Chọn game' })).toHaveAttribute(
      'aria-current',
      'page',
    )
    expect(screen.getByRole('main')).toHaveAttribute('id', 'game-content')
  })

  it.each(games)('uses the canonical route %s for guests without starting a round', async (slug, code) => {
    open(`/play/${slug}?hskLevel=1`)
    expect(screen.getByRole('heading', { level: 1, name: 'Ván chơi' })).toBeVisible()
    expect(screen.getByRole('region', { name: 'Ván chơi' })).toHaveAttribute('data-game-code', code)
    expect(screen.queryByRole('button', { name: 'Bắt đầu' })).not.toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'Đăng nhập để chơi' })).toBeVisible()
  })

  it.each(games)('loads %s in GameShell with the uppercase API code', async (slug, code, name) => {
    vi.stubEnv('VITE_API_BASE_URL', '')
    document.cookie = 'XSRF-TOKEN=fixture; Path=/'
    const fetchMock = vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url === '/api/auth/me') return Response.json({ id: 1, roles: ['LEARNER'] })
      if (url.startsWith('/stroke-data/')) {
        return Response.json({ strokes: ['M 20 50 L 300 50'], medians: [[[20, 50], [300, 50]]] })
      }
      if (url === '/api/community/games/' + code + '/rounds') {
        const now = Date.now()
        const isPinyin = code === 'PINYIN_MATCH'
        return Response.json({
          roundId: 5, gameCode: code, startedAt: new Date(now - 3000).toISOString(),
          serverTime: new Date(now).toISOString(), deadline: new Date(now + 60000).toISOString(),
          timeLimitSeconds: 60, pointsPerCorrect: 10, uiConfig: isPinyin ? { boards: [[1, 2, 3, 4, 5, 6]] } : {},
          items: (isPinyin ? ['一', '二', '三', '四', '五', '六'] : ['一']).map((text, index) => ({
            no: index + 1, type: isPinyin ? 'PINYIN_PAIR' : 'TRACE_CHAR', prompt: { text, hint: 'một' },
            rules: { minDurationMs: 300, maxReplays: 0, hasAudio: false },
          })),
        })
      }
      throw new Error('Unexpected request ' + url)
    })
    open(`/play/${slug}?hskLevel=2`)
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu' }))
    expect(await screen.findByRole('region', { name })).toBeVisible()
    expect(fetchMock).toHaveBeenCalledWith('/api/community/games/' + code + '/rounds',
      expect.objectContaining({ method: 'POST', body: '{"hskLevel":2}' }))
  })

  it.each([
    ...games.map(([slug, code]) => ['/play/' + code, slug]),
    ['/arcade/brush', 'brush-stroke'],
    ['/arcade/dart', 'flying-dart'],
  ])('redirects %s while preserving query and hash', async (from, slug) => {
    const router = open(from + '?hskLevel=2#play')
    await screen.findByRole('link', { name: 'Đăng nhập để chơi' })
    expect(router.state.location).toMatchObject({ pathname: '/play/' + slug, search: '?hskLevel=2', hash: '#play' })
  })

  it('looks up missed pinyin only after landing and sends no credentials to the public dictionary', async () => {
    vi.stubEnv('VITE_API_BASE_URL', '')
    document.cookie = 'XSRF-TOKEN=fixture; Path=/'
    const lexeme = '/api/public/dictionary/lexemes/11'
    const fetchMock = vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url === '/api/auth/me') return Response.json({ id: 1, roles: ['LEARNER'] })
      if (url === lexeme) return Response.json({ pinyin: 'yī' })
      if (url.startsWith('/stroke-data/')) {
        return Response.json({ strokes: ['M 20 50 L 300 50'], medians: [[[20, 50], [300, 50]]] })
      }
      if (url === '/api/community/games/BRUSH_STROKE/rounds') {
        const now = Date.now()
        return Response.json({
          roundId: 5, gameCode: 'BRUSH_STROKE', startedAt: new Date(now - 3000).toISOString(),
          serverTime: new Date(now).toISOString(), deadline: new Date(now + 60000).toISOString(),
          timeLimitSeconds: 60, pointsPerCorrect: 10,
          uiConfig: { fallStartMs: 500, fallStepMs: 0, fallMinMs: 500 },
          items: [1, 2].map((no) => ({
            no, type: 'TRACE_CHAR', lexemeId: 11, prompt: { text: '一', hint: 'một' },
            rules: { minDurationMs: 300, maxReplays: 0, hasAudio: false },
          })),
        })
      }
      throw new Error('Unexpected request ' + url)
    })
    open('/play/brush-stroke?hskLevel=1')
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu' }))
    await screen.findByRole('img', { name: 'Ô viết chữ' })
    expect(fetchMock.mock.calls.filter(([url]) => url === lexeme)).toHaveLength(0)
    expect(await screen.findByText('Bỏ lỡ: 一 · yī')).toBeVisible()
    expect(fetchMock.mock.calls.filter(([url]) => url === lexeme)).toEqual([
      [lexeme, { credentials: 'omit', signal: expect.any(AbortSignal) }],
    ])
  })

  it('AT-20 redirects through web login with the whole game URL encoded', async () => {
    vi.stubEnv('VITE_WEB_ORIGIN', '')
    const path = '/play/char-rain?hskLevel=1&source=hub#round'
    open(path)
    const link = await screen.findByRole('link', { name: 'Đăng nhập để chơi' })
    const href = link.getAttribute('href') ?? ''
    const login = new URL(href)
    expect(login.origin).toBe('http://localhost:5173')
    expect(login.pathname).toBe('/login')
    expect(href).toContain('redirect=http%3A%2F%2F')
    expect(login.searchParams.get('redirect')).toBe(window.location.origin + path)
  })

  it.each(['/unknown', '/play', '/play/char-rain/extra', '/play/unknown-game'])(
    'shows a recoverable 404 for %s',
    (path) => {
      open(path)
      expect(screen.getByRole('heading', { level: 1, name: 'Không tìm thấy trang' })).toBeVisible()
      expect(screen.getByRole('link', { name: 'Về danh sách trò chơi' })).toHaveAttribute(
        'href',
        '/',
      )
    },
  )

  it('returns to Hub through the 404 link without a full navigation', async () => {
    const user = userEvent.setup()
    const router = open('/not-here')
    await user.click(screen.getByRole('link', { name: 'Về danh sách trò chơi' }))
    expect(router.state.location.pathname).toBe('/')
    expect(
      screen.getByRole('heading', { level: 1, name: 'Luyện tiếng Trung qua trò chơi' }),
    ).toBeVisible()
  })

  it('allows keyboard users to reach the skip link first', async () => {
    const user = userEvent.setup()
    open('/')
    await user.tab()
    expect(screen.getByRole('link', { name: 'Đến nội dung chính' })).toHaveFocus()
  })
})
