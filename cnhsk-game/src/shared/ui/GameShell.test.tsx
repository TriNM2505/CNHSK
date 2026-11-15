import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

import { DemoGame } from '../../games/_demo/DemoGame.fixture'
import type { RoundStart } from '../api/rounds'
import { GameShell } from './GameShell'

const base = '/api/community/games'
const round = base + '/rounds/42'
const start = base + '/CHAR_RAIN/rounds'
const now = Date.parse('2026-11-16T10:00:00Z')
const fetchMock = vi.fn<(input: string, init: RequestInit) => Promise<Response>>()

const fixture: RoundStart = {
  roundId: 42,
  gameCode: 'CHAR_RAIN',
  serverTime: new Date(now).toISOString(),
  startedAt: new Date(now).toISOString(),
  deadline: new Date(now + 8000).toISOString(),
  timeLimitSeconds: 5,
  pointsPerCorrect: 10,
  uiConfig: { label: 'Fixture game' },
  items: [1, 2].map((no) => ({ no, type: 'MEANING_CHOICE', prompt: {} })),
}
const result = {
  roundId: 42,
  points: 10,
  maxPoints: 20,
  correct: 1,
  wrong: 0,
  missed: 1,
  accuracy: 100,
  durationMs: 5000,
  skill: 'VOCAB',
  bestPoints: 10,
  newBest: true,
  suspicious: false,
}
const view = (answered: number[]) => ({
  ...fixture,
  serverTime: new Date(now + 3500).toISOString(),
  status: 'IN_PROGRESS',
  totalPoints: answered.length * 10,
  answered: answered.map((no) => ({ no, correct: true, correctAnswer: 'A' })),
})

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(now)
  vi.stubEnv('VITE_API_BASE_URL', '')
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  document.cookie = 'XSRF-TOKEN=fixture; Path=/'
})
afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

const json = (payload: unknown) => Response.json(payload)
const error = (status: number, error_code: string) => Response.json({ error_code }, { status })
const score = (no: number) => json({ no, totalPoints: no * 10, answeredCount: no })

function show(hskLevel?: number) {
  render(
    <MemoryRouter>
      <GameShell code="CHAR_RAIN" hskLevel={hskLevel} Game={DemoGame} />
    </MemoryRouter>,
  )
}

async function press(name: string) {
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name }))
  })
}

function advance(ms: number) {
  return act(async () => {
    await vi.advanceTimersByTimeAsync(ms)
  })
}

const paths = () => fetchMock.mock.calls.map(([path]) => path)

it.each([undefined, 9])('uses the route HSK level %s on round creation', async (level) => {
  fetchMock.mockResolvedValueOnce(json(fixture))
  show(level)
  expect(fetchMock).not.toHaveBeenCalled()
  await press('Bắt đầu')
  expect(fetchMock).toHaveBeenCalledTimes(1)
  expect(fetchMock).toHaveBeenCalledWith(
    start,
    expect.objectContaining({
      method: 'POST',
      body: level === undefined ? undefined : '{"hskLevel":9}',
    }),
  )
})

it.each([-5, 5])(
  'aligns the countdown and deadline with the server at a clock skew of %s minutes',
  async (minutes) => {
    vi.setSystemTime(now + minutes * 60000)
    fetchMock.mockResolvedValueOnce(json(fixture)).mockResolvedValueOnce(json(result))
    show()
    await press('Bắt đầu')
    expect(screen.getByText('Bắt đầu sau 3')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Trả lời 1' })).not.toBeInTheDocument()
    await advance(3000)
    expect(screen.getByText('Fixture game')).toBeVisible()
    expect(screen.getByText('5 giây')).toBeVisible()
    await advance(5000)
    expect(screen.getByText('Kỷ lục mới!')).toBeVisible()
    expect(screen.getByText('10 / 20')).toBeVisible()
    expect(screen.getByText('100%')).toBeVisible()
    await advance(1000)
    expect(paths()).toEqual([start, round + '/finish'])
  },
)

it('sends queued answers in order with one in-flight answer and finishes once when the deadline and last answer coincide', async () => {
  let release!: (response: Response) => void
  const lastAnswer = new Promise<Response>((resolve) => {
    release = resolve
  })
  fetchMock
    .mockResolvedValueOnce(json(fixture))
    .mockResolvedValueOnce(score(1))
    .mockReturnValueOnce(lastAnswer)
    .mockResolvedValueOnce(json(result))
  show()
  await press('Bắt đầu')
  await advance(3000)
  await press('Trả lời 1')
  await press('Trả lời 2')
  await press('Trả lời 2')
  await advance(5000)
  expect(paths()).toEqual([start, round + '/answers', round + '/answers'])
  await act(async () => {
    release(score(2))
  })
  expect(paths()).toEqual([start, round + '/answers', round + '/answers', round + '/finish'])
  expect(screen.getByRole('heading', { name: 'Kết quả ván chơi' })).toBeVisible()
})

it.each([true, false])(
  'checks whether a lost answer reached the server before replay (recorded=%s)',
  async (recorded) => {
    fetchMock
      .mockResolvedValueOnce(json(fixture))
      .mockRejectedValueOnce(new TypeError('Offline'))
      .mockResolvedValueOnce(json(view(recorded ? [1] : [])))
    if (!recorded) fetchMock.mockResolvedValueOnce(score(1))
    fetchMock.mockResolvedValueOnce(score(2)).mockResolvedValueOnce(json(result))
    show()
    await press('Bắt đầu')
    await advance(3200)
    await press('Trả lời 1')
    await press('Trả lời 2')
    expect(screen.getByText(/2 đang chờ/)).toBeVisible()
    expect(paths()).toEqual([start, round + '/answers'])
    await act(async () => {
      window.dispatchEvent(new Event('online'))
    })
    const calls = fetchMock.mock.calls.map(([path, options]) => [
      path,
      options.body ? JSON.parse(String(options.body)).no : null,
    ])
    expect(calls).toEqual([
      [start, null],
      [round + '/answers', 1],
      [round, null],
      ...(recorded ? [] : [[round + '/answers', 1]]),
      [round + '/answers', 2],
      [round + '/finish', null],
    ])
    expect(screen.getByRole('heading', { name: 'Kết quả ván chơi' })).toBeVisible()
  },
)

it('stops accepting answers after ROUND_TIME_OVER and finishes once', async () => {
  fetchMock
    .mockResolvedValueOnce(json(fixture))
    .mockResolvedValueOnce(error(422, 'ROUND_TIME_OVER'))
    .mockResolvedValueOnce(json(result))
  show()
  await press('Bắt đầu')
  await advance(3200)
  await press('Trả lời 1')
  expect(paths()).toEqual([start, round + '/answers', round + '/finish'])
  expect(screen.queryByRole('button', { name: 'Trả lời 2' })).not.toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Kết quả ván chơi' })).toBeVisible()
})

it.each([
  ['ROUND_ALREADY_SUBMITTED', 409, 'Ván đã kết thúc'],
  ['ROUND_EXPIRED', 422, 'Ván đã hết hạn'],
] as const)('shows %s without inventing a result', async (code, status, heading) => {
  fetchMock.mockResolvedValueOnce(json(fixture)).mockResolvedValueOnce(error(status, code))
  show()
  await press('Bắt đầu')
  await advance(8000)
  expect(screen.getByRole('heading', { name: heading })).toBeVisible()
  expect(screen.getByRole('link', { name: 'Về danh sách trò chơi' })).toHaveAttribute('href', '/')
  expect(screen.queryByText('Kỷ lục mới!')).not.toBeInTheDocument()
  expect(paths()).toEqual([start, round + '/finish'])
})

it('returns to Hub after the answer endpoint reports an expired round', async () => {
  fetchMock.mockResolvedValueOnce(json(fixture)).mockResolvedValueOnce(error(422, 'ROUND_EXPIRED'))
  show()
  await press('Bắt đầu')
  await advance(3200)
  await press('Trả lời 1')
  expect(screen.getByRole('heading', { name: 'Ván đã hết hạn' })).toBeVisible()
  expect(screen.getByRole('link', { name: 'Về danh sách trò chơi' })).toHaveAttribute('href', '/')
  expect(paths()).toEqual([start, round + '/answers'])
})

it('asks before abandoning, can continue, and abandons only after confirmation', async () => {
  fetchMock
    .mockResolvedValueOnce(json(fixture))
    .mockResolvedValueOnce(new Response(null, { status: 204 }))
  show()
  await press('Bắt đầu')
  await press('Thoát')
  expect(screen.getByRole('group', { name: 'Xác nhận thoát' })).toBeVisible()
  await press('Chơi tiếp')
  expect(paths()).toEqual([start])
  await press('Thoát')
  await press('Bỏ ván')
  expect(paths()).toEqual([start, round + '/abandon'])
  expect(screen.getByRole('heading', { name: 'Đã bỏ ván' })).toBeVisible()
})
