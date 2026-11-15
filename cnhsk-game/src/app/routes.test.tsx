import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { appRoutes } from './routes'

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
    expect(screen.getByRole('link', { name: 'Danh sách trò chơi' })).toHaveAttribute(
      'aria-current',
      'page',
    )
    expect(screen.getByRole('main')).toHaveAttribute('id', 'game-content')
  })

  it.each([
    'CHAR_RAIN',
    'PINYIN_MATCH',
    'RADICAL_MATCH',
    'CHAR_CATCH',
    'BRUSH_STROKE',
    'FLYING_DART',
  ])('preserves the game route parameter %s without starting a round', async (code) => {
    open(`/play/${code}?hskLevel=1`)
    expect(screen.getByRole('heading', { level: 1, name: 'Ván chơi' })).toBeVisible()
    expect(screen.getByRole('region', { name: 'Ván chơi' })).toHaveAttribute('data-game-code', code)
    expect(screen.queryByRole('button', { name: 'Bắt đầu' })).not.toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'Đăng nhập để chơi' })).toBeVisible()
  })

  it('AT-20 redirects through web login with the whole game URL encoded', async () => {
    vi.stubEnv('VITE_WEB_ORIGIN', '')
    const path = '/play/CHAR_RAIN?hskLevel=1&source=hub#round'
    open(path)
    const link = await screen.findByRole('link', { name: 'Đăng nhập để chơi' })
    const href = link.getAttribute('href') ?? ''
    const login = new URL(href)
    expect(login.origin).toBe('http://localhost:5173')
    expect(login.pathname).toBe('/login')
    expect(href).toContain('redirect=http%3A%2F%2F')
    expect(login.searchParams.get('redirect')).toBe(window.location.origin + path)
  })

  it.each(['/unknown', '/play', '/play/CHAR_RAIN/extra'])(
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
