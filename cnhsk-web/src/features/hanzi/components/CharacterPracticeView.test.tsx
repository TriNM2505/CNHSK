import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { CharacterDetail, WritingMode } from '../types'
import { CharacterPracticeView } from './CharacterPracticeView'

const canvasMock = vi.hoisted(() => ({
  animate: vi.fn(),
  cancelAnimations: vi.fn(),
  cancelQuiz: vi.fn(),
  dataError: undefined as (() => void) | undefined,
  onReady: undefined as (() => void) | undefined,
  quiz: vi.fn(),
  setVisibility: vi.fn(),
}))

vi.mock('./HanziCanvas', async () => {
  const React = await import('react')
  const MockCanvas = React.forwardRef(function MockCanvas(
    props: { character: string; onDataError?: () => void; onReady?: () => void },
    ref,
  ) {
    React.useImperativeHandle(ref, () => ({
      animateCharacter: canvasMock.animate,
      cancelAnimations: canvasMock.cancelAnimations,
      cancelQuiz: canvasMock.cancelQuiz,
      quiz: canvasMock.quiz,
      setVisibility: canvasMock.setVisibility,
    }))
    React.useEffect(() => {
      canvasMock.onReady = props.onReady
      canvasMock.dataError = props.onDataError
    }, [props.character, props.onDataError, props.onReady])
    return React.createElement('div', { 'data-testid': 'mock-hanzi-canvas' })
  })
  return { HanziCanvas: MockCanvas }
})

const detail: CharacterDetail = {
  audioUrl: '/audio/hao.mp3',
  hasAudio: true,
  hskLevel: 1,
  id: 11,
  meaning: 'tốt; hay',
  pinyin: 'hǎo',
  radical: '女',
  sinoViet: 'hảo',
  strokeCount: 2,
  text: '好',
  writing: { dictationAvailable: true, recallUnlocked: true, strokeAvailable: true },
}

async function renderPractice(mode: WritingMode, onModeChange = vi.fn(), onSubmitAttempt = vi.fn()) {
  const result = render(
    <MemoryRouter>
      <CharacterPracticeView
        attemptState="idle"
        character={detail}
        mode={mode}
        onModeChange={onModeChange}
        onRetry={vi.fn()}
        onSubmitAttempt={onSubmitAttempt}
        state="ready"
      />
    </MemoryRouter>,
  )
  act(() => canvasMock.onReady?.())
  return { ...result, onModeChange, onSubmitAttempt }
}

beforeEach(() => {
  vi.clearAllMocks()
  canvasMock.dataError = undefined
  canvasMock.onReady = undefined
})

afterEach(() => vi.useRealTimers())

describe('CharacterPracticeView — writer integration', () => {
  it('FR031_startsStrokeQuizWithOutlineAndHintAfterOneMiss', async () => {
    await renderPractice('STROKE')

    await waitFor(() => expect(canvasMock.quiz).toHaveBeenCalledOnce())
    expect(canvasMock.setVisibility).toHaveBeenCalledWith(false, true)
    expect(canvasMock.quiz.mock.calls[0]?.[0]).toMatchObject({
      quizStartStrokeNum: 0,
      showHintAfterMisses: 1,
    })
  })

  it('FR031_resumesQuizAtTheCurrentStrokeAfterAnimationAndKeepsCumulativeMistakes', async () => {
    const user = userEvent.setup()
    const { onSubmitAttempt } = await renderPractice('STROKE')
    await waitFor(() => expect(canvasMock.quiz).toHaveBeenCalledOnce())
    const firstQuiz = canvasMock.quiz.mock.calls[0]![0]
    act(() => firstQuiz.onCorrectStroke({ strokeNum: 1, totalMistakes: 2 }))

    await user.click(screen.getByRole('button', { name: 'Xem lại nét' }))
    expect(canvasMock.cancelQuiz).toHaveBeenCalled()
    const animationComplete = canvasMock.animate.mock.calls[0]![0] as (canceled: boolean) => void
    act(() => animationComplete(false))

    await waitFor(() => expect(canvasMock.quiz).toHaveBeenCalledTimes(2))
    const resumedQuiz = canvasMock.quiz.mock.calls[1]![0]
    expect(resumedQuiz).toMatchObject({ quizStartStrokeNum: 2, showHintAfterMisses: 1 })
    act(() => resumedQuiz.onMistake({ strokeNum: 2, totalMistakes: 3 }))
    act(() => resumedQuiz.onComplete({ character: '好', totalMistakes: 3 }))

    await waitFor(() => expect(onSubmitAttempt).toHaveBeenCalledOnce())
    expect(onSubmitAttempt).toHaveBeenCalledWith(expect.objectContaining({
      accuracy: 0,
      completed: true,
      strokeMisses: 5,
      usedAnimation: true,
    }))
  })

  it('FR031_clampsStrokeMissesToTheServerMaximumButKeepsTrueAccuracy', async () => {
    const { onSubmitAttempt } = await renderPractice('STROKE')
    await waitFor(() => expect(canvasMock.quiz).toHaveBeenCalledOnce())
    const quiz = canvasMock.quiz.mock.calls[0]![0]
    act(() => quiz.onComplete({ character: '好', totalMistakes: 25 }))

    await waitFor(() => expect(onSubmitAttempt).toHaveBeenCalledOnce())
    expect(onSubmitAttempt).toHaveBeenCalledWith(expect.objectContaining({ accuracy: 0, strokeMisses: 20 }))
  })

  it('FR031_unmountDuringAnimationDoesNotStartAnotherQuiz', async () => {
    const user = userEvent.setup()
    const view = await renderPractice('STROKE')
    await waitFor(() => expect(canvasMock.quiz).toHaveBeenCalledOnce())
    await user.click(screen.getByRole('button', { name: 'Xem lại nét' }))
    const animationComplete = canvasMock.animate.mock.calls[0]![0] as (canceled: boolean) => void
    view.unmount()
    act(() => animationComplete(false))
    expect(canvasMock.quiz).toHaveBeenCalledOnce()
  })

  it('FR031_switchingModeDuringAnimationDoesNotResumeTheOldQuiz', async () => {
    const user = userEvent.setup()
    const { rerender } = await renderPractice('STROKE')
    await waitFor(() => expect(canvasMock.quiz).toHaveBeenCalledOnce())
    await user.click(screen.getByRole('button', { name: 'Xem lại nét' }))
    const animationComplete = canvasMock.animate.mock.calls[0]![0] as (canceled: boolean) => void

    rerender(
      <MemoryRouter>
        <CharacterPracticeView
          attemptState="idle"
          character={detail}
          mode="DICTATION"
          onModeChange={vi.fn()}
          onRetry={vi.fn()}
          onSubmitAttempt={vi.fn()}
          state="ready"
        />
      </MemoryRouter>,
    )
    await waitFor(() => expect(canvasMock.quiz).toHaveBeenCalledTimes(2))
    act(() => animationComplete(false))
    expect(canvasMock.quiz).toHaveBeenCalledTimes(2)
  })

  it.each([
    [0, 5000],
    [1, 8000],
    [2, 11000],
  ])('FR032_submitsFixedPreviewDurationFor_%s_extra_previews', async (extraCount, expectedPreviewMs) => {
    vi.useFakeTimers()
    const { onSubmitAttempt } = await renderPractice('RECALL')
    await act(async () => { await vi.advanceTimersByTimeAsync(5000) })
    expect(canvasMock.quiz).toHaveBeenCalledOnce()

    for (let index = 0; index < extraCount; index += 1) {
      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: /Xem mẫu thêm/ }))
        await vi.advanceTimersByTimeAsync(3000)
      })
    }
    const quiz = canvasMock.quiz.mock.calls.at(-1)![0]
    act(() => quiz.onComplete({ character: '好', totalMistakes: 0 }))
    expect(onSubmitAttempt).toHaveBeenCalledWith(expect.objectContaining({
      mode: 'RECALL',
      previewCount: extraCount,
      previewMs: expectedPreviewMs,
    }))
    vi.useRealTimers()
  })

  it('FR032_sayingNotRememberedReturnsToStrokeWithoutPostingAnAttempt', async () => {
    vi.useFakeTimers()
    const onModeChange = vi.fn()
    const onSubmitAttempt = vi.fn()
    const view = await renderPractice('RECALL', onModeChange, onSubmitAttempt)

    await act(async () => { await vi.advanceTimersByTimeAsync(5000) })
    fireEvent.click(screen.getByRole('button', { name: /Chưa nhớ — về viết theo nét/ }))
    expect(onModeChange).toHaveBeenCalledWith('STROKE')
    expect(onSubmitAttempt).not.toHaveBeenCalled()

    view.unmount()
    vi.useRealTimers()
  })

  it('FR032_stopButtonSubmitsAnIncompleteAttemptButLeavingDoesNot', async () => {
    const user = userEvent.setup()
    const view = await renderPractice('STROKE')
    await waitFor(() => expect(canvasMock.quiz).toHaveBeenCalledOnce())
    await user.click(screen.getByRole('button', { name: 'Dừng' }))
    expect(view.onSubmitAttempt).toHaveBeenCalledWith(expect.objectContaining({ completed: false }))
    expect(view.onSubmitAttempt).toHaveBeenCalledOnce()
  })

  it('FR033_audioFailedSwitchesToStrokeWithoutSubmittingDictation', async () => {
    const user = userEvent.setup()
    const { onModeChange, onSubmitAttempt } = await renderPractice('DICTATION')
    await waitFor(() => expect(canvasMock.quiz).toHaveBeenCalledOnce())
    await user.click(screen.getByRole('button', { name: 'Không nghe được' }))
    expect(onModeChange).toHaveBeenCalledWith('STROKE')
    expect(onSubmitAttempt).not.toHaveBeenCalled()
  })

  it('FR030_dataErrorStopsTheQuizAndShowsSafeCharacterViewWithoutPosting', async () => {
    await renderPractice('STROKE')
    await waitFor(() => expect(canvasMock.quiz).toHaveBeenCalledOnce())
    act(() => canvasMock.dataError?.())
    expect(await screen.findByRole('alert')).toHaveTextContent('Chữ này chưa có dữ liệu nét')
    expect(screen.queryByTestId('mock-hanzi-canvas')).not.toBeInTheDocument()
    expect(screen.getAllByText('好').length).toBeGreaterThan(0)
  })
})
