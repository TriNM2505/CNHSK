import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

import type { ServedItem } from '../../shared/api/rounds'
import { GameShell } from '../../shared/ui/GameShell'
import type { GameAnswer, GameProps } from '../../shared/ui/GameShell'
import { CharCatchGame } from './CharCatchGame'

const options = ['山', '水', '林', '田', '口', '日'].map((text, index) => ({
  key: String.fromCharCode(65 + index), text,
}))
const audioUrl = '/sample.ogg'
const meaning: ServedItem = {
  no: 1, type: 'CATCH_CHOICE', skill: 'LISTENING', options,
  prompt: { hint: 'núi' },
  rules: { minDurationMs: 300, maxReplays: 0, hasAudio: false },
}
const pinyin: ServedItem = { ...meaning, no: 2, prompt: { pinyin: 'shuǐ' } }
const audioItem: ServedItem = {
  ...meaning, no: 3, prompt: { audioUrl },
  rules: { ...meaning.rules, maxReplays: 1, hasAudio: true },
}
const uiConfig = { gridColumns: 3, perItemMs: 4000 }

function show(items = [meaning, pinyin]) {
  const submitAnswer = vi.fn<GameProps['submitAnswer']>()
    .mockResolvedValue({ correct: true, correctAnswer: 'A' })
  const props = { items, uiConfig, submitAnswer, onMissed: vi.fn(), onItemShown: vi.fn(), disabled: false }
  const view = render(<CharCatchGame {...props} />)
  return {
    ...view, ...props,
    setDisabled(disabled: boolean) { view.rerender(<CharCatchGame {...props} disabled={disabled} />) },
  }
}

const button = (name: string) => screen.getByRole('button', { name })
const advance = (ms: number) => act(async () => { await vi.advanceTimersByTimeAsync(ms) })
const click = (name: string) => act(async () => { fireEvent.click(button(name)) })
const media = (container: HTMLElement) => container.querySelector('audio')!
const canPlay = (element: HTMLAudioElement) => act(async () => { fireEvent.canPlay(element) })
const timeLeft = () => (screen.getByRole('progressbar') as HTMLProgressElement).value

beforeEach(() => vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval', 'Date', 'performance'] }))
afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/'
})

it('AT-19 shows six options and misses exactly once at 4000 ms', async () => {
  const { onMissed, submitAnswer, onItemShown } = show()
  const grid = screen.getByRole('group', { name: 'Phương án' })
  expect(within(grid).getAllByRole('button')).toHaveLength(6)
  expect(grid).toHaveStyle({ gridTemplateColumns: 'repeat(3, minmax(0, 1fr))' })
  expect(screen.getByRole('heading', { name: 'núi' })).toBeVisible()
  expect(onItemShown.mock.calls).toEqual([[1]])
  await advance(3999)
  expect(onMissed).not.toHaveBeenCalled()
  await advance(1)
  expect(screen.getByRole('heading', { name: 'shuǐ' })).toBeVisible()
  expect(onMissed.mock.calls).toEqual([[1]])
  expect(submitAnswer).not.toHaveBeenCalled()
})

it('pauses time while disabled and announces the same item only once', async () => {
  const { onMissed, onItemShown, setDisabled } = show()
  await advance(1000)
  setDisabled(true)
  expect(button('山')).toBeDisabled()
  expect(timeLeft()).toBe(3000)
  await advance(9000)
  expect(timeLeft()).toBe(3000)
  expect(onMissed).not.toHaveBeenCalled()
  setDisabled(false)
  await advance(2999)
  expect(onMissed).not.toHaveBeenCalled()
  await advance(1)
  expect(onMissed.mock.calls).toEqual([[1]])
  expect(onItemShown.mock.calls).toEqual([[1], [2]])
})

it('AT-19 locks immediately, shows grading for 300 ms, then uses server feedback for 500 ms', async () => {
  const { submitAnswer, onMissed } = show()
  let respond!: (result: GameAnswer) => void
  submitAnswer.mockReturnValueOnce(new Promise((resolve) => { respond = resolve }))
  await advance(3999)
  await click('水')
  await click('山')
  expect(submitAnswer.mock.calls).toEqual([[1, 'B']])
  expect(button('山')).toBeDisabled()
  expect(screen.getByText('Đang chấm…')).toBeVisible()
  await advance(299)
  expect(screen.getByText('Đang chấm…')).toBeVisible()
  await advance(1)
  expect(screen.getByText('Đã gửi câu trả lời.')).toBeVisible()
  expect(onMissed).not.toHaveBeenCalled()
  await act(async () => respond({ correct: false, correctAnswer: 'A' }))
  expect(button('水')).toHaveClass('answer-wrong')
  expect(button('山')).toHaveClass('answer-correct')
  await advance(499)
  expect(screen.getByText('Chưa đúng')).toBeVisible()
  await advance(1)
  expect(screen.getByRole('heading', { name: 'shuǐ' })).toBeVisible()
})

it('shows a correct answer and advances after a rejection on the next item', async () => {
  const { submitAnswer, onMissed } = show()
  await click('山')
  expect(button('山')).toHaveClass('answer-correct')
  expect(screen.getByText('Chính xác')).toBeVisible()
  await advance(500)
  submitAnswer.mockRejectedValueOnce(new Error('No graded answer'))
  await click('水')
  expect(screen.getByText('Đã đi qua tất cả các chữ.')).toBeVisible()
  expect(onMissed).not.toHaveBeenCalled()
})

it('starts audio and the timer at readiness, pauses on disable and caps replay', async () => {
  let playbackStarted!: () => void
  const play = vi.spyOn(HTMLMediaElement.prototype, 'play')
    .mockReturnValueOnce(new Promise((resolve) => { playbackStarted = resolve }))
    .mockResolvedValue()
  const pause = vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
  const { container, onMissed, onItemShown, setDisabled, unmount } = show([audioItem])
  expect(play).toHaveBeenCalledTimes(1)
  await advance(2000)
  expect(timeLeft()).toBe(4000)
  expect(onItemShown).not.toHaveBeenCalled()
  await canPlay(media(container))
  await act(async () => playbackStarted())
  expect(onItemShown.mock.calls).toEqual([[3]])
  expect(play).toHaveBeenCalledTimes(1)
  await advance(1000)
  setDisabled(true)
  expect(pause).toHaveBeenCalledTimes(1)
  expect(button('Nghe lại')).toBeDisabled()
  expect(timeLeft()).toBe(3000)
  await advance(5000)
  expect(onMissed).not.toHaveBeenCalled()
  setDisabled(false)
  expect(play).toHaveBeenCalledTimes(1)
  await click('Nghe lại')
  fireEvent.ended(media(container))
  expect(play).toHaveBeenCalledTimes(2)
  expect(button('Nghe lại')).toBeDisabled()
  unmount()
  expect(pause).toHaveBeenCalledTimes(2)
  await advance(4000)
  expect(onMissed).not.toHaveBeenCalled()
})

it('offers Chạm để nghe before canplay without using the replay allowance', async () => {
  const play = vi.spyOn(HTMLMediaElement.prototype, 'play')
    .mockRejectedValueOnce(new DOMException('Autoplay denied', 'NotAllowedError'))
    .mockResolvedValue()
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
  const { container, onItemShown, onMissed } = show([audioItem])
  await advance(0)
  expect(onItemShown.mock.calls).toEqual([[3]])
  expect(onMissed).not.toHaveBeenCalled()
  expect(button('Chạm để nghe')).toBeEnabled()
  await advance(250)
  expect(timeLeft()).toBe(3750)
  await click('Chạm để nghe')
  fireEvent.ended(media(container))
  expect(button('Nghe lại')).toBeEnabled()
  await click('Nghe lại')
  fireEvent.ended(media(container))
  expect(button('Nghe lại')).toBeDisabled()
  expect(play).toHaveBeenCalledTimes(3)
  expect(onMissed).not.toHaveBeenCalled()
})

it('starts the answer timer when play resolves without canplay', async () => {
  let playbackStarted!: () => void
  vi.spyOn(HTMLMediaElement.prototype, 'play')
    .mockReturnValueOnce(new Promise((resolve) => { playbackStarted = resolve }))
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
  const { onItemShown, onMissed } = show([audioItem])
  await advance(2500)
  expect(onItemShown).not.toHaveBeenCalled()
  await act(async () => playbackStarted())
  expect(onItemShown.mock.calls).toEqual([[3]])
  await advance(3999)
  expect(onMissed).not.toHaveBeenCalled()
  await advance(1)
  expect(onMissed.mock.calls).toEqual([[3]])
})

it('pauses the loading limit while disabled and keeps the remaining time on re-enable', async () => {
  let interrupted!: (error: DOMException) => void
  const play = vi.spyOn(HTMLMediaElement.prototype, 'play')
    .mockReturnValueOnce(new Promise((_resolve, reject) => { interrupted = reject }))
    .mockImplementation(() => new Promise(() => {}))
  const pause = vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
  const { onItemShown, onMissed, submitAnswer, setDisabled } = show([audioItem])
  await advance(1250)
  setDisabled(true)
  expect(pause).toHaveBeenCalledTimes(1)
  await act(async () => interrupted(new DOMException('Paused', 'AbortError')))
  await advance(6000)
  expect(play).toHaveBeenCalledTimes(1)
  expect(onMissed).not.toHaveBeenCalled()
  setDisabled(false)
  expect(play).toHaveBeenCalledTimes(2)
  await advance(2749)
  expect(onMissed).not.toHaveBeenCalled()
  await advance(1)
  expect(onMissed.mock.calls).toEqual([[3]])
  expect(onItemShown).not.toHaveBeenCalled()
  expect(submitAnswer).not.toHaveBeenCalled()
})

it.each(['load', 'play'])('E5 misses once on %s failure without submitting', async (failure) => {
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockRejectedValue(new DOMException('Decode failure', 'NotSupportedError'))
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
  const { container, onMissed, submitAnswer } = show([audioItem, meaning])
  const element = media(container)
  if (failure === 'load') fireEvent.error(element)
  else await advance(0)
  fireEvent.error(element)
  expect(screen.getByRole('heading', { name: 'núi' })).toBeVisible()
  expect(onMissed.mock.calls).toEqual([[3]])
  expect(submitAnswer).not.toHaveBeenCalled()
})

it('starts audio after the shell countdown and misses a hung load at 4000 ms without a POST to answers', async () => {
  const play = vi.spyOn(HTMLMediaElement.prototype, 'play').mockImplementation(() => new Promise(() => {}))
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
  vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080')
  document.cookie = 'XSRF-TOKEN=fixture; Path=/'
  const fetchMock = vi.fn<typeof fetch>().mockImplementation(async (input) => {
    const url = String(input)
    if (url.endsWith('/games/CHAR_CATCH/rounds')) {
      const startedAt = new Date().toISOString()
      return Response.json({
        roundId: 40, gameCode: 'CHAR_CATCH', serverTime: startedAt, startedAt,
        deadline: new Date(Date.now() + 75000).toISOString(),
        timeLimitSeconds: 75, pointsPerCorrect: 10, uiConfig, items: [audioItem],
      }, { status: 201 })
    }
    if (url.endsWith('/finish')) {
      return Response.json({ roundId: 40, points: 0, maxPoints: 10, correct: 0, total: 1, bestPoints: 0, newBest: false })
    }
    throw new Error('Unexpected request ' + url)
  })
  vi.stubGlobal('fetch', fetchMock)
  const onMissed = vi.fn()
  function TestGame(props: GameProps) {
    return <CharCatchGame {...props} onMissed={(no) => {
      onMissed(no)
      props.onMissed(no)
    }} />
  }
  const { container } = render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <GameShell code="CHAR_CATCH" Game={TestGame} />
    </MemoryRouter>,
  )
  await advance(5000)
  expect(media(container)).toBeNull()
  expect(play).not.toHaveBeenCalled()
  await click('Bắt đầu')
  await advance(2999)
  expect(media(container)).toBeNull()
  await advance(1)
  expect(play).toHaveBeenCalledTimes(1)
  await advance(3999)
  expect(screen.getByText('Đang tải âm thanh…')).toBeVisible()
  expect(onMissed).not.toHaveBeenCalled()
  await advance(1)
  expect(screen.getByText('Đã đi qua tất cả các chữ.')).toBeVisible()
  await advance(800)
  expect(onMissed.mock.calls).toEqual([[3]])
  const urls = fetchMock.mock.calls.map(([url]) => String(url))
  expect(urls.filter((url) => url.endsWith('/finish'))).toHaveLength(1)
  expect(urls.some((url) => url.includes('/answers'))).toBe(false)
})
