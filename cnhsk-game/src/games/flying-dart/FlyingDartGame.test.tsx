import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

import type { GameProps } from '../../shared/ui/GameShell'
import { FlyingDartGame } from './FlyingDartGame'

const items: GameProps['items'] = ['人', '大'].map((text, index) => ({
  no: index + 1, type: 'SINO_VIET_INPUT', skill: 'CHARACTER', lexemeId: index + 51,
  prompt: { text, hint: index === 0 ? 'người' : 'to' },
  rules: { minDurationMs: 500, maxReplays: 0, hasAudio: false },
}))

function show() {
  const props = {
    items, disabled: false, uiConfig: { maxInputLength: 20 }, onMissed: vi.fn(), onItemShown: vi.fn(),
    submitAnswer: vi.fn<GameProps['submitAnswer']>().mockResolvedValue({ correct: true, correctAnswer: 'nhân' }),
  }
  const view = render(<FlyingDartGame {...props} />)
  return { ...view, ...props, disable: (disabled: boolean) => view.rerender(<FlyingDartGame {...props} disabled={disabled} />) }
}

const field = () => screen.getByRole('textbox', { name: 'Gõ âm Hán Việt' })
const advance = (ms: number) => act(async () => { await vi.advanceTimersByTimeAsync(ms) })
const type = (value: string) => fireEvent.change(field(), { target: { value } })
const enter = (value: string) => act(async () => {
  type(value)
  fireEvent.submit(field().closest('form')!)
})

beforeEach(() => vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] }))
afterEach(() => {
  cleanup()
  vi.useRealTimers()
})

it('throws the dart on a correct reading, breaks the character and moves to the next one', async () => {
  const { submitAnswer, onItemShown, container } = show()
  expect(field()).toHaveFocus()
  expect(field()).toHaveAttribute('maxlength', '20')
  expect(container.querySelector('.panda')).not.toBeNull()
  expect(container.querySelector('.dart-blade')).toBeNull()
  await enter(' Nhân ')
  expect(submitAnswer.mock.calls).toEqual([[1, 'Nhân']])
  expect(container.querySelector('.dart-blade')).not.toBeNull()
  expect(container.querySelectorAll('.dart-shard')).toHaveLength(4)
  expect(screen.getByText('Đã hạ chữ 人 · nhân')).toBeVisible()
  await advance(549)
  expect(onItemShown.mock.calls).toEqual([[1]])
  await advance(1)
  expect(onItemShown.mock.calls).toEqual([[1], [2]])
  expect(screen.getByText('Số chữ đã hạ: 1')).toBeVisible()
  expect(field()).toHaveValue('')
  expect(field()).toHaveFocus()
})

it('shows the right reading after a wrong one, throws nothing and moves on', async () => {
  const { submitAnswer, container } = show()
  submitAnswer.mockResolvedValueOnce({ correct: false, correctAnswer: 'nhân' })
  await enter('đại')
  expect(screen.getByText('Chưa đúng: 人 đọc là nhân')).toBeVisible()
  expect(container.querySelector('.dart-blade')).toBeNull()
  await enter('nhân')
  expect(submitAnswer).toHaveBeenCalledTimes(1)
  await advance(900)
  expect(screen.getByText('大')).toBeVisible()
  expect(screen.getByText('Số chữ đã hạ: 0')).toBeVisible()
})

it('ignores blank input, unfinished composition and typing while disabled', async () => {
  const { submitAnswer, disable } = show()
  expect(screen.getByRole('button', { name: 'Phóng dao' })).toBeDisabled()
  await enter('   ')
  fireEvent.compositionStart(field())
  await enter('nhân')
  expect(submitAnswer).not.toHaveBeenCalled()
  fireEvent.compositionEnd(field())
  disable(true)
  expect(field()).toBeDisabled()
  disable(false)
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Phóng dao' })) })
  expect(submitAnswer).toHaveBeenCalledTimes(1)
})

it('advances after a rejected answer and reports the end of the list', async () => {
  const { submitAnswer } = show()
  submitAnswer.mockRejectedValueOnce(new Error('No grade'))
  await enter('nhân')
  expect(screen.getByText('大')).toBeVisible()
  await enter('đại')
  await advance(550)
  expect(screen.getByText('Đã đi qua tất cả các chữ.')).toBeVisible()
})
