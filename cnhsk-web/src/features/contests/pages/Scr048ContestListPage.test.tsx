import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { listLeaderboardGames } from '@/features/leaderboard/api/leaderboards'
import { listContests } from '../api/contests'
import { contestPageFixture } from '../test-fixtures'
import Scr048ContestListPage from './Scr048ContestListPage'

vi.mock('../api/contests', () => ({ listContests: vi.fn() }))
vi.mock('@/features/leaderboard/api/leaderboards', () => ({ listLeaderboardGames: vi.fn() }))
function mount() {
  return render(
    <MemoryRouter>
      <Scr048ContestListPage />
    </MemoryRouter>,
  )
}

describe('SCR-048 FR015', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(listContests).mockResolvedValue(contestPageFixture)
    vi.mocked(listLeaderboardGames).mockResolvedValue([{ code: 'CHAR_RAIN', name: 'Lọ Mực' }] as never)
  })
  it('labels exam and game contests and sends a game contest to its community page', async () => {
    vi.mocked(listContests).mockResolvedValue({
      ...contestPageFixture,
      items: [{ ...contestPageFixture.items[0]!, id: 7, name: 'Đua Lọ Mực', gameCode: 'CHAR_RAIN' }, contestPageFixture.items[1]!],
    })
    mount()
    const game = await screen.findByRole('article', { name: 'Đua Lọ Mực' })
    expect(game).toHaveTextContent('Trò chơi · Lọ Mực')
    expect(screen.getByRole('link', { name: 'Đua Lọ Mực' })).toHaveAttribute('href', '/community/contests/7')
    expect(screen.getByRole('article', { name: contestPageFixture.items[1]!.name })).toHaveTextContent('Kiến thức')
  })
  it('shows published contest groups, Vietnam schedules, prizes and detail links', async () => {
    mount()
    expect(
      await screen.findByRole('article', { name: contestPageFixture.items[0]!.name }),
    ).toBeVisible()
    for (const label of ['Đang diễn ra', 'Sắp diễn ra', 'Đã kết thúc']) {
      expect(screen.getByRole('region', { name: label })).toBeVisible()
    }
    expect(screen.getAllByText(/giờ Việt Nam/)).toHaveLength(3)
    expect(screen.getAllByRole('link', { name: /Xem cuộc thi/ })[0]).toHaveAttribute(
      'href',
      '/contests/2101',
    )
    expect(listContests).toHaveBeenCalledWith({ page: 0, size: 20 }, expect.any(AbortSignal))
  })
  it('filters by schedule and resets the requested page', async () => {
    const user = userEvent.setup()
    mount()
    await screen.findByRole('article', { name: contestPageFixture.items[0]!.name })
    await user.click(screen.getByRole('button', { name: 'Sắp diễn ra' }))
    await waitFor(() =>
      expect(listContests).toHaveBeenLastCalledWith(
        { state: 'UPCOMING', page: 0, size: 20 },
        expect.any(AbortSignal),
      ),
    )
  })
  it('distinguishes loading, empty and failed requests and permits retry', async () => {
    vi.mocked(listContests)
      .mockRejectedValueOnce(new Error('unavailable'))
      .mockResolvedValue({ ...contestPageFixture, items: [], totalElements: 0 })
    const user = userEvent.setup()
    mount()
    expect(screen.getByRole('status')).toHaveTextContent('Đang tải cuộc thi')
    expect(await screen.findByRole('alert')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Chưa có cuộc thi trong giai đoạn này.')).toBeVisible()
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
  })
})
