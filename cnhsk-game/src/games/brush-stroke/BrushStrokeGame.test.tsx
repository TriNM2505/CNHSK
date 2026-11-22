import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

import { loadStrokeData } from '../../engine/strokeData'
import type { GameProps } from '../../shared/ui/GameShell'
import { BrushStrokeGame } from './BrushStrokeGame'
import type { ReadPinyin } from '../shared/FallingTarget'

vi.mock('../../engine/strokeData', () => ({ loadStrokeData: vi.fn() }))

// 二: a short line above a longer one.
const data = {
  strokes: ['M 100 800 L 300 800', 'M 50 600 L 350 600'],
  medians: [[[100, 800], [300, 800]], [[50, 600], [350, 600]]],
} as const
const items: GameProps['items'] = ['二', '一'].map((text, index) => ({
  no: index + 1, type: 'TRACE_CHAR', skill: 'WRITING', lexemeId: index + 31,
  prompt: { text }, rules: { minDurationMs: 300, maxReplays: 0, hasAudio: false },
}))

async function show() {
  const props = {
    items, disabled: false, uiConfig: { fallStartMs: 10000, fallStepMs: 100, fallMinMs: 4000 },
    onMissed: vi.fn(), onItemShown: vi.fn(), readPinyin: vi.fn<ReadPinyin>().mockResolvedValue('èr'),
    submitAnswer: vi.fn<GameProps['submitAnswer']>().mockResolvedValue({ correct: true, correctAnswer: 'PASS' }),
  }
  const view = render(<BrushStrokeGame {...props} />)
  await act(async () => {})
  return { ...view, ...props, disable: (disabled: boolean) => view.rerender(<BrushStrokeGame {...props} disabled={disabled} />) }
}

const advance = (ms: number) => act(async () => { await vi.advanceTimersByTimeAsync(ms) })

function draw(y: number, from: number, to: number) {
  return act(async () => {
    const pad = screen.getByRole('img', { name: 'Ô viết chữ' })
    vi.spyOn(pad, 'getBoundingClientRect').mockReturnValue(new DOMRect(0, 0, 1000, 1000))
    for (const [type, x] of [['pointerdown', from], ['pointerup', to]] as const) {
      const event = new MouseEvent(type, { bubbles: true, button: 0, clientX: x, clientY: y })
      Object.defineProperty(event, 'pointerId', { value: 1 })
      fireEvent(pad, event)
    }
  })
}
const shortLine = () => draw(100, 100, 300)
const longLine = () => draw(300, 350, 50)
const clear = () => screen.getByRole('button', { name: 'Xoá, viết lại' })

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'performance', 'requestAnimationFrame', 'cancelAnimationFrame'] })
  vi.mocked(loadStrokeData).mockReset().mockResolvedValue(data)
})
afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

it('accepts the whole character in any stroke order, sends PASS once and keeps 200 ms feedback', async () => {
  const { submitAnswer, readPinyin, onItemShown } = await show()
  expect(onItemShown.mock.calls).toEqual([[1]])
  await longLine()
  expect(submitAnswer).not.toHaveBeenCalled()
  await shortLine()
  await shortLine()
  expect(submitAnswer.mock.calls).toEqual([[1, 'PASS']])
  expect(screen.getByText('Đã hạ chữ 二')).toBeVisible()
  await advance(199)
  expect(onItemShown.mock.calls).toEqual([[1]])
  await advance(1)
  expect(onItemShown.mock.calls).toEqual([[1], [2]])
  expect(readPinyin).not.toHaveBeenCalled()
  expect(screen.getByText('Số chữ đã viết: 1')).toBeVisible()
})

it('shows no outline, and lets the learner clear the pad and write again', async () => {
  const { submitAnswer } = await show()
  expect(document.querySelector('.stroke-fill-future, .stroke-start')).toBeNull()
  expect(clear()).toBeDisabled()
  await draw(700, 50, 950)
  await draw(800, 50, 950)
  await draw(900, 50, 950)
  await act(async () => { fireEvent.click(clear()) })
  await shortLine()
  await longLine()
  expect(submitAnswer.mock.calls).toEqual([[1, 'PASS']])
})

it('AT-17 pauses, exposes the reduced-motion timer, and records a landed character as a miss', async () => {
  const { disable, readPinyin, onItemShown, onMissed, submitAnswer } = await show()
  const progress = screen.getByRole('progressbar', { hidden: true })
  expect(progress).toHaveClass('motion-reduce:block')
  expect(progress).toHaveAttribute('max', '10000')
  await advance(1000)
  disable(true)
  await shortLine()
  await advance(12000)
  expect(readPinyin).not.toHaveBeenCalled()
  expect(submitAnswer).not.toHaveBeenCalled()
  disable(false)
  await advance(8999)
  expect(submitAnswer).not.toHaveBeenCalled()
  await advance(1)
  // The server counts lives by wrong answers, so a landed character is answered, not skipped.
  expect(submitAnswer.mock.calls).toEqual([[1, 'MISS']])
  expect(onMissed).not.toHaveBeenCalled()
  expect(readPinyin).toHaveBeenCalledWith(31, expect.any(AbortSignal))
  expect(screen.getByText('Bỏ lỡ: 二 · èr')).toBeVisible()
  await advance(599)
  expect(onItemShown.mock.calls).toEqual([[1]])
  await advance(1)
  expect(onItemShown.mock.calls).toEqual([[1], [2]])
  expect(screen.getByRole('progressbar', { hidden: true })).toHaveAttribute('max', '9900')
})

it('E7 skips characters without stroke data, and a rejected PASS advances rather than locking the round', async () => {
  vi.mocked(loadStrokeData).mockResolvedValueOnce(null)
  const { onMissed, onItemShown, readPinyin, submitAnswer } = await show()
  expect(onMissed.mock.calls).toEqual([[1]])
  expect(onItemShown.mock.calls).toEqual([[2]])
  expect(readPinyin).not.toHaveBeenCalled()
  submitAnswer.mockRejectedValueOnce(new Error('No feedback'))
  await shortLine()
  await longLine()
  expect(submitAnswer.mock.calls).toEqual([[2, 'PASS']])
  expect(screen.getByText('Đã đi qua tất cả các chữ.')).toBeVisible()
})
