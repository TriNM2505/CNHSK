import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

import { loadStrokeData } from '../../engine/strokeData'
import type { GameProps } from '../../shared/ui/GameShell'
import { BrushStrokeGame } from './BrushStrokeGame'
import type { ReadPinyin } from '../shared/FallingTarget'

vi.mock('../../engine/strokeData', () => ({ loadStrokeData: vi.fn() }))

const data = {
  strokes: ['M 100 800 L 300 800', 'M 100 600 L 300 600'],
  medians: [[[100, 800], [300, 800]], [[100, 600], [300, 600]]],
} as const
const items: GameProps['items'] = ['二', '一'].map((text, index) => ({
  no: index + 1, type: 'TRACE_CHAR', skill: 'WRITING', lexemeId: index + 31,
  prompt: { text }, rules: { minDurationMs: 300, maxReplays: 0, hasAudio: false },
}))

async function show() {
  const props = {
    items, disabled: false, uiConfig: { fallStartMs: 6000, fallStepMs: 150, fallMinMs: 3000 },
    onMissed: vi.fn(), onItemShown: vi.fn(), readPinyin: vi.fn<ReadPinyin>().mockResolvedValue('èr'),
    submitAnswer: vi.fn<GameProps['submitAnswer']>().mockResolvedValue({ correct: true, correctAnswer: 'PASS' }),
  }
  const view = render(<BrushStrokeGame {...props} />)
  await act(async () => {})
  return { ...view, ...props, disable: (disabled: boolean) => view.rerender(<BrushStrokeGame {...props} disabled={disabled} />) }
}

const advance = (ms: number) => act(async () => { await vi.advanceTimersByTimeAsync(ms) })

function draw(y: number) {
  const canvas = screen.getByRole('img', { name: 'Khung tô nét' })
  vi.spyOn(canvas, 'getBoundingClientRect').mockReturnValue(new DOMRect(0, 0, 1000, 1000))
  for (const [type, x] of [['pointerdown', 100], ['pointerup', 300]] as const) {
    const event = new MouseEvent(type, { bubbles: true, button: 0, clientX: x, clientY: y })
    Object.defineProperty(event, 'pointerId', { value: 1 })
    fireEvent(canvas, event)
  }
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'performance', 'requestAnimationFrame', 'cancelAnimationFrame'] })
  vi.mocked(loadStrokeData).mockReset().mockResolvedValue(data)
})
afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

it('AT-17 reuses the first median, sends PASS once and keeps 200 ms feedback', async () => {
  const { submitAnswer, readPinyin, onItemShown } = await show()
  expect(onItemShown.mock.calls).toEqual([[1]])
  draw(300)
  expect(submitAnswer).not.toHaveBeenCalled()
  await act(async () => { draw(100); draw(100) })
  expect(submitAnswer.mock.calls).toEqual([[1, 'PASS']])
  expect(screen.getByText('Đã hạ chữ 二')).toBeVisible()
  await advance(199)
  expect(onItemShown.mock.calls).toEqual([[1]])
  await advance(1)
  expect(onItemShown.mock.calls).toEqual([[1], [2]])
  expect(readPinyin).not.toHaveBeenCalled()
  expect(screen.getByText('Số chữ đã hạ: 1')).toBeVisible()
})

it('AT-17 pauses, exposes the reduced-motion timer and reveals pinyin only after ground contact', async () => {
  const { disable, readPinyin, onItemShown, onMissed, submitAnswer } = await show()
  const progress = screen.getByRole('progressbar', { hidden: true })
  expect(progress).toHaveClass('motion-reduce:block')
  expect(progress).toHaveAttribute('max', '6000')
  await advance(1000)
  disable(true)
  draw(100)
  await advance(8000)
  expect(readPinyin).not.toHaveBeenCalled()
  expect(submitAnswer).not.toHaveBeenCalled()
  disable(false)
  await advance(4999)
  expect(onMissed).not.toHaveBeenCalled()
  await advance(1)
  expect(onMissed.mock.calls).toEqual([[1]])
  expect(readPinyin).toHaveBeenCalledWith(31, expect.any(AbortSignal))
  expect(screen.getByText('Bỏ lỡ: 二 · èr')).toBeVisible()
  await advance(599)
  expect(onItemShown.mock.calls).toEqual([[1]])
  await advance(1)
  expect(onItemShown.mock.calls).toEqual([[1], [2]])
  expect(progress).not.toBeInTheDocument()
  expect(screen.getByRole('progressbar', { hidden: true })).toHaveAttribute('max', '5850')
})

it('E7 skips missing strokes, and a rejected PASS advances rather than locking the round', async () => {
  vi.mocked(loadStrokeData).mockResolvedValueOnce(null)
  const { onMissed, onItemShown, readPinyin, submitAnswer } = await show()
  expect(onMissed.mock.calls).toEqual([[1]])
  expect(onItemShown.mock.calls).toEqual([[2]])
  expect(readPinyin).not.toHaveBeenCalled()
  submitAnswer.mockRejectedValueOnce(new Error('No feedback'))
  await act(async () => draw(100))
  expect(submitAnswer.mock.calls).toEqual([[2, 'PASS']])
  expect(screen.getByText('Đã đi qua tất cả các chữ.')).toBeVisible()
})
