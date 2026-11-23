import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { CurrentUser, RoleCode } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { downloadBlob } from '@/shared/lib/downloadBlob'

import {
  createGameContest,
  decideGameContestExclusion,
  exportGameContestResults,
  getGameContestLive,
  getGameContestResults,
  type GameContestLive,
  type GameContestResults,
} from '../api/game-contests'
import { createModeratorPost } from '../api/posts'
import Scr080ModeratorPostComposerPage from './Scr080ModeratorPostComposerPage'
import Scr081GameContestPage from './Scr081GameContestPage'
import Scr082GameContestResultsPage from './Scr082GameContestResultsPage'

vi.mock('../api/game-contests', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/game-contests')>()),
  createGameContest: vi.fn(),
  decideGameContestExclusion: vi.fn(),
  exportGameContestResults: vi.fn(),
  getGameContestLive: vi.fn(),
  getGameContestResults: vi.fn(),
}))
vi.mock('../api/posts', () => ({ createModeratorPost: vi.fn() }))
vi.mock('../api/media', () => ({
  MAX_MEDIA_ITEMS: 4,
  getUploadSignature: vi.fn(),
  rejectFile: vi.fn(),
  uploadToCloudinary: vi.fn(),
}))
vi.mock('@/features/leaderboard/api/leaderboards', () => ({
  listLeaderboardGames: vi.fn(async () => [
    { code: 'INK_DROP', name: 'Lọ Mực' },
    { code: 'WORD_RAIN', name: 'Mưa Chữ' },
  ]),
}))
vi.mock('@/shared/lib/downloadBlob', () => ({ downloadBlob: vi.fn() }))

const MINUTE = 60_000

function user(roles: RoleCode[], id = 7): CurrentUser {
  return { id, displayName: 'Người dùng', email: 'contest@example.test', emailVerified: true, roles }
}

function authValue(current: CurrentUser | null): AuthContextValue {
  return {
    error: null,
    reloadCurrentUser: async () => undefined,
    setAuthenticatedUser: () => undefined,
    signOut: async () => undefined,
    status: current ? 'authenticated' : 'guest',
    user: current,
  }
}

function Landing({ label }: { label: string }) {
  const location = useLocation()
  const state = location.state as { announcement?: { title: string; content: string } } | null
  return (
    <>
      <p>{label}</p>
      <output aria-label="landing">{location.pathname}</output>
      {state?.announcement ? <pre aria-label="announcement">{`${state.announcement.title}\n${state.announcement.content}`}</pre> : null}
    </>
  )
}

/** `YYYY-MM-DDTHH:mm` as a datetime-local input expects, in the test machine's time zone. */
function localInput(date: Date): string {
  return new Date(date.getTime() - date.getTimezoneOffset() * MINUTE).toISOString().slice(0, 16)
}

function live(overrides: Partial<GameContestLive> = {}): GameContestLive {
  const now = Date.now()
  return {
    id: 5,
    title: 'Đua chữ cuối tuần',
    description: 'Chơi Lọ Mực trong một giờ.',
    gameCode: 'INK_DROP',
    startsAt: new Date(now - 10 * MINUTE).toISOString(),
    endsAt: new Date(now + 50 * MINUTE).toISOString(),
    resultsAt: new Date(now + 51 * MINUTE).toISOString(),
    prizeCount: 3,
    serverTime: new Date(now).toISOString(),
    phase: 'OPEN',
    participants: 2,
    ranking: [
      { rank: 1, userId: 9, displayName: 'An', points: 1500, durationMs: 61_000, achievedAt: new Date(now).toISOString() },
      { rank: 2, userId: 7, displayName: 'Bình', points: 1200, durationMs: 75_000, achievedAt: new Date(now).toISOString() },
    ],
    ...overrides,
  }
}

describe('SCR-080 moderator composer — game contest fields', () => {
  function renderComposer(state?: unknown) {
    return render(
      <AuthContext.Provider value={authValue(user(['MODERATOR']))}>
        <MemoryRouter initialEntries={[{ pathname: '/moderation/posts/new', state }]}>
          <Routes>
            <Route element={<Scr080ModeratorPostComposerPage />} path="/moderation/posts/new" />
            <Route element={<Landing label="Trang cuộc thi" />} path="/community/contests/:id" />
            <Route element={<Landing label="Bảng tin" />} path="/community" />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    )
  }

  async function chooseGameContest() {
    await userEvent.click(screen.getByRole('button', { name: /Cuộc thi game/ }))
  }

  beforeEach(() => {
    vi.mocked(createGameContest).mockReset().mockResolvedValue({ contestId: 77, postId: 300 })
    vi.mocked(createModeratorPost).mockReset()
  })

  it('shows the game, window and prize fields and drops pinning for a game contest', async () => {
    renderComposer()
    await chooseGameContest()
    expect(screen.getByRole('button', { name: /Cuộc thi game/ })).toHaveAttribute('aria-pressed', 'true')
    const games = await screen.findByRole('radiogroup', { name: 'Trò chơi của cuộc thi' })
    expect(await within(games).findAllByRole('radio')).toHaveLength(2)
    expect(screen.getByLabelText(/Mở lúc/)).toHaveAttribute('type', 'datetime-local')
    expect(screen.getByLabelText(/Đóng lúc/)).toHaveAttribute('type', 'datetime-local')
    expect(screen.getByLabelText(/Số người nhận giải/)).toHaveValue(3)
    expect(screen.getByLabelText(/Hướng dẫn cuộc thi/)).toBeVisible()
    expect(screen.queryByRole('checkbox', { name: /Ghim đầu bảng tin/ })).not.toBeInTheDocument()
  })

  it('validates the game, the window and the prize count before sending', async () => {
    renderComposer()
    await chooseGameContest()
    fireEvent.change(screen.getByLabelText(/Tiêu đề/), { target: { value: 'Đua chữ' } })
    fireEvent.change(screen.getByLabelText(/Hướng dẫn cuộc thi/), { target: { value: 'Thể lệ' } })
    fireEvent.change(screen.getByLabelText(/Mở lúc/), { target: { value: localInput(new Date(Date.now() + 120 * MINUTE)) } })
    fireEvent.change(screen.getByLabelText(/Đóng lúc/), { target: { value: localInput(new Date(Date.now() + 60 * MINUTE)) } })
    fireEvent.change(screen.getByLabelText(/Số người nhận giải/), { target: { value: '51' } })
    await userEvent.click(screen.getByRole('button', { name: 'Đăng ngay' }))

    expect(screen.getByText('Chọn trò chơi.')).toBeVisible()
    expect(screen.getByText('Giờ đóng phải sau giờ mở.')).toBeVisible()
    expect(screen.getByText('Từ 1 đến 50 người.')).toBeVisible()

    fireEvent.change(screen.getByLabelText(/Đóng lúc/), { target: { value: localInput(new Date(Date.now() - 60 * MINUTE)) } })
    await userEvent.click(screen.getByRole('button', { name: 'Đăng ngay' }))
    expect(screen.getByText('Chọn thời điểm trong tương lai.')).toBeVisible()
    expect(createGameContest).not.toHaveBeenCalled()
  })

  it('creates the contest with server times in UTC and opens its room', async () => {
    renderComposer()
    await chooseGameContest()
    const start = localInput(new Date(Date.now() + 60 * MINUTE))
    const end = localInput(new Date(Date.now() + 180 * MINUTE))
    await userEvent.click(await screen.findByRole('radio', { name: /Mưa Chữ/ }))
    fireEvent.change(screen.getByLabelText(/Tiêu đề/), { target: { value: '  Đua chữ  ' } })
    fireEvent.change(screen.getByLabelText(/Hướng dẫn cuộc thi/), { target: { value: ' Thể lệ ' } })
    fireEvent.change(screen.getByLabelText(/Mở lúc/), { target: { value: start } })
    fireEvent.change(screen.getByLabelText(/Đóng lúc/), { target: { value: end } })
    fireEvent.change(screen.getByLabelText(/Số người nhận giải/), { target: { value: '5' } })
    await userEvent.click(screen.getByRole('button', { name: 'Đăng ngay' }))

    await waitFor(() => expect(screen.getByLabelText('landing')).toHaveTextContent('/community/contests/77'))
    expect(createGameContest).toHaveBeenCalledWith({
      content: 'Thể lệ',
      endsAt: new Date(end).toISOString(),
      gameCode: 'WORD_RAIN',
      media: [],
      prizeCount: 5,
      startsAt: new Date(start).toISOString(),
      title: 'Đua chữ',
    })
    expect(createModeratorPost).not.toHaveBeenCalled()
  })

  it('keeps the form and shows the error when the contest cannot be created', async () => {
    vi.mocked(createGameContest).mockRejectedValue(new Error('Đang có cuộc thi khác trùng giờ.'))
    renderComposer()
    await chooseGameContest()
    await userEvent.click(await screen.findByRole('radio', { name: /Lọ Mực/ }))
    fireEvent.change(screen.getByLabelText(/Tiêu đề/), { target: { value: 'Đua chữ' } })
    fireEvent.change(screen.getByLabelText(/Hướng dẫn cuộc thi/), { target: { value: 'Thể lệ' } })
    await userEvent.click(screen.getByRole('button', { name: 'Đăng ngay' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Đang có cuộc thi khác trùng giờ.')
    expect(screen.getByRole('button', { name: 'Đăng ngay' })).toBeEnabled()
    expect(screen.getByLabelText(/Tiêu đề/)).toHaveValue('Đua chữ')
  })

  it('starts from the winners announcement handed over by the results page', () => {
    renderComposer({ announcement: { title: 'Công bố kết quả: Đua chữ', content: 'Xin chúc mừng' } })
    expect(screen.getByLabelText(/Tiêu đề/)).toHaveValue('Công bố kết quả: Đua chữ')
    expect(screen.getByLabelText(/Nội dung/)).toHaveValue('Xin chúc mừng')
    expect(screen.getByRole('button', { name: /Thông báo/ })).toHaveAttribute('aria-pressed', 'true')
  })
})

describe('SCR-081 game contest room', () => {
  function renderRoom(current: CurrentUser | null) {
    return render(
      <AuthContext.Provider value={authValue(current)}>
        <MemoryRouter initialEntries={['/community/contests/5']}>
          <Routes>
            <Route element={<Scr081GameContestPage />} path="/community/contests/:id" />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    )
  }

  beforeEach(() => {
    vi.mocked(getGameContestLive).mockReset()
  })
  afterEach(() => vi.unstubAllEnvs())

  it('WAITING shows the countdown, a locked play button and no ranking', async () => {
    const now = Date.now()
    vi.mocked(getGameContestLive).mockResolvedValue(
      live({
        phase: 'WAITING',
        startsAt: new Date(now + 2 * 60 * MINUTE).toISOString(),
        endsAt: new Date(now + 3 * 60 * MINUTE).toISOString(),
        resultsAt: new Date(now + 3 * 60 * MINUTE + 30_000).toISOString(),
        ranking: [],
        participants: 0,
      }),
    )
    renderRoom(user(['LEARNER']))
    expect(await screen.findByRole('heading', { name: 'Đua chữ cuối tuần' })).toBeVisible()
    expect(screen.getByText('Sắp mở')).toBeVisible()
    expect(screen.getByText('Cuộc thi mở sau')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Chưa tới giờ thi' })).toBeDisabled()
    expect(screen.queryByRole('list', { name: 'Bảng xếp hạng cuộc thi' })).not.toBeInTheDocument()
    expect(screen.getByText(/Lọ Mực · HSK 1–3/)).toBeVisible()
    expect(getGameContestLive).toHaveBeenCalledWith(5, expect.any(AbortSignal))
  })

  it('OPEN gives a learner the game link, the live ranking and their own place', async () => {
    vi.stubEnv('VITE_GAME_ORIGIN', 'http://game.test/')
    vi.mocked(getGameContestLive).mockResolvedValue(live())
    renderRoom(user(['LEARNER'], 7))
    const play = await screen.findByRole('link', { name: 'Vào thi' })
    expect(play).toHaveAttribute('href', 'http://game.test/play/ink-drop?contest=5')
    expect(play).toHaveAttribute('target', '_blank')
    expect(screen.getByText('Đang thi')).toBeVisible()
    const ranking = screen.getByRole('list', { name: 'Bảng xếp hạng cuộc thi' })
    expect(within(ranking).getAllByRole('listitem').map((row) => row.textContent)).toEqual([
      expect.stringContaining('An'),
      expect.stringContaining('Bình(bạn)'),
    ])
    expect(screen.getByText(/Bạn đang hạng 2 với 1\.200 điểm/)).toBeVisible()
    expect(screen.queryByRole('link', { name: /Xem kết quả, email người thắng/ })).not.toBeInTheDocument()
  })

  it('OPEN sends a guest to sign in and back to the room', async () => {
    vi.mocked(getGameContestLive).mockResolvedValue(live())
    renderRoom(null)
    expect(await screen.findByRole('link', { name: 'Đăng nhập để vào thi' })).toHaveAttribute(
      'href',
      '/login?redirect=%2Fcommunity%2Fcontests%2F5',
    )
  })

  it('TALLYING hides the ranking until the server has the final result', async () => {
    const now = Date.now()
    vi.mocked(getGameContestLive).mockResolvedValue(
      live({
        phase: 'TALLYING',
        startsAt: new Date(now - 60 * MINUTE).toISOString(),
        endsAt: new Date(now - 10_000).toISOString(),
        resultsAt: new Date(now + 20_000).toISOString(),
        ranking: [],
      }),
    )
    renderRoom(user(['LEARNER']))
    expect(await screen.findByText('Đang tính kết quả…')).toBeVisible()
    expect(screen.queryByRole('list', { name: 'Bảng xếp hạng cuộc thi' })).not.toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Bục vinh danh' })).not.toBeInTheDocument()
  })

  it('keeps tallying when the clock passed the results time but the server has not finished', async () => {
    const now = Date.now()
    vi.mocked(getGameContestLive).mockResolvedValue(
      live({
        phase: 'TALLYING',
        startsAt: new Date(now - 60 * MINUTE).toISOString(),
        endsAt: new Date(now - 2 * MINUTE).toISOString(),
        resultsAt: new Date(now - MINUTE).toISOString(),
        ranking: [],
      }),
    )
    renderRoom(user(['LEARNER']))
    expect(await screen.findByText('Đang tính kết quả…')).toBeVisible()
    expect(screen.queryByRole('list', { name: 'Bục vinh danh' })).not.toBeInTheDocument()
  })

  it('FINISHED reveals the podium and the final ranking; moderators get the results link', async () => {
    const now = Date.now()
    vi.mocked(getGameContestLive).mockResolvedValue(
      live({
        phase: 'FINISHED',
        startsAt: new Date(now - 120 * MINUTE).toISOString(),
        endsAt: new Date(now - 60 * MINUTE).toISOString(),
        resultsAt: new Date(now - 59 * MINUTE).toISOString(),
      }),
    )
    renderRoom(user(['MODERATOR']))
    const podium = await screen.findByRole('list', { name: 'Bục vinh danh' })
    expect(within(podium).getByText('An')).toBeVisible()
    expect(within(podium).getByText('1.500 điểm')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Bảng xếp hạng chung cuộc' })).toBeVisible()
    expect(screen.getByRole('link', { name: /Xem kết quả, email người thắng/ })).toHaveAttribute(
      'href',
      '/moderation/contests/5/results',
    )
    // A moderator without the learner role cannot play.
    expect(screen.queryByRole('link', { name: 'Vào thi' })).not.toBeInTheDocument()
  })

  it('offers a retry when the room cannot load', async () => {
    vi.mocked(getGameContestLive).mockRejectedValueOnce(new Error('offline')).mockResolvedValue(live())
    renderRoom(null)
    expect(await screen.findByRole('alert')).toHaveTextContent('Không tải được cuộc thi.')
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByRole('heading', { name: 'Đua chữ cuối tuần' })).toBeVisible()
    expect(getGameContestLive).toHaveBeenCalledTimes(2)
  })
})

describe('SCR-082 game contest results', () => {
  const results = (overrides: Partial<GameContestResults> = {}): GameContestResults => ({
    contest: live({ phase: 'FINISHED', participants: 12 }),
    winners: [
      { rank: 1, userId: 9, displayName: 'An', email: 'an@example.test', points: 1500, durationMs: 61_000, achievedAt: '2026-11-20T12:30:00Z' },
      { rank: 2, userId: 7, displayName: null, email: 'binh@example.test', points: 1200, durationMs: null, achievedAt: '2026-11-20T12:40:00Z' },
    ],
    excluded: [{ userId: 11, displayName: 'Cường', note: 'Dùng công cụ', decidedAt: '2026-11-20T13:10:00Z' }],
    ...overrides,
  })

  function renderResults() {
    return render(
      <AuthContext.Provider value={authValue(user(['MODERATOR']))}>
        <MemoryRouter initialEntries={['/moderation/contests/5/results']}>
          <Routes>
            <Route element={<Scr082GameContestResultsPage />} path="/moderation/contests/:id/results" />
            <Route element={<Landing label="Soạn bài" />} path="/moderation/posts/new" />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    )
  }

  beforeEach(() => {
    vi.mocked(getGameContestResults).mockReset().mockResolvedValue(results())
    vi.mocked(decideGameContestExclusion).mockReset().mockResolvedValue(undefined)
    vi.mocked(exportGameContestResults).mockReset()
    vi.mocked(downloadBlob).mockReset()
  })
  afterEach(() => vi.restoreAllMocks())

  it('lists winners with contact emails, times and the excluded players', async () => {
    renderResults()
    expect(await screen.findByRole('heading', { name: 'Kết quả: Đua chữ cuối tuần' })).toBeVisible()
    expect(screen.getByText('12 người chơi · top 3 nhận giải')).toBeVisible()
    const table = screen.getByRole('table', { name: 'Người đạt giải' })
    const rows = within(table).getAllByRole('row').slice(1)
    expect(within(rows[0]!).getByText('an@example.test')).toBeVisible()
    expect(within(rows[0]!).getByText('61.0 giây')).toBeVisible()
    expect(within(rows[1]!).getByText('Người chơi')).toBeVisible()
    expect(within(rows[1]!).getByText('—')).toBeVisible()
    expect(screen.getByText('Dùng công cụ', { exact: false })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Xem trang cuộc thi' })).toHaveAttribute('href', '/community/contests/5')
    expect(getGameContestResults).toHaveBeenCalledWith(5, expect.any(AbortSignal))
  })

  it('hands a winners announcement draft to the composer', async () => {
    renderResults()
    await userEvent.click(await screen.findByRole('button', { name: 'Soạn bài công bố' }))
    expect(screen.getByLabelText('landing')).toHaveTextContent('/moderation/posts/new')
    const draft = screen.getByLabelText('announcement').textContent!
    expect(draft).toContain('Công bố kết quả: Đua chữ cuối tuần')
    expect(draft).toContain('đã khép lại với 12 người chơi')
    expect(draft).toContain('1. An — 1500 điểm (61.0 giây)')
    expect(draft).toContain('2. Người chơi — 1200 điểm (—)')
    // Contact emails stay on the moderator screen, never in a public post.
    expect(draft).not.toContain('@example.test')
  })

  it('downloads the CSV with a fallback file name', async () => {
    const blob = new Blob(['rank,email'])
    vi.mocked(exportGameContestResults).mockResolvedValue({ blob, fileName: undefined } as never)
    renderResults()
    await userEvent.click(await screen.findByRole('button', { name: 'Tải CSV' }))
    await waitFor(() => expect(downloadBlob).toHaveBeenCalledWith(blob, 'cuoc-thi-5-ket-qua.csv'))
    expect(exportGameContestResults).toHaveBeenCalledWith(5)
  })

  it('excludes a winner with a reason and reloads; cancelling the prompt sends nothing', async () => {
    const prompt = vi.spyOn(window, 'prompt').mockReturnValueOnce(null).mockReturnValueOnce('  Gian lận  ')
    renderResults()
    const table = await screen.findByRole('table', { name: 'Người đạt giải' })
    await userEvent.click(within(table).getAllByRole('button', { name: 'Loại' })[0]!)
    expect(decideGameContestExclusion).not.toHaveBeenCalled()
    await userEvent.click(within(table).getAllByRole('button', { name: 'Loại' })[0]!)
    await waitFor(() => expect(decideGameContestExclusion).toHaveBeenCalledWith(5, 9, true, 'Gian lận'))
    await waitFor(() => expect(getGameContestResults).toHaveBeenCalledTimes(2))
    expect(prompt).toHaveBeenCalledTimes(2)
  })

  it('restores an excluded player and shows a failed decision', async () => {
    vi.mocked(decideGameContestExclusion).mockRejectedValue(new Error('Cuộc thi chưa kết thúc.'))
    renderResults()
    await userEvent.click(await screen.findByRole('button', { name: 'Khôi phục' }))
    expect(decideGameContestExclusion).toHaveBeenCalledWith(5, 11, false, 'Khôi phục')
    expect(await screen.findByRole('alert')).toHaveTextContent('Cuộc thi chưa kết thúc.')
    expect(getGameContestResults).toHaveBeenCalledTimes(1)
  })

  it('says when nobody qualified and offers a retry after a load error', async () => {
    vi.mocked(getGameContestResults)
      .mockReset()
      .mockRejectedValueOnce(new ApiError({ status: 409, errorCode: 'CONTEST_NOT_FINISHED', message: 'Chỉ xem được khi cuộc thi đã kết thúc.' }))
      .mockResolvedValue(results({ winners: [], excluded: [] }))
    renderResults()
    expect(await screen.findByRole('alert')).toHaveTextContent('Chỉ xem được khi cuộc thi đã kết thúc.')
    await act(async () => {
      await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    })
    expect(await screen.findByText('Không có người chơi hợp lệ.')).toBeVisible()
  })
})
