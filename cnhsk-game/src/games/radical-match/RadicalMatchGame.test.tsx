import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'

import type { ServedItem } from '../../shared/api/rounds'
import type { GameAnswer } from '../../shared/ui/GameShell'
import { RadicalMatchGame } from './RadicalMatchGame'

const items: ServedItem[] = ['河', '林'].map((text, index) => ({
  no: index + 1, type: 'RADICAL_CHOICE', skill: 'CHARACTER', prompt: { text, hint: 'Nghĩa mẫu' },
  options: ['氵', '木', '口', '日'].map((label, key) => ({ key: String.fromCharCode(65 + key), text: label })),
  rules: { minDurationMs: 700, maxReplays: 0, hasAudio: false },
}))

afterEach(() => vi.useRealTimers())

it.each([[true, 500], [false, 800]] as const)('uses server feedback and advances after %i / %i ms', async (correct, delay) => {
  vi.useFakeTimers()
  let reply!: (value: GameAnswer) => void
  const submitAnswer = vi.fn().mockReturnValue(new Promise((resolve) => { reply = resolve }))
  const onItemShown = vi.fn()
  render(<RadicalMatchGame items={items} uiConfig={{}} submitAnswer={submitAnswer}
    onMissed={vi.fn()} onItemShown={onItemShown} disabled={false} />)
  const choice = screen.getByRole('button', { name: '氵' })
  fireEvent.click(choice)
  fireEvent.click(choice)
  expect(submitAnswer).toHaveBeenCalledTimes(1)
  expect(submitAnswer).toHaveBeenCalledWith(1, 'A')
  expect(choice).toBeDisabled()
  expect(screen.getByText('Đang chấm…')).toBeVisible()
  await act(async () => vi.advanceTimersByTimeAsync(300))
  expect(screen.queryByText('Đang chấm…')).not.toBeInTheDocument()
  await act(async () => reply({ correct, correctAnswer: correct ? 'A' : 'B' }))
  expect(screen.getByText(correct ? 'Chính xác' : 'Bộ thủ đúng: 木')).toBeVisible()
  await act(async () => vi.advanceTimersByTimeAsync(delay - 1))
  expect(screen.getByRole('heading', { name: '河' })).toBeVisible()
  expect(onItemShown).not.toHaveBeenCalledWith(2)
  await act(async () => vi.advanceTimersByTimeAsync(1))
  expect(screen.getByRole('heading', { name: '林' })).toBeVisible()
  expect(onItemShown).toHaveBeenCalledWith(2)
})

it('locks disabled input and handles null options as a missed item', () => {
  const submitAnswer = vi.fn()
  const onMissed = vi.fn()
  const missing = { ...items[0]!, options: null } as unknown as ServedItem
  const { rerender } = render(<RadicalMatchGame items={[missing]} uiConfig={{}} disabled
    submitAnswer={submitAnswer} onMissed={onMissed} />)
  const skip = screen.getByRole('button', { name: 'Bỏ qua câu thiếu phương án' })
  expect(skip).toBeDisabled()
  fireEvent.click(skip)
  expect(onMissed).not.toHaveBeenCalled()
  rerender(<RadicalMatchGame items={[missing]} uiConfig={{}} disabled={false}
    submitAnswer={submitAnswer} onMissed={onMissed} />)
  fireEvent.click(skip)
  expect(onMissed).toHaveBeenCalledTimes(1)
  expect(onMissed).toHaveBeenCalledWith(1)
  expect(submitAnswer).not.toHaveBeenCalled()
})
