import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

import { DemoGame } from '../../games/_demo/DemoGame.fixture'
import { CharRainGame } from '../../games/char-rain/CharRainGame'
import { RadicalMatchGame } from '../../games/radical-match/RadicalMatchGame'
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
  items: [1, 2].map((no) => ({
    no,
    type: 'MEANING_CHOICE',
    skill: 'VOCAB',
    prompt: {},
    rules: { minDurationMs: 300, maxReplays: 0, hasAudio: false },
  })),
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

it('passes the server option text and key through to the game', async () => {
  fetchMock
    .mockResolvedValueOnce(
      json({
        ...fixture,
        items: fixture.items.map((item) => ({
          ...item,
          options: [{ key: 'B', text: 'Đáp án mẫu từ server' }],
        })),
      }),
    )
    .mockResolvedValueOnce(score(1))
  show()
  await press('Bắt đầu')
  await advance(3200)
  await press('Trả lời 1 · Đáp án mẫu từ server')
  expect(fetchMock).toHaveBeenLastCalledWith(
    round + '/answers',
    expect.objectContaining({ body: expect.stringContaining('"answer":"B"') }),
  )
})

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
    expect(paths()).not.toContain(round + '/finish')
    expect(screen.queryByRole('heading', { name: 'Kết quả ván chơi' })).not.toBeInTheDocument()
    await advance(800)
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

it.each([false, true])('measures duration from the first onItemShown when supplied (reported=%s)', async (reported) => {
  fetchMock.mockResolvedValueOnce(json(fixture))
    .mockResolvedValueOnce(json({ no: 2, correct: true, correctAnswer: 'A', totalPoints: 10 }))
    .mockResolvedValueOnce(json(result))

  function TimedGame({ onItemShown, onMissed, submitAnswer, disabled }: import('./GameShell').GameProps) {
    return <>
      <button disabled={disabled} onClick={() => onMissed(1)}>Bỏ lỡ</button>
      <button onClick={() => onItemShown?.(2)}>Hiện câu 2</button>
      <button onClick={() => { void submitAnswer(2, 'A') }}>Gửi câu 2</button>
    </>
  }

  render(<MemoryRouter><GameShell code="CHAR_RAIN" Game={TimedGame} /></MemoryRouter>)
  await press('Bắt đầu')
  await advance(3100)
  await press('Bỏ lỡ')
  expect(paths()).toEqual([start])
  await advance(2000)
  if (reported) await press('Hiện câu 2')
  await advance(150)
  if (reported) await press('Hiện câu 2')
  await advance(250)
  await press('Gửi câu 2')

  const body = fetchMock.mock.calls.find(([url]) => url.endsWith('/answers'))?.[1].body
  expect(JSON.parse(String(body))).toEqual({ no: 2, answer: 'A', durationMs: reported ? 400 : 2400 })
  await advance(799)
  expect(paths()).not.toContain(round + '/finish')
  await advance(1)
  expect(paths()).toEqual([start, round + '/answers', round + '/finish'])
})

it('reports recovered server feedback to the actual radical game without reposting', async () => {
  const radicalItems = fixture.items.map((item, index) => ({
    ...item,
    prompt: { text: index === 0 ? '河' : '林', hint: 'Nghĩa mẫu' },
    options: [{ key: 'A', text: '氵' }, { key: 'B', text: '木' }],
  }))
  fetchMock.mockResolvedValueOnce(json({ ...fixture, items: radicalItems }))
    .mockRejectedValueOnce(new TypeError('offline'))
    .mockResolvedValueOnce(json({
      ...view([1]), items: radicalItems,
      answered: [{ no: 1, correct: false, correctAnswer: 'B' }],
    }))
  render(<MemoryRouter><GameShell code="RADICAL_MATCH" Game={RadicalMatchGame} /></MemoryRouter>)
  await press('Bắt đầu')
  await advance(3200)
  await press('氵')
  expect(screen.getByText(/Mất kết nối, đang thử lại/)).toBeVisible()
  await act(async () => { window.dispatchEvent(new Event('online')) })
  expect(screen.getByText('Bộ thủ đúng: 木')).toBeVisible()
  expect(paths()).toEqual([base + '/RADICAL_MATCH/rounds', round + '/answers', round])
  await advance(800)
  expect(screen.getByRole('heading', { name: '林' })).toBeVisible()
})

it('finishes a real Mưa Chữ view when its only character is missing, without posting an answer', async () => {
  const missing = { ...fixture.items[0], prompt: { text: '𠀀', hint: 'Chữ mẫu' } }
  fetchMock.mockResolvedValueOnce(json({ ...fixture, items: [missing] }))
    .mockResolvedValueOnce(Response.json({}, { status: 404 }))
    .mockResolvedValueOnce(json(result))
  render(<MemoryRouter><GameShell code="CHAR_RAIN" Game={CharRainGame} /></MemoryRouter>)
  await press('Bắt đầu')
  await advance(3200)
  expect(paths()).toEqual([start, '/stroke-data/%F0%A0%80%80.json'])
  await advance(800)
  expect(paths()).toEqual([start, '/stroke-data/%F0%A0%80%80.json', round + '/finish'])
})

it('ends at the server deadline when it comes before the final feedback window', async () => {
  const last = { ...fixture, items: fixture.items.slice(0, 1) }
  fetchMock.mockResolvedValueOnce(json(last))
    .mockResolvedValueOnce(json({ no: 1, correct: false, correctAnswer: 'B', totalPoints: 0 }))
    .mockResolvedValueOnce(json(result))

  show()
  await press('Bắt đầu')
  await advance(7900)
  await press('Trả lời 1')
  await advance(99)
  expect(paths()).not.toContain(round + '/finish')
  await advance(1)
  expect(paths()).toEqual([start, round + '/answers', round + '/finish'])
})
