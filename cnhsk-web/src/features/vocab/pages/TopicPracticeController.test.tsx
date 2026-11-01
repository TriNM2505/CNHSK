import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  abandonSession,
  answerItem,
  findActiveSession,
  finishSession,
  getSession,
} from '@/features/practice/api'
import { SessionSummary } from '@/features/practice'
import type { AnswerResult, FinishResult, SessionView } from '@/features/practice'
import { ApiError } from '@/shared/api'

import { getTopic, startTopicPracticeSession, startTopicStudySession } from '../api/topics'
import type { TopicDetail } from '../types'
import Scr012TopicStudyPage from './Scr012TopicStudyPage'

vi.mock('../api/topics', () => ({
  getTopic: vi.fn(),
  startTopicPracticeSession: vi.fn(),
  startTopicStudySession: vi.fn(),
}))

vi.mock('@/features/practice/api', () => ({
  abandonSession: vi.fn(),
  answerItem: vi.fn(),
  findActiveSession: vi.fn(),
  finishSession: vi.fn(),
  getSession: vi.fn(),
}))

vi.mock('@/features/practice', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/practice')>()
  return { ...actual, SessionSummary: vi.fn(actual.SessionSummary) }
})

const topic: TopicDetail = {
  actions: {
    canPracticeListening: true,
    canPracticeRecognition: true,
    canStudy: true,
    canTakeTest: false,
    testBlockedReason: 'NOT_ENOUGH_LEARNED',
  },
  bestTestPercent: null,
  code: 'FAMILY',
  hskLevel: 1,
  id: 11,
  learnedWords: 4,
  name: 'Gia đình',
  percent: 40,
  status: 'IN_PROGRESS',
  totalWords: 10,
  words: [],
}

const options = [
  { key: 'A', text: 'mẹ' },
  { key: 'B', text: 'bố' },
  { key: 'C', text: 'anh trai' },
  { key: 'D', text: 'em gái' },
]

const recognitionItems: SessionView['items'] = [1, 2].map((no) => ({
  lexemeId: 100 + no,
  no,
  options,
  prompt: { text: no === 1 ? '妈妈' : '爸爸' },
  rules: { minDurationMs: 800 },
  skill: 'VOCAB',
  type: 'MEANING_CHOICE',
}))

const listeningItems: SessionView['items'] = [
  {
    lexemeId: 101,
    no: 1,
    options: [
      { key: 'A', text: '妈妈' },
      { key: 'B', text: '爸爸' },
    ],
    prompt: { audioUrl: '/audio/mama.mp3' },
    rules: { hasAudio: true, maxReplays: 3 },
    skill: 'LISTENING',
    type: 'AUDIO_WORD_CHOICE',
  },
]

function practiceSession(overrides: Partial<SessionView> = {}): SessionView {
  return {
    answered: [],
    expiresAt: '2026-10-20T12:00:00Z',
    feedback: 'IMMEDIATE',
    items: recognitionItems,
    refId: 11,
    servedAt: '2026-10-20T10:00:00Z',
    sessionId: 81,
    source: 'TOPIC_PRACTICE',
    status: 'IN_PROGRESS',
    ...overrides,
  }
}

function renderPage(entry: string | { pathname: string; state: unknown }) {
  const router = createMemoryRouter(
    [{ element: <Scr012TopicStudyPage />, path: '/learn/topics/:id/*' }],
    { initialEntries: [entry] },
  )
  render(<RouterProvider router={router} />)
  return router
}

function deferred<T>() {
  let resolveValue: (value: T) => void = () => undefined
  const promise = new Promise<T>((resolve) => {
    resolveValue = resolve
  })
  return { promise, resolve: (value: T) => resolveValue(value) }
}

function listeningPair(): SessionView['items'] {
  return [1, 2].map((no) => ({
    ...listeningItems[0],
    lexemeId: 100 + no,
    no,
    options: [
      { key: 'A', text: '妈妈' },
      { key: 'B', text: '爸爸' },
      { key: 'C', text: '哥哥' },
      { key: 'D', text: '妹妹' },
    ],
    prompt: { audioUrl: '/audio/shared.mp3' },
    rules: { hasAudio: true, maxReplays: 3, minDurationMs: 800 },
    skill: 'LISTENING',
    type: 'AUDIO_WORD_CHOICE',
  }))
}

const completedRecognition: SessionView['answered'] = [
  { answer: 'A', correct: true, correctAnswer: 'A', counted: true, no: 1 },
  { answer: 'B', correct: false, correctAnswer: 'A', counted: true, no: 2 },
]

describe('TopicPracticeController', () => {
  beforeEach(() => {
    vi.mocked(SessionSummary).mockClear()
    vi.mocked(getTopic).mockReset().mockResolvedValue(topic)
    vi.mocked(startTopicStudySession).mockReset()
    vi.mocked(startTopicPracticeSession).mockReset().mockResolvedValue(practiceSession())
    vi.mocked(findActiveSession).mockReset().mockResolvedValue(null)
    vi.mocked(getSession).mockReset()
    vi.mocked(answerItem)
      .mockReset()
      .mockImplementation(async (_id, request) => ({
        correct: false,
        correctAnswer: 'A',
        counted: true,
        explanation: '妈妈 nghĩa là mẹ.',
        no: request.no,
      }))
    vi.mocked(finishSession).mockReset().mockResolvedValue({
      items: [],
      maxScore: 2,
      percent: 50,
      score: 1,
      sessionId: 81,
      status: 'SUBMITTED',
      suspicious: false,
    })
    vi.mocked(abandonSession).mockReset().mockResolvedValue(undefined)
  })

  afterEach(() => vi.restoreAllMocks())

  it('FR-043: chấm xong hiện phản hồi, chỉ sang câu sau khi bấm "Câu tiếp", hết câu thì nộp', async () => {
    renderPage('/learn/topics/11/practice/recognition')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /mẹ/ }))

    expect(startTopicPracticeSession).toHaveBeenCalledWith(11, { mode: 'RECOGNITION' })
    expect(await screen.findByText('Chưa chính xác')).toBeVisible()
    expect(screen.getByText('妈妈 nghĩa là mẹ.')).toBeVisible()
    const request = vi.mocked(answerItem).mock.calls[0]?.[1]
    expect(request).toMatchObject({ answer: 'A', no: 1 })
    expect(typeof request?.durationMs).toBe('number')
    expect(screen.getByText('妈妈')).toBeVisible()

    await user.click(screen.getByRole('button', { name: /Câu tiếp/ }))
    expect(await screen.findByText('爸爸')).toBeVisible()

    await user.click(screen.getByRole('button', { name: /bố/ }))
    await user.click(await screen.findByRole('button', { name: /Câu tiếp/ }))

    expect(await screen.findByRole('button', { name: 'Luyện lượt mới' })).toBeVisible()
    expect(finishSession).toHaveBeenCalledTimes(1)
  })

  it('"Bỏ qua" gửi skipped', async () => {
    renderPage('/learn/topics/11/practice/recognition')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Bỏ qua' }))

    await waitFor(() =>
      expect(answerItem).toHaveBeenCalledWith(
        81,
        expect.objectContaining({ no: 1, skipped: true }),
      ),
    )
  })

  it('FR-045: tải lại trang tiếp tục lượt dở, không tạo lượt mới', async () => {
    const restored = practiceSession({
      answered: [{ answer: 'A', correct: true, correctAnswer: 'A', counted: true, no: 1 }],
    })
    vi.mocked(findActiveSession).mockResolvedValue(restored)
    vi.mocked(getSession).mockResolvedValue(restored)

    renderPage('/learn/topics/11/practice/recognition')

    expect(await screen.findByText('爸爸')).toBeVisible()
    expect(startTopicPracticeSession).not.toHaveBeenCalled()
  })

  it('lượt dở thuộc chế độ khác: không lộ câu, cho bỏ lượt rồi bắt đầu đúng chế độ', async () => {
    const recognition = practiceSession()
    vi.mocked(findActiveSession).mockResolvedValue(recognition)
    vi.mocked(getSession).mockResolvedValue(recognition)
    vi.mocked(startTopicPracticeSession).mockResolvedValue(
      practiceSession({ items: listeningItems, sessionId: 82 }),
    )
    renderPage('/learn/topics/11/practice/listening')
    const user = userEvent.setup()

    expect(await screen.findByText('Có lượt luyện đang dở')).toBeVisible()
    expect(screen.queryByText('妈妈')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Tiếp tục lượt đang dở' })).toHaveAttribute(
      'href',
      '/learn/topics/11/practice/recognition',
    )

    await user.click(screen.getByRole('button', { name: 'Bỏ lượt đó, bắt đầu lượt mới' }))
    await user.click(screen.getByRole('button', { name: 'Bỏ lượt và bắt đầu mới' }))

    await waitFor(() => expect(abandonSession).toHaveBeenCalledWith(81))
    await waitFor(() =>
      expect(startTopicPracticeSession).toHaveBeenCalledWith(11, { mode: 'LISTENING' }),
    )
    expect(await screen.findByRole('button', { name: /Nghe từ/ })).toBeVisible()
  })

  it('FR-043: nghe tối đa 3 lần và gửi replayCount khi trả lời', async () => {
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined)
    vi.mocked(startTopicPracticeSession).mockResolvedValue(
      practiceSession({ items: listeningItems }),
    )
    renderPage('/learn/topics/11/practice/listening')
    const user = userEvent.setup()

    const listen = await screen.findByRole('button', { name: /Nghe từ/ })
    await user.click(listen)
    await user.click(listen)
    await user.click(listen)

    expect(await screen.findByRole('button', { name: /Đã hết lượt nghe/ })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: /妈妈/ }))

    await waitFor(() =>
      expect(answerItem).toHaveBeenCalledWith(
        81,
        expect.objectContaining({ answer: 'A', replayCount: 3 }),
      ),
    )
  })

  it('"Không nghe được" gửi audioFailed', async () => {
    vi.mocked(startTopicPracticeSession).mockResolvedValue(
      practiceSession({ items: listeningItems }),
    )
    renderPage('/learn/topics/11/practice/listening')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /Không nghe được/ }))

    await waitFor(() =>
      expect(answerItem).toHaveBeenCalledWith(
        81,
        expect.objectContaining({ audioFailed: true, no: 1 }),
      ),
    )
  })

  it('NO_LEARNED_WORDS mời sang bước Học từ mới bằng đường dẫn tuyệt đối', async () => {
    vi.mocked(startTopicPracticeSession).mockRejectedValue(
      new ApiError({ errorCode: 'NO_LEARNED_WORDS', message: 'x', status: 422 }),
    )
    renderPage('/learn/topics/11/practice/recognition')

    expect(await screen.findByText('Bạn cần học ít nhất một từ trước khi luyện.')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Học từ mới' })).toHaveAttribute(
      'href',
      '/learn/topics/11/study',
    )
  })

  it('luyện lại từ sai dùng focusLexemeIds đúng một lần rồi xoá state', async () => {
    const router = renderPage({
      pathname: '/learn/topics/11/practice/recognition',
      state: { focusLexemeIds: [101, 102] },
    })

    await screen.findByText('妈妈')
    expect(startTopicPracticeSession).toHaveBeenCalledWith(11, {
      focusLexemeIds: [101, 102],
      mode: 'RECOGNITION',
    })
    await waitFor(() => expect(router.state.location.state).toBeNull())
  })

  it('INVALID_FOCUS_WORDS cho luyện bình thường không kèm danh sách từ', async () => {
    vi.mocked(startTopicPracticeSession)
      .mockRejectedValueOnce(
        new ApiError({ errorCode: 'INVALID_FOCUS_WORDS', message: 'x', status: 422 }),
      )
      .mockResolvedValueOnce(practiceSession())
    renderPage({
      pathname: '/learn/topics/11/practice/recognition',
      state: { focusLexemeIds: [999] },
    })
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Luyện bình thường' }))

    await waitFor(() =>
      expect(startTopicPracticeSession).toHaveBeenLastCalledWith(11, { mode: 'RECOGNITION' }),
    )
    expect(await screen.findByText('妈妈')).toBeVisible()
  })

  it('FR043_recognition waits for server feedback and blocks duplicate answers while pending', async () => {
    const response = deferred<AnswerResult>()
    vi.mocked(answerItem).mockReturnValue(response.promise)
    renderPage('/learn/topics/11/practice/recognition')
    const user = userEvent.setup()

    expect(await screen.findByText('妈妈')).toBeVisible()
    const choices = within(screen.getByRole('group', { name: 'Các phương án' }))
    expect(choices.getAllByRole('button')).toHaveLength(4)
    expect(document.querySelector('audio')).toBeNull()
    expect(screen.queryByText('Chính xác')).not.toBeInTheDocument()
    expect(screen.queryByText(/Đáp án đúng:/)).not.toBeInTheDocument()
    await user.click(choices.getByRole('button', { name: /mẹ/ }))
    await user.click(choices.getByRole('button', { name: /bố/ }))
    expect(answerItem).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('button', { name: /Câu tiếp/ })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Bỏ qua' })).toBeDisabled()

    await act(async () =>
      response.resolve({ correct: true, correctAnswer: 'A', counted: true, no: 1 }),
    )
    expect(await screen.findByText('Chính xác')).toBeVisible()
    expect(choices.getAllByRole('button').every((button) => button.hasAttribute('disabled'))).toBe(
      true,
    )
    expect(screen.getByText('妈妈')).toBeVisible()
    expect(finishSession).not.toHaveBeenCalled()
  })

  it.each([
    { audioFailed: false, mode: 'recognition', name: 'Bỏ qua' },
    { audioFailed: true, mode: 'listening', name: 'Không nghe được' },
  ])(
    'FR043_$mode uncounted action shows no grading and still allows Next',
    async ({ audioFailed, mode, name }) => {
      vi.mocked(startTopicPracticeSession).mockResolvedValue(
        practiceSession({
          items: audioFailed ? listeningPair() : recognitionItems,
        }),
      )
      vi.mocked(answerItem).mockResolvedValue({ correct: null, counted: false, no: 1 })
      renderPage(`/learn/topics/11/practice/${mode}`)
      const user = userEvent.setup()

      await user.click(await screen.findByRole('button', { name }))
      expect(await screen.findByText('Câu này không tính điểm.')).toBeVisible()
      expect(screen.queryByText('Chính xác')).not.toBeInTheDocument()
      expect(screen.queryByText('Chưa chính xác')).not.toBeInTheDocument()
      expect(answerItem).toHaveBeenCalledWith(
        81,
        expect.objectContaining({
          ...(audioFailed ? { audioFailed: true } : { skipped: true }),
          no: 1,
          replayCount: 0,
        }),
      )
      const request = vi.mocked(answerItem).mock.calls[0]?.[1]
      expect(request?.answer).toBeUndefined()
      expect(audioFailed ? request?.skipped : request?.audioFailed).toBeUndefined()
      expect(screen.queryByRole('button', { name: 'Bỏ qua' })).not.toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: /Câu tiếp/ }))
      expect(await screen.findByText('Câu 2/2')).toBeVisible()
      expect(answerItem).toHaveBeenCalledTimes(1)
    },
  )

  it('FR043_listening does not autoplay, counts the first play and blocks the fourth', async () => {
    const play = vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined)
    vi.mocked(startTopicPracticeSession).mockResolvedValue(
      practiceSession({ items: listeningPair() }),
    )
    renderPage('/learn/topics/11/practice/listening')
    const user = userEvent.setup()
    const listen = await screen.findByRole('button', { name: /Nghe từ/ })

    expect(play).not.toHaveBeenCalled()
    expect(document.querySelector('audio')).toHaveAttribute('preload', 'none')
    expect(document.querySelector('audio')).not.toHaveAttribute('autoplay')
    expect(screen.getByText('Đã nghe 0/3 lần')).toBeVisible()
    await user.click(listen)
    expect(screen.getByText('Đã nghe 1/3 lần')).toBeVisible()
    await user.click(listen)
    await user.click(listen)
    await user.click(screen.getByRole('button', { name: /Đã hết lượt nghe/ }))
    expect(play).toHaveBeenCalledTimes(3)
    await user.click(screen.getByRole('button', { name: /妈妈/ }))
    expect(answerItem).toHaveBeenCalledWith(81, expect.objectContaining({ no: 1, replayCount: 3 }))
  })

  it('FR043_resets the counter between questions with the same audio URL', async () => {
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined)
    vi.mocked(startTopicPracticeSession).mockResolvedValue(
      practiceSession({ items: listeningPair() }),
    )
    renderPage('/learn/topics/11/practice/listening')
    const user = userEvent.setup()
    const listen = await screen.findByRole('button', { name: /Nghe từ/ })
    await user.click(listen)
    await user.click(listen)
    await user.click(screen.getByRole('button', { name: /妈妈/ }))
    await user.click(await screen.findByRole('button', { name: /Câu tiếp/ }))

    expect(await screen.findByText('Đã nghe 0/3 lần')).toBeVisible()
    await user.click(screen.getByRole('button', { name: /Nghe từ/ }))
    await user.click(screen.getByRole('button', { name: /爸爸/ }))
    expect(answerItem).toHaveBeenLastCalledWith(
      81,
      expect.objectContaining({ no: 2, replayCount: 1 }),
    )
  })

  it('FR043_ignores a previous question playback promise that resolves after Next', async () => {
    const playback = deferred<void>()
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockReturnValue(playback.promise)
    vi.mocked(startTopicPracticeSession).mockResolvedValue(
      practiceSession({ items: listeningPair() }),
    )
    renderPage('/learn/topics/11/practice/listening')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /Nghe từ/ }))
    await user.click(screen.getByRole('button', { name: /妈妈/ }))
    await user.click(await screen.findByRole('button', { name: /Câu tiếp/ }))
    expect(await screen.findByText('Câu 2/2')).toBeVisible()

    await act(async () => playback.resolve(undefined))
    expect(screen.getByText('Đã nghe 0/3 lần')).toBeVisible()
    await user.click(screen.getByRole('button', { name: /爸爸/ }))
    expect(vi.mocked(answerItem).mock.lastCall?.[1].replayCount).toBe(0)
    expect(answerItem).toHaveBeenLastCalledWith(
      81,
      expect.objectContaining({ no: 2, replayCount: 0 }),
    )
  })

  it('FR043_playback failure gives a safe instruction without incrementing the counter', async () => {
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockRejectedValue(
      new Error('internal-media-error'),
    )
    vi.mocked(startTopicPracticeSession).mockResolvedValue(
      practiceSession({ items: listeningPair() }),
    )
    renderPage('/learn/topics/11/practice/listening')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /Nghe từ/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Không tải được âm thanh')
    expect(screen.queryByText('internal-media-error')).not.toBeInTheDocument()
    expect(screen.getByText('Đã nghe 0/3 lần')).toBeVisible()
    await user.click(screen.getByRole('button', { name: /Không nghe được/ }))
    expect(answerItem).toHaveBeenCalledWith(
      81,
      expect.objectContaining({ audioFailed: true, replayCount: 0 }),
    )
  })

  it('FR043_start fails once without looping and starts again only after Retry', async () => {
    vi.mocked(startTopicPracticeSession).mockRejectedValueOnce(
      new ApiError({
        errorCode: 'INTERNAL_ERROR',
        message: 'x',
        requestId: 't16-start',
        status: 500,
      }),
    )
    renderPage('/learn/topics/11/practice/recognition')
    const user = userEvent.setup()
    const retry = await screen.findByRole('button', { name: 'Thử lại' })
    expect(screen.getByText(/t16-start/)).toBeVisible()
    await act(async () => undefined)
    expect(startTopicPracticeSession).toHaveBeenCalledTimes(1)

    await user.click(retry)
    expect(await screen.findByText('妈妈')).toBeVisible()
    expect(startTopicPracticeSession).toHaveBeenCalledTimes(2)
  })

  it('FR043_answer can be retried after reloading the active session without starting another', async () => {
    vi.mocked(answerItem).mockRejectedValueOnce(
      new ApiError({ errorCode: 'INTERNAL_ERROR', message: 'x', status: 500 }),
    )
    vi.mocked(findActiveSession).mockResolvedValueOnce(null).mockResolvedValue(practiceSession())
    vi.mocked(getSession).mockResolvedValue(practiceSession())
    renderPage('/learn/topics/11/practice/recognition')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /mẹ/ }))
    await user.click(await screen.findByRole('button', { name: 'Thử lại' }))
    await user.click(await screen.findByRole('button', { name: /bố/ }))

    expect(await screen.findByText('Chưa chính xác')).toBeVisible()
    expect(answerItem).toHaveBeenCalledTimes(2)
    expect(answerItem).toHaveBeenLastCalledWith(81, expect.objectContaining({ answer: 'B', no: 1 }))
    expect(startTopicPracticeSession).toHaveBeenCalledTimes(1)
  })

  it('FR043_normal completion waits for Next and retries a failed finish only after a click', async () => {
    const single = practiceSession({ items: recognitionItems.slice(0, 1) })
    vi.mocked(startTopicPracticeSession).mockResolvedValue(single)
    vi.mocked(findActiveSession).mockResolvedValueOnce(null).mockResolvedValue(single)
    vi.mocked(getSession).mockResolvedValue({
      ...single,
      answered: completedRecognition.slice(0, 1),
    })
    vi.mocked(finishSession).mockRejectedValueOnce(
      new ApiError({ errorCode: 'INTERNAL_ERROR', message: 'x', status: 500 }),
    )
    renderPage('/learn/topics/11/practice/recognition')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /mẹ/ }))
    await screen.findByText('Chưa chính xác')
    expect(finishSession).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: /Câu tiếp/ }))
    const retry = await screen.findByRole('button', { name: 'Thử lại' })
    await act(async () => undefined)
    expect(finishSession).toHaveBeenCalledTimes(1)

    await user.click(retry)
    expect(await screen.findByText('Kết quả lượt luyện')).toBeVisible()
    expect(finishSession).toHaveBeenCalledTimes(2)
    expect(answerItem).toHaveBeenCalledTimes(1)
  })

  it('FR045_finishes a restored fully answered IN_PROGRESS session once and shows progress', async () => {
    const restored = practiceSession({ answered: completedRecognition })
    const finish = deferred<FinishResult>()
    vi.mocked(findActiveSession).mockResolvedValue(restored)
    vi.mocked(getSession).mockResolvedValue(restored)
    vi.mocked(finishSession).mockReturnValue(finish.promise)
    renderPage('/learn/topics/11/practice/recognition')

    await waitFor(() => expect(finishSession).toHaveBeenCalledTimes(1))
    expect(screen.getByText('Đang hoàn tất lượt luyện')).toBeVisible()
    expect(startTopicPracticeSession).not.toHaveBeenCalled()
    expect(answerItem).not.toHaveBeenCalled()
    await act(async () =>
      finish.resolve({
        items: [],
        maxScore: 2,
        score: 1,
        sessionId: 81,
        status: 'SUBMITTED',
        suspicious: false,
      }),
    )
    expect(await screen.findByText('1/2')).toBeVisible()
    expect(finishSession).toHaveBeenCalledTimes(1)
    expect(finishSession).toHaveBeenCalledWith(81, undefined)
  })

  it('FR045_does not repeat a failed automatic finish until Retry is clicked', async () => {
    const restored = practiceSession({ answered: completedRecognition })
    vi.mocked(findActiveSession).mockResolvedValue(restored)
    vi.mocked(getSession).mockResolvedValue(restored)
    vi.mocked(finishSession).mockRejectedValueOnce(
      new ApiError({ errorCode: 'INTERNAL_ERROR', message: 'x', status: 500 }),
    )
    renderPage('/learn/topics/11/practice/recognition')
    const user = userEvent.setup()
    const retry = await screen.findByRole('button', { name: 'Thử lại' })
    await act(async () => undefined)
    expect(finishSession).toHaveBeenCalledTimes(1)
    await user.click(retry)
    expect(await screen.findByText('Kết quả lượt luyện')).toBeVisible()
    expect(finishSession).toHaveBeenCalledTimes(2)
    expect(answerItem).not.toHaveBeenCalled()
    expect(startTopicPracticeSession).not.toHaveBeenCalled()
  })

  it('FR045_restores the next listening question with zero plays and no extra start', async () => {
    const restored = practiceSession({
      answered: completedRecognition.slice(0, 1),
      items: listeningPair(),
    })
    vi.mocked(findActiveSession).mockResolvedValue(restored)
    vi.mocked(getSession).mockResolvedValue(restored)
    renderPage('/learn/topics/11/practice/listening')
    const user = userEvent.setup()
    expect(await screen.findByText('Câu 2/2')).toBeVisible()
    expect(screen.getByText('Đã nghe 0/3 lần')).toBeVisible()
    expect(findActiveSession).toHaveBeenCalledWith('TOPIC_PRACTICE', 11, expect.any(AbortSignal))
    expect(getSession).toHaveBeenCalledWith(81, expect.any(AbortSignal))
    await user.click(screen.getByRole('button', { name: /爸爸/ }))
    expect(answerItem).toHaveBeenCalledWith(81, expect.objectContaining({ no: 2, replayCount: 0 }))
    expect(startTopicPracticeSession).not.toHaveBeenCalled()
  })

  it.each([
    { from: 'recognition', to: 'listening' },
    { from: 'listening', to: 'recognition' },
  ])(
    'FR045_switching $from to $to refreshes active session and drops old feedback',
    async ({ from, to }) => {
      vi.mocked(startTopicPracticeSession).mockResolvedValue(
        practiceSession({
          items: from === 'listening' ? listeningPair() : recognitionItems,
        }),
      )
      const restored = practiceSession({
        items: to === 'listening' ? listeningPair() : recognitionItems,
        sessionId: 82,
      })
      vi.mocked(findActiveSession).mockResolvedValueOnce(null).mockResolvedValue(restored)
      vi.mocked(getSession).mockResolvedValue(restored)
      const router = renderPage(`/learn/topics/11/practice/${from}`)
      const user = userEvent.setup()
      await user.click(
        await screen.findByRole('button', { name: from === 'listening' ? /妈妈/ : /mẹ/ }),
      )
      await screen.findByText('Chưa chính xác')

      await act(async () => {
        await router.navigate(`/learn/topics/11/practice/${to}`)
      })
      await waitFor(() => expect(findActiveSession).toHaveBeenCalledTimes(2))
      if (to === 'listening') expect(await screen.findByText('Đã nghe 0/3 lần')).toBeVisible()
      else expect(await screen.findByText('妈妈')).toBeVisible()
      expect(screen.queryByText('Chưa chính xác')).not.toBeInTheDocument()
      expect(startTopicPracticeSession).toHaveBeenCalledTimes(1)
      expect(screen.queryByRole('button', { name: /Câu tiếp/ })).not.toBeInTheDocument()
    },
  )

  it('FR045_switching modes after start failure resets the start flag for the new mode', async () => {
    vi.mocked(startTopicPracticeSession)
      .mockRejectedValueOnce(
        new ApiError({ errorCode: 'INTERNAL_ERROR', message: 'x', status: 500 }),
      )
      .mockResolvedValueOnce(practiceSession({ items: listeningPair(), sessionId: 82 }))
    const router = renderPage('/learn/topics/11/practice/recognition')
    await screen.findByRole('button', { name: 'Thử lại' })
    await act(async () => {
      await router.navigate('/learn/topics/11/practice/listening')
    })

    expect(await screen.findByRole('button', { name: /Nghe từ/ })).toBeVisible()
    expect(startTopicPracticeSession).toHaveBeenLastCalledWith(11, { mode: 'LISTENING' })
    expect(startTopicPracticeSession).toHaveBeenCalledTimes(2)
  })

  it('FR045_ignores late focus start navigation and answers after changing topic', async () => {
    const start = deferred<SessionView>()
    vi.mocked(getTopic).mockImplementation(async (id) => ({ ...topic, id }))
    vi.mocked(startTopicPracticeSession)
      .mockReturnValueOnce(start.promise)
      .mockResolvedValueOnce(practiceSession({ refId: 12, sessionId: 82 }))
    const router = renderPage({
      pathname: '/learn/topics/11/practice/recognition',
      state: { focusLexemeIds: [101] },
    })
    await waitFor(() => expect(startTopicPracticeSession).toHaveBeenCalledTimes(1))
    await act(async () => {
      await router.navigate('/learn/topics/12/practice/recognition', {
        state: { marker: 'new-topic' },
      })
    })
    expect(await screen.findByText('妈妈')).toBeVisible()
    await act(async () => start.resolve(practiceSession()))

    expect(router.state.location.pathname).toBe('/learn/topics/12/practice/recognition')
    expect(router.state.location.state).toEqual({ marker: 'new-topic' })
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /mẹ/ }))
    expect(answerItem).toHaveBeenLastCalledWith(
      82,
      expect.objectContaining({ no: 1, replayCount: 0 }),
    )
  })

  it('FR043_a new session resets question duration even when the first number is unchanged', async () => {
    let now = 1000
    vi.spyOn(Date, 'now').mockImplementation(() => now)
    const single = practiceSession({ items: recognitionItems.slice(0, 1) })
    vi.mocked(startTopicPracticeSession)
      .mockResolvedValueOnce(single)
      .mockResolvedValueOnce({ ...single, sessionId: 82 })
    renderPage('/learn/topics/11/practice/recognition')
    const user = userEvent.setup()
    await screen.findByText('妈妈')
    now = 2000
    await user.click(screen.getByRole('button', { name: /mẹ/ }))
    await user.click(await screen.findByRole('button', { name: /Câu tiếp/ }))
    now = 10_000
    await user.click(await screen.findByRole('button', { name: 'Luyện lượt mới' }))
    await screen.findByText('妈妈')
    now = 10_800
    await user.click(screen.getByRole('button', { name: /mẹ/ }))

    expect(vi.mocked(answerItem).mock.lastCall?.[1].durationMs).toBe(800)
    expect(answerItem).toHaveBeenLastCalledWith(
      82,
      expect.objectContaining({ no: 1, durationMs: 800, replayCount: 0 }),
    )
  })

  it.each([
    {
      label: 'mixed graded and uncounted answers',
      score: 2,
      maxScore: 3,
      percent: 66,
      uncounted: false,
    },
    { label: 'all uncounted answers', score: 0, maxScore: 0, percent: null, uncounted: true },
  ])(
    'FR045_rebuilds a SUBMITTED summary from $label',
    async ({ score, maxScore, percent, uncounted }) => {
      const items = recognitionItems
        .slice(0, 1)
        .flatMap((item) => [1, 2, 3, 4].map((no) => ({ ...item, no })))
      const answered: SessionView['answered'] = uncounted
        ? items.map(({ no }) => ({ correct: null, counted: false, no }))
        : [
            { answer: 'A', correct: true, counted: true, no: 1 },
            { answer: 'A', correct: true, counted: true, no: 2 },
            { answer: 'B', correct: false, correctAnswer: 'A', counted: true, no: 3 },
            { correct: null, counted: false, no: 4 },
          ]
      vi.mocked(startTopicPracticeSession).mockResolvedValue(practiceSession({ items }))
      vi.mocked(answerItem).mockRejectedValueOnce(
        new ApiError({ errorCode: 'SESSION_ALREADY_SUBMITTED', message: 'x', status: 409 }),
      )
      vi.mocked(getSession).mockResolvedValue(
        practiceSession({ answered, items, status: 'SUBMITTED' }),
      )
      renderPage('/learn/topics/11/practice/recognition')
      const user = userEvent.setup()
      await user.click(await screen.findByRole('button', { name: /mẹ/ }))

      expect(await screen.findByText(`${score}/${maxScore}`)).toBeVisible()
      expect(vi.mocked(SessionSummary).mock.lastCall?.[0].result).toMatchObject({
        extra: {},
        maxScore,
        percent,
        score,
        sessionId: 81,
        status: 'SUBMITTED',
      })
      expect(screen.queryByText('Không chấm điểm')).not.toBeInTheDocument()
      expect(finishSession).not.toHaveBeenCalled()
    },
  )

  it('FR043_after a rebuilt summary the new session uses the real finish response unchanged', async () => {
    vi.mocked(answerItem)
      .mockRejectedValueOnce(
        new ApiError({ errorCode: 'SESSION_ALREADY_SUBMITTED', message: 'x', status: 409 }),
      )
      .mockImplementation(async (_id, request) => ({
        correct: true,
        correctAnswer: 'A',
        counted: true,
        no: request.no,
      }))
    vi.mocked(getSession).mockResolvedValue(
      practiceSession({
        answered: completedRecognition,
        status: 'SUBMITTED',
      }),
    )
    vi.mocked(startTopicPracticeSession)
      .mockResolvedValueOnce(practiceSession())
      .mockResolvedValueOnce(practiceSession({ sessionId: 82 }))
    const realFinish: FinishResult = {
      extra: { serverMarker: true },
      items: [1, 2].map((no) => ({ correct: true, correctAnswer: 'A', no, yourAnswer: 'A' })),
      maxScore: 2,
      percent: 100,
      score: 2,
      sessionId: 82,
      status: 'SUBMITTED',
      suspicious: false,
    }
    vi.mocked(finishSession).mockResolvedValue(realFinish)
    renderPage('/learn/topics/11/practice/recognition')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /mẹ/ }))
    await user.click(await screen.findByRole('button', { name: 'Luyện lượt mới' }))
    await user.click(await screen.findByRole('button', { name: /mẹ/ }))
    await user.click(await screen.findByRole('button', { name: /Câu tiếp/ }))
    await user.click(await screen.findByRole('button', { name: /mẹ/ }))
    await user.click(await screen.findByRole('button', { name: /Câu tiếp/ }))

    expect(await screen.findByText('2/2')).toBeVisible()
    expect(vi.mocked(SessionSummary).mock.lastCall?.[0].result).toBe(realFinish)
    expect(finishSession).toHaveBeenCalledTimes(1)
  })
})
