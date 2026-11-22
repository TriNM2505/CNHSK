import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'

import { loadStrokeData } from '../../engine/strokeData'
import type { ServedItem } from '../../shared/api/rounds'
import { CharRainGame } from './CharRainGame'

vi.mock('../../engine/strokeData', () => ({ loadStrokeData: vi.fn() }))

const data = {
  strokes: ['M 100 800 L 300 800', 'M 100 600 L 300 600'],
  medians: [[[100, 800], [300, 800]], [[100, 600], [300, 600]]],
} as const

function item(no: number, text: string): ServedItem {
  return { no, type: 'TRACE_CHAR', skill: 'WRITING', prompt: { text, hint: 'Chữ mẫu' },
    rules: { minDurationMs: 600, maxReplays: 0, hasAudio: false } }
}

beforeEach(() => {
  vi.mocked(loadStrokeData).mockReset()
  vi.mocked(loadStrokeData).mockResolvedValue(data)
})

function draw(canvas: Element, y: number, end = 'pointerup', startX = 100, endX = 300) {
  function pointer(type: string, x: number) {
    const event = new MouseEvent(type, { bubbles: true, button: 0, clientX: x, clientY: y })
    Object.defineProperty(event, 'pointerId', { value: 1 })
    fireEvent(canvas, event)
  }
  pointer('pointerdown', startX)
  pointer(end, endX)
}

it('AT-04 keeps a rejected stroke and sends PASS once after the final accepted stroke', async () => {
  const submitAnswer = vi.fn().mockResolvedValue({ correct: true, correctAnswer: 'PASS' })
  const onItemShown = vi.fn()
  render(<CharRainGame items={[item(1, '二')]} uiConfig={{}} disabled={false}
    submitAnswer={submitAnswer} onMissed={vi.fn()} onItemShown={onItemShown} />)
  const canvas = await screen.findByRole('img', { name: 'Khung tô nét' })
  vi.spyOn(canvas, 'getBoundingClientRect').mockReturnValue(new DOMRect(0, 0, 1000, 1000))
  expect(onItemShown).toHaveBeenCalledWith(1)

  draw(canvas, 900)
  expect(screen.getByText('Nét 1/2')).toBeVisible()
  expect(screen.getByText(/Bắt đầu chưa đúng vị trí/)).toBeVisible()
  draw(canvas, 100, 'pointercancel')
  expect(screen.getByText('Nét 1/2')).toBeVisible()
  draw(canvas, 100)
  expect(screen.getByText('Nét 2/2')).toBeVisible()
  expect(submitAnswer).not.toHaveBeenCalled()
  draw(canvas, 300)
  draw(canvas, 300)
  await waitFor(() => expect(screen.getByText('1/3 sao cho chữ vừa tô')).toBeVisible())
  expect(submitAnswer).toHaveBeenCalledTimes(1)
  expect(submitAnswer).toHaveBeenCalledWith(1, 'PASS')
})

it('spends ink on each wrong stroke, stays on the character and ends the round when the jar is empty', async () => {
  const submitAnswer = vi.fn()
  const endRound = vi.fn()
  render(<CharRainGame items={[item(1, '二')]} uiConfig={{ inkLives: 3, startGuide: false }} disabled={false}
    submitAnswer={submitAnswer} onMissed={vi.fn()} endRound={endRound} />)
  const canvas = await screen.findByRole('img', { name: 'Khung tô nét' })
  vi.spyOn(canvas, 'getBoundingClientRect').mockReturnValue(new DOMRect(0, 0, 1000, 1000))
  expect(canvas.querySelector('.stroke-start')).toBeNull()
  expect(screen.getByRole('img', { name: 'Còn 3/3 mực' })).toBeVisible()

  draw(canvas, 900, 'pointerup', 100, 100)
  draw(canvas, 900, 'pointerup', 100, 120)
  expect(screen.getByRole('img', { name: 'Còn 3/3 mực' })).toBeVisible()

  draw(canvas, 900)
  expect(screen.getByRole('img', { name: 'Còn 2/3 mực' })).toBeVisible()
  expect(screen.getByText('Nét 1/2')).toBeVisible()
  draw(canvas, 900)
  expect(endRound).not.toHaveBeenCalled()
  draw(canvas, 900)
  expect(screen.getByRole('img', { name: 'Còn 0/3 mực' })).toBeVisible()
  expect(endRound).toHaveBeenCalledTimes(1)
  expect(submitAnswer).not.toHaveBeenCalled()
})

it('reports a missing character once without reporting shown or sending an answer', async () => {
  vi.mocked(loadStrokeData).mockResolvedValueOnce(null)
  const onMissed = vi.fn()
  const onItemShown = vi.fn()
  const submitAnswer = vi.fn()
  render(<CharRainGame items={[item(1, '一'), item(2, '二')]} uiConfig={{}} disabled={false}
    submitAnswer={submitAnswer} onMissed={onMissed} onItemShown={onItemShown} />)
  await screen.findByRole('img', { name: 'Khung tô nét' })
  expect(onMissed).toHaveBeenCalledTimes(1)
  expect(onMissed).toHaveBeenCalledWith(1)
  expect(onItemShown).not.toHaveBeenCalledWith(1)
  expect(onItemShown).toHaveBeenCalledWith(2)
  expect(submitAnswer).not.toHaveBeenCalled()
})

it('ignores a late stroke-data result after unmount and locks disabled input', async () => {
  let finishLoad!: (value: typeof data) => void
  vi.mocked(loadStrokeData).mockReturnValueOnce(new Promise((resolve) => { finishLoad = resolve }))
  const onItemShown = vi.fn()
  const submitAnswer = vi.fn()
  const props = { uiConfig: {}, disabled: true, onItemShown, submitAnswer, onMissed: vi.fn() }
  const { unmount } = render(<CharRainGame {...props} items={[item(1, '一')]} />)
  unmount()
  render(<CharRainGame {...props} items={[item(2, '二')]} />)
  const canvas = await screen.findByRole('img', { name: 'Khung tô nét' })
  await act(async () => finishLoad(data))
  draw(canvas, 100)
  expect(onItemShown).not.toHaveBeenCalled()
  expect(submitAnswer).not.toHaveBeenCalled()
  expect(screen.getByText('Nét 1/2')).toBeVisible()
})
