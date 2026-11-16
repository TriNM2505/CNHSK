import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

import { GameShell } from '../../shared/ui/GameShell'
import type { GameProps } from '../../shared/ui/GameShell'
import type { ReadPinyin } from '../shared/FallingTarget'
import { FlyingDartGame } from './FlyingDartGame'

const items: GameProps['items'] = ['你好', '学习'].map((text, index) => ({
  no: index + 1, type: 'PINYIN_INPUT', skill: 'VOCAB', lexemeId: index + 51,
  prompt: { text, hint: index === 0 ? 'xin chào' : 'học tập' },
  rules: { minDurationMs: 700, maxReplays: 0, hasAudio: false },
}))
const uiConfig = { fallStartMs: 6000, fallStepMs: 150, fallMinMs: 3000, maxInputLength: 40 }

function show() {
  const props = {
    items, disabled: false, uiConfig, onMissed: vi.fn(), onItemShown: vi.fn(),
    readPinyin: vi.fn<ReadPinyin>().mockResolvedValue('nǐ hǎo'),
    submitAnswer: vi.fn<GameProps['submitAnswer']>().mockResolvedValue({ correct: false, correctAnswer: 'nǐ hǎo' }),
  }
  const view = render(<FlyingDartGame {...props} />)
  return { ...view, ...props, disable: (disabled: boolean) => view.rerender(<FlyingDartGame {...props} disabled={disabled} />) }
}

const field = () => screen.getByRole('textbox', { name: 'Gõ pinyin' })
const advance = (ms: number) => act(async () => { await vi.advanceTimersByTimeAsync(ms) })
async function enter(value: string, isComposing = false) {
  await act(async () => {
    fireEvent.change(field(), { target: { value } })
    fireEvent.keyDown(field(), { key: 'Enter', isComposing })
  })
}

beforeEach(() => vi.useFakeTimers({
  toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval', 'Date', 'performance',
    'requestAnimationFrame', 'cancelAnimationFrame'],
}))
afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/'
})

it('AT-22 blocks partial tones, retains focus and lets learners try again without another POST', async () => {
  const { submitAnswer, readPinyin, onMissed } = show()
  expect(field()).toHaveFocus()
  expect(field()).toHaveAttribute('autocomplete', 'off')
  expect(field()).toHaveAttribute('autocapitalize', 'off')
  expect(field()).toHaveAttribute('spellcheck', 'false')
  expect(field()).toHaveAttribute('maxlength', '40')
  await enter('ni3hao')
  expect(screen.getByRole('alert')).toHaveTextContent('Gõ đủ thanh cho mọi âm tiết hoặc bỏ hết thanh')
  await enter('ni3hao3', true)
  expect(submitAnswer).not.toHaveBeenCalled()
  await enter('ni2hao3')
  expect(submitAnswer.mock.calls).toEqual([[1, 'ni2hao3']])
  expect(field()).toHaveValue('')
  expect(field()).toHaveFocus()
  expect(screen.getByText('Pinyin đúng: nǐ hǎo')).toBeVisible()
  await enter('ni3hao3')
  expect(field()).toHaveValue('ni3hao3')
  expect(submitAnswer).toHaveBeenCalledTimes(1)
  await advance(6000)
  expect(screen.getByText('Bỏ lỡ: 你好 · nǐ hǎo')).toBeVisible()
  expect(readPinyin).not.toHaveBeenCalled()
  expect(onMissed).not.toHaveBeenCalled()
  await advance(600)
  expect(field()).toHaveFocus()
  expect(screen.getByText('学习')).toBeVisible()
})

it('pauses while disabled and aborts lookup at 600 ms without waiting for it', async () => {
  const { disable, onItemShown, onMissed, readPinyin, submitAnswer } = show()
  let resolveLookup!: (value: string) => void
  readPinyin.mockReturnValueOnce(new Promise((resolve) => { resolveLookup = resolve }))
  await advance(2200)
  disable(true)
  expect(field()).toBeDisabled()
  await advance(7000)
  expect(onMissed).not.toHaveBeenCalled()
  disable(false)
  await advance(3799)
  expect(readPinyin).not.toHaveBeenCalled()
  await advance(1)
  expect(onItemShown.mock.calls).toEqual([[1]])
  expect(onMissed.mock.calls).toEqual([[1]])
  expect(screen.getByText('Bỏ lỡ: 你好')).toBeVisible()
  const [id, signal] = readPinyin.mock.calls[0]!
  expect(id).toBe(51)
  expect(signal.aborted).toBe(false)
  await advance(599)
  expect(signal.aborted).toBe(false)
  await advance(1)
  expect(signal.aborted).toBe(true)
  await act(async () => resolveLookup('nǐ hǎo'))
  expect(screen.queryByText(/nǐ hǎo/)).not.toBeInTheDocument()
  expect(screen.getByText('学习')).toBeVisible()
  expect(submitAnswer).not.toHaveBeenCalled()
})

it('advances after a rejected answer and shows a successful shot for 200 ms', async () => {
  const { submitAnswer } = show()
  submitAnswer.mockRejectedValueOnce(new Error('No grade'))
    .mockResolvedValueOnce({ correct: true, correctAnswer: 'xuéxí' })
  await enter('ni3hao3')
  expect(screen.getByText('学习')).toBeVisible()
  await enter('xue2xi2')
  expect(screen.getByText('Đã hạ từ 学习')).toBeVisible()
  await advance(200)
  expect(screen.getByText('Đã đi qua tất cả các từ.')).toBeVisible()
})

it('E10 finishes once after all 60 items even when the round has time left', async () => {
  vi.stubEnv('VITE_API_BASE_URL', '')
  document.cookie = 'XSRF-TOKEN=fixture; Path=/'
  const now = Date.now()
  const allItems = Array.from({ length: 60 }, (_, index) => ({ ...items[0], no: index + 1 }))
  const fetchMock = vi.fn<typeof fetch>().mockResolvedValueOnce(Response.json({
    roundId: 41, gameCode: 'FLYING_DART', startedAt: new Date(now - 3000).toISOString(),
    serverTime: new Date(now).toISOString(), deadline: new Date(now + 60000).toISOString(),
    timeLimitSeconds: 60, pointsPerCorrect: 10, items: allItems,
    uiConfig: { fallStartMs: 10, fallStepMs: 0, fallMinMs: 10 },
  })).mockResolvedValueOnce(Response.json({
    roundId: 41, points: 0, maxPoints: 600, correct: 0, wrong: 0, missed: 60, bestPoints: 0,
  }))
  vi.stubGlobal('fetch', fetchMock)
  render(<MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
    <GameShell code="FLYING_DART" Game={FlyingDartGame} />
  </MemoryRouter>)
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Bắt đầu' })) })
  for (let i = 0; i < 60; i += 1) {
    await advance(10)
    await advance(600)
  }
  expect(fetchMock).toHaveBeenCalledTimes(1)
  await advance(200)
  await advance(1000)
  expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
    '/api/community/games/FLYING_DART/rounds',
    '/api/community/games/rounds/41/finish',
  ])
  expect(Date.now() - now).toBeLessThan(60000)
})
