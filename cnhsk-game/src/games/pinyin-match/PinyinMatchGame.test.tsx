import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

import type { ServedItem } from '../../shared/api/rounds'
import { GameShell } from '../../shared/ui/GameShell'
import type { GameAnswer, GameProps } from '../../shared/ui/GameShell'
import { PinyinMatchGame } from './PinyinMatchGame'

const options = ['hé', 'lín', 'shān', 'shuǐ', 'tiān', 'dì'].map((text, index) => ({
  key: String.fromCharCode(65 + index), text,
}))
const items: ServedItem[] = ['河', '林', '山', '水', '天', '地', '人', '日', '月', '火', '白', '青']
  .map((text, index) => ({
    no: index + 1,
    type: 'PINYIN_PAIR',
    skill: 'VOCAB',
    prompt: { text },
    options,
    rules: { minDurationMs: 600, maxReplays: 0, hasAudio: false },
  }))
const boards = [[1, 2, 3, 4, 5, 6], [7, 8, 9, 10, 11, 12]]
const uiConfig = { boards }

function show(overrides: Partial<GameProps> = {}) {
  const submitAnswer = vi.fn<GameProps['submitAnswer']>()
    .mockImplementation(async (_no, key) => ({ correct: true, correctAnswer: key }))
  const props = { items, uiConfig, submitAnswer, disabled: false, onMissed: vi.fn(), ...overrides }
  return { ...render(<PinyinMatchGame {...props} />), submitAnswer }
}

beforeEach(() => vi.useFakeTimers())
afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

const button = (name: string) => screen.getByRole('button', { name })
const tiles = () => ['Từ tiếng Trung', 'Pinyin'].flatMap((name) =>
  within(screen.getByRole('group', { name })).getAllByRole('button'))
const click = (name: string) => act(async () => { fireEvent.click(button(name)) })
const advance = (ms: number) => act(async () => { await vi.advanceTimersByTimeAsync(ms) })

it('AT-18 selects a word, sends one key, then marks the pair and keeps both tiles in place', async () => {
  let resolve!: (value: GameAnswer) => void
  const { submitAnswer } = show()
  submitAnswer.mockReturnValueOnce(new Promise((done) => { resolve = done }))
  expect(button('hé')).toBeDisabled()
  await click('河')
  expect(button('河')).toHaveAttribute('aria-pressed', 'true')
  await click('hé')
  await click('hé')
  expect(submitAnswer).toHaveBeenCalledTimes(1)
  expect(submitAnswer).toHaveBeenCalledWith(1, 'A')
  expect(button('lín')).toBeDisabled()
  expect(screen.getByText('Đang chấm…')).toBeVisible()
  await advance(300)
  expect(screen.getByText('Đã gửi câu trả lời.')).toBeVisible()
  await act(async () => resolve({ correct: true, correctAnswer: 'A' }))
  await advance(250)
  for (const name of ['河', 'hé']) {
    expect(button(name)).toHaveClass('settled-right')
    expect(button(name)).toBeDisabled()
  }
  await click('林')
  expect(button('hé')).toBeDisabled()
  expect(button('lín')).toBeEnabled()
  expect(tiles()).toHaveLength(12)
})

it('E6 shows the right pair for 800 ms and keeps the mistakenly chosen card', async () => {
  const { submitAnswer } = show()
  submitAnswer.mockResolvedValueOnce({ correct: false, correctAnswer: 'A' })
  await click('河')
  await click('lín')
  expect(submitAnswer).toHaveBeenCalledWith(1, 'B')
  expect(screen.getByText('Cặp đúng: 河 — hé')).toBeVisible()
  expect(button('hé')).toHaveClass('answer-correct')
  await advance(799)
  expect(button('hé')).toBeDisabled()
  expect(button('河')).toBeVisible()
  await advance(1)
  for (const name of ['河', 'hé']) {
    expect(button(name)).toHaveClass('settled-wrong')
    expect(button(name)).toBeDisabled()
  }
  expect(tiles()).toHaveLength(12)
  await click('林')
  expect(button('lín')).toBeEnabled()
})

it('settles only the word after rejection and allows the next pair', async () => {
  const { submitAnswer } = show()
  submitAnswer.mockRejectedValueOnce(new Error('Feedback unavailable'))
  await click('河')
  await click('hé')
  expect(button('河')).toBeDisabled()
  await click('林')
  expect(button('hé')).toBeEnabled()
  await click('lín')
  expect(submitAnswer).toHaveBeenLastCalledWith(2, 'B')
})

it('stays on a board until its six pairs are done, then moves to the next one', async () => {
  const onItemShown = vi.fn()
  const { submitAnswer } = show({ items: [...items].reverse(), onItemShown })
  for (const board of boards) {
    for (const [index, no] of board.entries()) {
      await click(String(items[no - 1]?.prompt.text))
      await click(options[index]!.text)
      expect(submitAnswer).toHaveBeenLastCalledWith(no, options[index]!.key)
      await advance(250)
      expect(screen.getByRole('heading', { name: `Bảng ${boards.indexOf(board) + 1}/2` })).toBeVisible()
      expect(tiles()).toHaveLength(12)
    }
    expect(screen.getByText(board === boards[0] ? 'Xong bảng này, sang bảng tiếp theo…' : 'Đã ghép xong bảng cuối.')).toBeVisible()
    await advance(899)
    expect(screen.queryByText('Đã ghép xong các bảng.')).not.toBeInTheDocument()
    await advance(1)
    if (board === boards[0]) {
      expect(screen.getByRole('heading', { name: 'Bảng 2/2' })).toBeVisible()
      expect(within(screen.getByRole('group', { name: 'Pinyin' })).getAllByRole('button')).toHaveLength(6)
    }
  }
  expect(screen.getByText('Đã ghép xong các bảng.')).toBeVisible()
  expect(onItemShown).not.toHaveBeenCalled()
})

it('honors disabled and handles missing options without exposing playable cards', () => {
  show({ disabled: true })
  expect(button('河')).toBeDisabled()
  expect(button('hé')).toBeDisabled()
  cleanup()
  show({ items: items.map((item) => ({ ...item, options: undefined })) })
  expect(screen.getByRole('alert')).toHaveTextContent('Bảng này thiếu thẻ pinyin.')
  expect(screen.queryByRole('button')).not.toBeInTheDocument()
})

it('uses the previous submission time for pair 2 through GameShell', async () => {
  const now = Date.parse('2026-11-16T10:00:00Z')
  vi.setSystemTime(now)
  vi.stubEnv('VITE_API_BASE_URL', '')
  document.cookie = 'XSRF-TOKEN=fixture; Path=/'
  const fetchMock = vi.fn<typeof fetch>()
    .mockResolvedValueOnce(Response.json({
      roundId: 42, gameCode: 'PINYIN_MATCH', serverTime: new Date(now).toISOString(),
      startedAt: new Date(now).toISOString(), deadline: new Date(now + 93000).toISOString(),
      timeLimitSeconds: 90, pointsPerCorrect: 10, uiConfig, items,
    }))
    .mockResolvedValueOnce(Response.json({ no: 1, correct: true, correctAnswer: 'A', totalPoints: 10 }))
    .mockResolvedValueOnce(Response.json({ no: 2, correct: true, correctAnswer: 'B', totalPoints: 20 }))
  vi.stubGlobal('fetch', fetchMock)
  render(<MemoryRouter><GameShell code="PINYIN_MATCH" hskLevel={1} Game={PinyinMatchGame} /></MemoryRouter>)
  await click('Bắt đầu')
  await advance(3900)
  await click('河')
  await advance(100)
  await click('hé')
  await advance(1200)
  await click('林')
  await advance(100)
  await click('lín')
  const bodies = fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/answers'))
    .map(([, request]) => JSON.parse(String(request?.body)))
  expect(bodies).toEqual([
    { no: 1, answer: 'A', durationMs: 1000 },
    { no: 2, answer: 'B', durationMs: 1300 },
  ])
})
