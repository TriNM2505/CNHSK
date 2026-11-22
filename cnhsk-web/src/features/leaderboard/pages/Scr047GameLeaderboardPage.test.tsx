import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { AuthContext, type AuthContextValue } from '@/shared/auth/AuthContext'

import Scr047GameLeaderboardPage from './Scr047GameLeaderboardPage'

const auth: AuthContextValue = {
  error: null,
  reloadCurrentUser: vi.fn(),
  setAuthenticatedUser: vi.fn(),
  signOut: vi.fn(),
  status: 'guest',
  user: null,
}
const games = [
  { code: 'CHAR_RAIN', name: 'Lọ Mực' },
  { code: 'PINYIN_MATCH', name: 'Ghép Pinyin' },
  { code: 'RADICAL_MATCH', name: 'Ghép Bộ Thủ' },
  { code: 'CHAR_CATCH', name: 'Bắt Chữ' },
  { code: 'BRUSH_STROKE', name: 'Mưa Chữ' },
  { code: 'FLYING_DART', name: 'Phi Đao' },
]
const board = {
  type: 'GAME',
  ref: 'FLYING_DART',
  period: 'all',
  periodStart: null,
  periodEnd: null,
  entries: [],
  myEntry: null,
}

afterEach(() => vi.unstubAllGlobals())

describe('SCR-047 game leaderboard', () => {
  it('requests the selected game and renders all six server-provided choices', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/leaderboards/games'))
        return new Response(JSON.stringify(games), { status: 200 })
      if (url.includes('/games/FLYING_DART?period=all'))
        return new Response(JSON.stringify(board), { status: 200 })
      throw new Error(`Unexpected request ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)
    render(
      <AuthContext.Provider value={auth}>
        <MemoryRouter initialEntries={['/leaderboards/games?game=FLYING_DART&period=all']}>
          <Scr047GameLeaderboardPage />
        </MemoryRouter>
      </AuthContext.Provider>,
    )
    expect(await screen.findByText('Chưa có ai trong kỳ này')).toBeInTheDocument()
    expect(screen.getByLabelText('Trò chơi')).toHaveValue('FLYING_DART')
    expect(screen.getByRole('option', { name: 'Mưa Chữ' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Phi Đao' })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/games/FLYING_DART?period=all'),
      expect.any(Object),
    )
  })

  it('shows the ranking of the HSK level given in the link, HSK 1 to 3 only', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/leaderboards/games'))
        return new Response(JSON.stringify(games), { status: 200 })
      if (url.includes('/games/FLYING_DART?period=all&hskLevel=2'))
        return new Response(JSON.stringify(board), { status: 200 })
      throw new Error(`Unexpected request ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)
    render(
      <AuthContext.Provider value={auth}>
        <MemoryRouter initialEntries={['/leaderboards/games?game=FLYING_DART&period=all&hskLevel=2']}>
          <Scr047GameLeaderboardPage />
        </MemoryRouter>
      </AuthContext.Provider>,
    )
    expect(await screen.findByText('Chưa có ai trong kỳ này')).toBeInTheDocument()
    const level = screen.getByLabelText('Cấp độ HSK')
    expect(level).toHaveValue('2')
    expect(Array.from((level as HTMLSelectElement).options).map((option) => option.value)).toEqual(['1', '2', '3'])
  })

  it('defaults a missing selection to the first game returned by the API', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/leaderboards/games'))
        return new Response(JSON.stringify(games), { status: 200 })
      return new Response(JSON.stringify({ ...board, ref: 'CHAR_RAIN', period: 'week' }), {
        status: 200,
      })
    })
    vi.stubGlobal('fetch', fetchMock)
    render(
      <AuthContext.Provider value={auth}>
        <MemoryRouter initialEntries={['/leaderboards/games']}>
          <Scr047GameLeaderboardPage />
        </MemoryRouter>
      </AuthContext.Provider>,
    )
    await waitFor(() => expect(screen.getByLabelText('Trò chơi')).toHaveValue('CHAR_RAIN'))
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('/games/CHAR_RAIN?period=week'),
        expect.any(Object),
      ),
    )
    expect(await screen.findByText('Chưa có ai trong kỳ này')).toBeInTheDocument()
    expect(screen.getByLabelText('Trò chơi')).toHaveValue('CHAR_RAIN')
  })
})
