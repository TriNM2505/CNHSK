import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { appRoutes } from './routes'

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
  ])('preserves the game route parameter %s without starting a round', (code) => {
    open(`/play/${code}?hskLevel=1`)
    expect(screen.getByRole('heading', { level: 1, name: 'Ván chơi' })).toBeVisible()
    expect(screen.getByRole('region', { name: 'Ván chơi' })).toHaveAttribute('data-game-code', code)
    expect(screen.queryByRole('button', { name: 'Bắt đầu' })).not.toBeInTheDocument()
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
