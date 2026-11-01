import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
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
import type { AnswerResult, FinishResult, SessionView } from '@/features/practice'
import { ApiError } from '@/shared/api'

import {
  getTopic,
  startTopicPracticeSession,
  startTopicStudySession,
  startTopicTest,
} from '../api/topics'
import type { TopicDetail } from '../types'
import Scr012TopicStudyPage from './Scr012TopicStudyPage'

vi.mock('../api/topics', () => ({
  getTopic: vi.fn(),
  startTopicPracticeSession: vi.fn(),
  startTopicStudySession: vi.fn(),
  startTopicTest: vi.fn(),
}))

vi.mock('@/features/practice/api', () => ({
  abandonSession: vi.fn(),
  answerItem: vi.fn(),
  findActiveSession: vi.fn(),
  finishSession: vi.fn(),
  getSession: vi.fn(),
}))

const topic: TopicDetail = {
  actions: {
    canPracticeListening: true,
    canPracticeRecognition: true,
    canStudy: false,
    canTakeTest: true,
  },
  bestTestPercent: null,
  code: 'FAMILY',
  hskLevel: 1,
  id: 11,
  learnedWords: 8,
  name: 'Gia đình',
  percent: 80,
  status: 'IN_PROGRESS',
  totalWords: 10,
  words: [],
}

const choiceOptions = [
  { key: 'A', text: 'mẹ' },
  { key: 'B', text: 'bố' },
]

const items: SessionView['items'] = [
  {
    no: 1,
    options: choiceOptions,
    prompt: { text: '妈妈' },
    rules: {},
    skill: 'VOCAB',
    type: 'MEANING_CHOICE',
  },
  {
    no: 2,
    options: [
      { key: 'A', text: '妈妈' },
      { key: 'B', text: '爸爸' },
    ],
    prompt: { audioUrl: '/audio/baba.mp3' },
    rules: { hasAudio: true, maxReplays: 3 },
    skill: 'LISTENING',
    type: 'AUDIO_WORD_CHOICE',
  },
  {
    no: 3,
    prompt: { hint: 'bạn bè', text: '朋友' },
    rules: {},
    skill: 'VOCAB',
    type: 'PINYIN_INPUT',
  },
  {
    no: 4,
    options: [
      { key: 'A', text: '朋友' },
      { key: 'B', text: '老师' },
    ],
    prompt: { hint: 'Chúng tôi là bạn.', sentence: '我们是＿＿' },
    rules: {},
    skill: 'READING',
    type: 'CLOZE_CHOICE',
  },
]

function testSession(overrides: Partial<SessionView> = {}): SessionView {
  return {
    answered: [],
    expiresAt: '2026-10-21T10:00:00Z',
    feedback: 'DEFERRED',
    items,
    refId: 11,
    servedAt: '2026-10-20T10:00:00Z',
    sessionId: 91,
    source: 'TOPIC_TEST',
    status: 'IN_PROGRESS',
    ...overrides,
  }
}

const passedResult: FinishResult = {
  extra: {
    bestPercent: 75,
    firstCompletion: false,
    passed: false,
    topicCompleted: false,
    wrongWords: [{ lexemeId: 102, meaning: 'bố', pinyin: 'bàba', text: '爸爸' }],
  },
  items: [],
  maxScore: 4,
  percent: 75,
  score: 3,
  sessionId: 91,
  status: 'SUBMITTED',
  suspicious: false,
}

function renderPage() {
  const router = createMemoryRouter(
    [{ element: <Scr012TopicStudyPage />, path: '/learn/topics/:id/*' }],
    { initialEntries: ['/learn/topics/11/test'] },
  )
  render(<RouterProvider router={router} />)
  return router
}

async function startTest(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: 'Bắt đầu kiểm tra' }))
  await screen.findByText('Câu 1/4')
}

function deferred<T>() {
  let resolveValue: (value: T) => void = () => undefined
  const promise = new Promise<T>((resolve) => {
    resolveValue = resolve
  })
  return { promise, resolve: (value: T) => resolveValue(value) }
}

async function confirmSubmit(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: 'Nộp bài' }))
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Nộp bài' }))
}

function restorePinyin(answer: string | null) {
  const restored = testSession({
    answered: [
      { answer: 'A', no: 1 },
      { answer: 'A', no: 2 },
      { answer, no: 3 },
      { answer: 'A', no: 4 },
    ],
  })
  vi.mocked(findActiveSession).mockResolvedValue(restored)
  vi.mocked(getSession).mockResolvedValue(restored)
  return restored
}

function recognitionSession(sessionId: number): SessionView {
  return testSession({
    feedback: 'IMMEDIATE',
    items: items
      .filter((item) => item.type === 'MEANING_CHOICE')
      .map((item) => ({
        ...item,
        prompt: { text: sessionId === 101 ? '妈妈' : '爸爸' },
      })),
    sessionId,
    source: 'TOPIC_PRACTICE',
  })
}

function renderEntry(entry: string | { pathname: string; state: unknown }) {
  const router = createMemoryRouter(
    [{ element: <Scr012TopicStudyPage />, path: '/learn/topics/:id/*' }],
    { initialEntries: [entry] },
  )
  return { router, ...render(<RouterProvider router={router} />) }
}

describe('TopicTestController', () => {
  beforeEach(() => {
    vi.mocked(getTopic).mockReset().mockResolvedValue(topic)
    vi.mocked(startTopicStudySession).mockReset()
    vi.mocked(startTopicPracticeSession).mockReset()
    vi.mocked(startTopicTest).mockReset().mockResolvedValue(testSession())
    vi.mocked(findActiveSession).mockReset().mockResolvedValue(null)
    vi.mocked(getSession).mockReset()
    vi.mocked(answerItem)
      .mockReset()
      .mockImplementation(async (_id, request) => ({ accepted: true, no: request.no }))
    vi.mocked(finishSession).mockReset().mockResolvedValue(passedResult)
    vi.mocked(abandonSession).mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('lưu nháp ngay khi chọn, đánh dấu câu đã làm bằng chữ, đi lại được giữa các câu', async () => {
    renderPage()
    const user = userEvent.setup()
    await startTest(user)

    expect(screen.getByRole('button', { name: 'Câu 1, chưa làm' })).toBeVisible()
    await user.click(screen.getByRole('button', { name: /mẹ/ }))

    await waitFor(() =>
      expect(answerItem).toHaveBeenCalledWith(91, expect.objectContaining({ answer: 'A', no: 1 })),
    )
    expect(screen.getByRole('button', { name: 'Câu 1, đã làm' })).toBeVisible()

    await user.click(screen.getByRole('button', { name: 'Câu 4, chưa làm' }))
    expect(screen.getByText('我们是＿＿')).toHaveAttribute('lang', 'zh-CN')

    await user.click(screen.getByRole('button', { name: /Câu trước/ }))
    expect(screen.getByText('朋友')).toBeVisible()
    expect(screen.getByLabelText('Pinyin')).toBeVisible()
  })

  it('PINYIN_INPUT lưu nháp sau 500 ms ngừng gõ', async () => {
    renderPage()
    const user = userEvent.setup()
    await startTest(user)
    await user.click(screen.getByRole('button', { name: 'Câu 3, chưa làm' }))

    await user.type(screen.getByLabelText('Pinyin'), 'peng2you')
    expect(answerItem).not.toHaveBeenCalled()

    await waitFor(
      () =>
        expect(answerItem).toHaveBeenCalledWith(
          91,
          expect.objectContaining({ answer: 'peng2you', no: 3 }),
        ),
      { timeout: 2000 },
    )
    expect(answerItem).toHaveBeenCalledTimes(1)
  })

  it('nộp ngay khi còn nháp chờ debounce vẫn gửi đủ câu trả lời, xác nhận nêu số câu chưa làm', async () => {
    renderPage()
    const user = userEvent.setup()
    await startTest(user)
    await user.click(screen.getByRole('button', { name: /mẹ/ }))
    await user.click(screen.getByRole('button', { name: 'Câu 3, chưa làm' }))
    await user.type(screen.getByLabelText('Pinyin'), 'peng2you')

    await user.click(screen.getByRole('button', { name: 'Nộp bài' }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText('Còn 2 câu chưa làm — câu chưa làm tính sai.')).toBeVisible()
    await user.click(within(dialog).getByRole('button', { name: 'Nộp bài' }))

    await waitFor(() => expect(finishSession).toHaveBeenCalledTimes(1))
    const answers = vi.mocked(finishSession).mock.calls[0]?.[1]?.answers ?? []
    expect(answers).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ answer: 'A', no: 1 }),
        expect.objectContaining({ answer: 'peng2you', no: 3 }),
      ]),
    )
  })

  it('kết quả dùng passed/wrongWords của server, luyện lại từ sai mang focusLexemeIds', async () => {
    const router = renderPage()
    const user = userEvent.setup()
    await startTest(user)

    await user.click(screen.getByRole('button', { name: 'Nộp bài' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Nộp bài' }))

    expect(await screen.findByText('Kết quả kiểm tra')).toBeVisible()
    expect(screen.getByText('Chưa đạt')).toBeVisible()
    expect(screen.getByText('3/4')).toBeVisible()
    expect(screen.getByText('爸爸')).toBeVisible()
    await waitFor(() => expect(getTopic).toHaveBeenCalledTimes(2))

    await user.click(screen.getByRole('button', { name: 'Luyện lại từ sai' }))
    expect(router.state.location.pathname).toBe('/learn/topics/11/practice/recognition')
    await waitFor(() =>
      expect(startTopicPracticeSession).toHaveBeenCalledWith(11, {
        focusLexemeIds: [102],
        mode: 'RECOGNITION',
      }),
    )
  })

  it('đạt lần đầu hiện "Hoàn thành chủ đề"; chủ đề bị khoá sau khi tải lại vẫn giữ kết quả', async () => {
    vi.mocked(finishSession).mockResolvedValue({
      ...passedResult,
      extra: { firstCompletion: true, passed: true, wrongWords: [] },
      percent: 100,
      score: 4,
    })
    vi.mocked(getTopic)
      .mockResolvedValueOnce(topic)
      .mockResolvedValue({
        ...topic,
        actions: { ...topic.actions, canTakeTest: false, testBlockedReason: 'RETRY_TOO_SOON' },
      })
    renderPage()
    const user = userEvent.setup()
    await startTest(user)

    await user.click(screen.getByRole('button', { name: 'Nộp bài' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Nộp bài' }))

    expect(await screen.findByText('Hoàn thành chủ đề')).toBeVisible()
    await waitFor(() => expect(getTopic).toHaveBeenCalledTimes(2))
    expect(screen.getByText('Đạt')).toBeVisible()
    expect(screen.queryByText('Bước này đang bị khoá')).not.toBeInTheDocument()
  })

  it('FR-045: tải lại trang khôi phục lượt và nháp, mở câu chưa làm đầu tiên', async () => {
    const restored = testSession({ answered: [{ answer: 'B', no: 1 }] })
    vi.mocked(findActiveSession).mockResolvedValue(restored)
    vi.mocked(getSession).mockResolvedValue(restored)
    renderPage()

    expect(await screen.findByText('Câu 2/4')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Câu 1, đã làm' })).toBeVisible()
    expect(startTopicTest).not.toHaveBeenCalled()
  })

  it('giữ số lần nghe của từng câu khi đi sang câu khác rồi quay lại', async () => {
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined)
    renderPage()
    const user = userEvent.setup()
    await startTest(user)
    await user.click(screen.getByRole('button', { name: 'Câu 2, chưa làm' }))

    const listen = screen.getByRole('button', { name: /Nghe từ/ })
    await user.click(listen)
    await user.click(listen)
    expect(screen.getByText('Đã nghe 2/3 lần')).toBeVisible()

    await user.click(screen.getByRole('button', { name: /Câu sau/ }))
    await user.click(screen.getByRole('button', { name: /Câu trước/ }))

    expect(screen.getByText('Đã nghe 2/3 lần')).toBeVisible()
    await user.click(screen.getByRole('button', { name: /爸爸/ }))
    await waitFor(() =>
      expect(answerItem).toHaveBeenCalledWith(
        91,
        expect.objectContaining({ answer: 'B', no: 2, replayCount: 2 }),
      ),
    )
  })

  it('NOT_ENOUGH_LEARNED nêu số từ cần học', async () => {
    vi.mocked(startTopicTest).mockRejectedValue(
      new ApiError({
        errorCode: 'NOT_ENOUGH_LEARNED',
        extra: { learnedWords: 5, requiredWords: 8 },
        message: 'x',
        status: 422,
      }),
    )
    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Bắt đầu kiểm tra' }))

    expect(await screen.findByText('Cần học ít nhất 8 từ (đã học 5).')).toBeVisible()
  })

  it('RETRY_TOO_SOON đếm ngược và khoá nút làm lại', async () => {
    vi.mocked(startTopicTest).mockRejectedValue(
      new ApiError({
        errorCode: 'RETRY_TOO_SOON',
        extra: { retryAfterSeconds: 125 },
        message: 'x',
        status: 429,
      }),
    )
    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Bắt đầu kiểm tra' }))

    expect(await screen.findByText(/Bạn có thể làm lại sau 02:0[45]\./)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Làm lại bài kiểm tra' })).toBeDisabled()
  })

  it('FR044_pending save keeps the latest answers of both questions when submitting', async () => {
    const saving = deferred<AnswerResult>()
    vi.mocked(answerItem).mockReturnValueOnce(saving.promise)
    renderPage()
    const user = userEvent.setup()
    await startTest(user)
    await user.click(screen.getByRole('button', { name: /mẹ/ }))
    await user.click(screen.getByRole('button', { name: 'Câu 4, chưa làm' }))
    await user.click(screen.getByRole('button', { name: /老师/ }))
    await user.click(screen.getByRole('button', { name: 'Câu 1, đã làm' }))
    await user.click(screen.getByRole('button', { name: /bố/ }))
    await confirmSubmit(user)
    expect(finishSession).not.toHaveBeenCalled()
    expect(answerItem).toHaveBeenCalledTimes(1)

    await act(async () => saving.resolve({ accepted: true, no: 1 }))
    await screen.findByText('Kết quả kiểm tra')
    expect(finishSession).toHaveBeenCalledTimes(1)
    expect(vi.mocked(finishSession).mock.lastCall?.[1]?.answers).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ answer: 'B', no: 1 }),
        expect.objectContaining({ answer: 'B', no: 4 }),
      ]),
    )
  })

  it('FR044_pinyin saves at 500 ms and navigation flushes without waiting for debounce', async () => {
    renderPage()
    const user = userEvent.setup()
    await startTest(user)
    await user.click(screen.getByRole('button', { name: 'Câu 3, chưa làm' }))
    vi.useFakeTimers()
    fireEvent.change(screen.getByLabelText('Pinyin'), { target: { value: 'peng2you' } })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(499)
    })
    expect(answerItem).not.toHaveBeenCalled()
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1)
    })
    expect(answerItem).toHaveBeenCalledWith(
      91,
      expect.objectContaining({ answer: 'peng2you', no: 3 }),
    )
    fireEvent.change(screen.getByLabelText('Pinyin'), { target: { value: 'peng2you3' } })
    fireEvent.click(screen.getByRole('button', { name: /Câu trước/ }))
    await act(async () => undefined)
    expect(answerItem).toHaveBeenLastCalledWith(
      91,
      expect.objectContaining({ answer: 'peng2you3', no: 3 }),
    )
  })

  it('FR044_clearing a restored answer submits skipped immediately even inside debounce', async () => {
    restorePinyin('peng2you')
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Câu 3, đã làm' }))
    fireEvent.change(screen.getByLabelText('Pinyin'), { target: { value: '' } })
    expect(screen.getByRole('button', { name: 'Câu 3, chưa làm' })).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Nộp bài' }))
    expect(
      within(screen.getByRole('dialog')).getByText('Còn 1 câu chưa làm — câu chưa làm tính sai.'),
    ).toBeVisible()
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Nộp bài' }))
    await screen.findByText('Kết quả kiểm tra')
    const answers = vi.mocked(finishSession).mock.lastCall?.[1]?.answers
    expect(answers).toEqual([expect.objectContaining({ no: 3, skipped: true })])
    expect(answers?.[0]).not.toHaveProperty('answer')
    expect(answers?.[0]).not.toHaveProperty('audioFailed')
  })

  it('FR044_clearing a new pending answer is included in finish before debounce', async () => {
    renderPage()
    const user = userEvent.setup()
    await startTest(user)
    await user.click(screen.getByRole('button', { name: 'Câu 3, chưa làm' }))
    fireEvent.change(screen.getByLabelText('Pinyin'), { target: { value: 'peng2you' } })
    fireEvent.change(screen.getByLabelText('Pinyin'), { target: { value: '' } })
    await confirmSubmit(user)
    await screen.findByText('Kết quả kiểm tra')
    const answers = vi.mocked(finishSession).mock.lastCall?.[1]?.answers
    expect(answers).toEqual([expect.objectContaining({ no: 3, skipped: true })])
    expect(answers?.[0]).not.toHaveProperty('answer')
  })

  it('FR044_clearing a saved answer autosaves skipped without an answer field', async () => {
    restorePinyin('peng2you')
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Câu 3, đã làm' }))
    vi.useFakeTimers()
    fireEvent.change(screen.getByLabelText('Pinyin'), { target: { value: '  ' } })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(500)
    })
    expect(answerItem).toHaveBeenCalledTimes(1)
    expect(vi.mocked(answerItem).mock.lastCall?.[1]).toMatchObject({ no: 3, skipped: true })
    expect(vi.mocked(answerItem).mock.lastCall?.[1]).not.toHaveProperty('answer')
  })

  it('FR044_clearing then retyping before submit sends the new answer without skipped', async () => {
    restorePinyin('peng2you')
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Câu 3, đã làm' }))
    fireEvent.change(screen.getByLabelText('Pinyin'), { target: { value: '' } })
    fireEvent.change(screen.getByLabelText('Pinyin'), { target: { value: 'peng2you3' } })
    await confirmSubmit(user)
    await screen.findByText('Kết quả kiểm tra')
    expect(vi.mocked(finishSession).mock.lastCall?.[1]?.answers).toEqual([
      expect.objectContaining({ answer: 'peng2you3', no: 3 }),
    ])
    expect(vi.mocked(finishSession).mock.lastCall?.[1]?.answers?.[0]).not.toHaveProperty(
      'skipped',
      true,
    )
  })

  it('FR045_null restored answer is unanswered and is not resaved or inferred as deletion', async () => {
    restorePinyin(null)
    renderPage()
    const user = userEvent.setup()
    expect(await screen.findByText('Câu 3/4')).toBeVisible()
    expect(screen.getByLabelText('Pinyin')).toHaveValue('')
    expect(answerItem).not.toHaveBeenCalled()
    expect(finishSession).not.toHaveBeenCalled()
    await confirmSubmit(user)
    await screen.findByText('Kết quả kiểm tra')
    expect(finishSession).toHaveBeenCalledWith(91, undefined)
    expect(answerItem).not.toHaveBeenCalled()
  })

  it('FR044_untouched empty input is not submitted as skipped', async () => {
    renderPage()
    const user = userEvent.setup()
    await startTest(user)
    await user.click(screen.getByRole('button', { name: 'Câu 3, chưa làm' }))
    await confirmSubmit(user)
    await screen.findByText('Kết quả kiểm tra')
    expect(finishSession).toHaveBeenCalledWith(91, undefined)
    expect(answerItem).not.toHaveBeenCalled()
  })

  it('FR044_whitespace without a previous answer never sends a deletion', async () => {
    renderPage()
    const user = userEvent.setup()
    await startTest(user)
    await user.click(screen.getByRole('button', { name: 'Câu 3, chưa làm' }))
    vi.useFakeTimers()
    fireEvent.change(screen.getByLabelText('Pinyin'), { target: { value: '   ' } })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(500)
    })
    expect(answerItem).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Câu 3, chưa làm' })).toBeVisible()
  })

  it('FR044_save error pauses retries and the retry button saves the retained draft', async () => {
    const retried = deferred<AnswerResult>()
    vi.mocked(answerItem)
      .mockRejectedValueOnce(
        new ApiError({
          errorCode: 'INTERNAL_ERROR',
          message: 'x',
          requestId: 't17-save',
          status: 500,
        }),
      )
      .mockReturnValueOnce(retried.promise)
    renderPage()
    const user = userEvent.setup()
    await startTest(user)
    await user.click(screen.getByRole('button', { name: /mẹ/ }))
    await screen.findByRole('alert')
    await act(async () => undefined)
    expect(answerItem).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: /mẹ/ })).toHaveAttribute('aria-pressed', 'true')
    await user.click(screen.getByRole('button', { name: 'Thử lại lưu nháp' }))
    expect(answerItem).toHaveBeenCalledTimes(2)
    await act(async () => retried.resolve({ accepted: true, no: 1 }))
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
    expect(answerItem).toHaveBeenLastCalledWith(91, expect.objectContaining({ answer: 'A', no: 1 }))
  })

  it('FR044_only editing the failed question resumes the paused queue with the latest values', async () => {
    const retry = deferred<AnswerResult>()
    vi.mocked(answerItem)
      .mockRejectedValueOnce(
        new ApiError({
          errorCode: 'INTERNAL_ERROR',
          message: 'x',
          status: 500,
        }),
      )
      .mockReturnValueOnce(retry.promise)
    renderPage()
    const user = userEvent.setup()
    await startTest(user)
    await user.click(screen.getByRole('button', { name: /mẹ/ }))
    await screen.findByRole('alert')
    await user.click(screen.getByRole('button', { name: 'Câu 4, chưa làm' }))
    await user.click(screen.getByRole('button', { name: /老师/ }))
    expect(answerItem).toHaveBeenCalledTimes(1)
    await user.click(screen.getByRole('button', { name: 'Câu 1, đã làm' }))
    await user.click(screen.getByRole('button', { name: /bố/ }))
    expect(answerItem).toHaveBeenLastCalledWith(91, expect.objectContaining({ answer: 'B', no: 1 }))
    await act(async () => retry.resolve({ accepted: true, no: 1 }))
    await waitFor(() => expect(answerItem).toHaveBeenCalledTimes(3))
    expect(answerItem).toHaveBeenLastCalledWith(91, expect.objectContaining({ answer: 'B', no: 4 }))
  })

  it('FR044_cancelling confirmation never finishes or invokes browser confirm', async () => {
    const browserConfirm = vi.spyOn(window, 'confirm')
    renderPage()
    const user = userEvent.setup()
    await startTest(user)
    await user.click(screen.getByRole('button', { name: 'Nộp bài' }))
    expect(
      within(screen.getByRole('dialog')).getByText('Còn 4 câu chưa làm — câu chưa làm tính sai.'),
    ).toBeVisible()
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Huỷ' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(finishSession).not.toHaveBeenCalled()
    expect(browserConfirm).not.toHaveBeenCalled()
    expect(screen.getByText('Câu 1/4')).toBeVisible()
  })

  it('FR044_double submit while finishing only makes one finish request', async () => {
    const finish = deferred<FinishResult>()
    vi.mocked(finishSession).mockReturnValue(finish.promise)
    renderPage()
    const user = userEvent.setup()
    await startTest(user)
    await user.click(screen.getByRole('button', { name: 'Nộp bài' }))
    const confirm = within(screen.getByRole('dialog')).getByRole('button', { name: 'Nộp bài' })
    await user.dblClick(confirm)
    expect(finishSession).toHaveBeenCalledTimes(1)
    expect(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'Đang xử lý' }),
    ).toBeDisabled()
    await act(async () => finish.resolve(passedResult))
    expect(await screen.findByText('Kết quả kiểm tra')).toBeVisible()
    expect(finishSession).toHaveBeenCalledTimes(1)
  })

  it('FR044_finish error retains drafts and requires another confirmation', async () => {
    vi.mocked(finishSession).mockRejectedValueOnce(
      new ApiError({
        errorCode: 'INTERNAL_ERROR',
        message: 'x',
        requestId: 't17-finish',
        status: 500,
      }),
    )
    renderPage()
    const user = userEvent.setup()
    await startTest(user)
    await user.click(screen.getByRole('button', { name: /mẹ/ }))
    await confirmSubmit(user)
    expect(await screen.findByRole('alert')).toHaveTextContent('t17-finish')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /mẹ/ })).toHaveAttribute('aria-pressed', 'true')
    await act(async () => undefined)
    expect(finishSession).toHaveBeenCalledTimes(1)
    await user.click(screen.getByRole('button', { name: 'Nộp bài' }))
    expect(finishSession).toHaveBeenCalledTimes(1)
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Nộp bài' }))
    await screen.findByText('Kết quả kiểm tra')
    expect(finishSession).toHaveBeenCalledTimes(2)
  })

  it('FR045_restore keeps server drafts without grading, autosaving or resubmitting unchanged evidence', async () => {
    restorePinyin('peng2you')
    renderPage()
    const user = userEvent.setup()
    await screen.findByText('Câu 1/4')
    expect(answerItem).not.toHaveBeenCalled()
    expect(finishSession).not.toHaveBeenCalled()
    expect(screen.queryByText('Chính xác')).not.toBeInTheDocument()
    expect(screen.queryByText('Chưa chính xác')).not.toBeInTheDocument()
    expect(screen.queryByText(/Đáp án đúng:/)).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Nộp bài' }))
    expect(
      within(screen.getByRole('dialog')).getByText(
        'Bạn đã làm hết các câu. Nộp bài để xem kết quả?',
      ),
    ).toBeVisible()
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Nộp bài' }))
    await screen.findByText('Kết quả kiểm tra')
    expect(finishSession).toHaveBeenCalledWith(91, undefined)
  })

  it('FR045_SUBMITTED sync without FinishResult only shows the submitted notice', async () => {
    vi.mocked(finishSession).mockRejectedValueOnce(
      new ApiError({
        errorCode: 'SESSION_ALREADY_SUBMITTED',
        message: 'x',
        status: 409,
      }),
    )
    vi.mocked(getSession).mockResolvedValue(testSession({ status: 'SUBMITTED' }))
    renderPage()
    const user = userEvent.setup()
    await startTest(user)
    await confirmSubmit(user)
    expect(await screen.findByText('Đã nộp bài')).toBeVisible()
    expect(screen.queryByText('Kết quả kiểm tra')).not.toBeInTheDocument()
    expect(screen.queryByText('Chưa đạt')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Luyện lại từ sai' })).not.toBeInTheDocument()
  })

  it('FR044_empty wrongWords hides the CTA and displays the server percentage', async () => {
    vi.mocked(finishSession).mockResolvedValue({
      ...passedResult,
      extra: { passed: false, wrongWords: [] },
    })
    renderPage()
    const user = userEvent.setup()
    await startTest(user)
    await confirmSubmit(user)
    expect(await screen.findByText('75%')).toBeVisible()
    expect(screen.getByText('Chưa đạt')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Luyện lại từ sai' })).not.toBeInTheDocument()
  })

  it('FR045_old debounce timer cannot save into the new test session', async () => {
    vi.mocked(startTopicTest)
      .mockResolvedValueOnce(testSession())
      .mockResolvedValueOnce(testSession({ sessionId: 92 }))
    renderPage()
    const user = userEvent.setup()
    await startTest(user)
    await user.click(screen.getByRole('button', { name: 'Câu 3, chưa làm' }))
    vi.useFakeTimers()
    fireEvent.change(screen.getByLabelText('Pinyin'), { target: { value: 'peng2you' } })
    fireEvent.click(screen.getByRole('button', { name: 'Nộp bài' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Nộp bài' }))
    await act(async () => undefined)
    fireEvent.click(screen.getByRole('button', { name: 'Làm bài kiểm tra mới' }))
    await act(async () => undefined)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(500)
    })
    expect(screen.getByText('Câu 1/4')).toBeVisible()
    expect(screen.getByRole('button', { name: /mẹ/ })).toHaveAttribute('aria-pressed', 'false')
    expect(answerItem).not.toHaveBeenCalled()
    expect(startTopicTest).toHaveBeenCalledTimes(2)
  })

  it('FR045_new session measures the first question again when its number is unchanged', async () => {
    let now = 1000
    vi.spyOn(Date, 'now').mockImplementation(() => now)
    vi.mocked(startTopicTest)
      .mockResolvedValueOnce(testSession())
      .mockResolvedValueOnce(testSession({ sessionId: 92 }))
    renderPage()
    const user = userEvent.setup()
    await startTest(user)
    await confirmSubmit(user)
    now = 10_000
    await user.click(await screen.findByRole('button', { name: 'Làm bài kiểm tra mới' }))
    await screen.findByText('Câu 1/4')
    now = 10_800
    await user.click(screen.getByRole('button', { name: /mẹ/ }))
    expect(vi.mocked(answerItem).mock.lastCall?.[1].durationMs).toBe(800)
    expect(answerItem).toHaveBeenLastCalledWith(92, expect.objectContaining({ answer: 'A', no: 1 }))
  })

  it.each([false, true])(
    'FR044_wrong-word CTA creates focus and keeps old recognition with all answers=%s',
    async (fullyAnswered) => {
      const old = recognitionSession(101)
      if (fullyAnswered) old.answered = [{ answer: 'A', correct: true, counted: true, no: 1 }]
      vi.mocked(findActiveSession).mockImplementation(async (source) =>
        source === 'TOPIC_PRACTICE' ? old : null,
      )
      vi.mocked(getSession).mockResolvedValue(old)
      vi.mocked(startTopicPracticeSession).mockResolvedValue(recognitionSession(102))
      const router = renderPage()
      const user = userEvent.setup()
      await startTest(user)
      await confirmSubmit(user)
      await user.click(await screen.findByRole('button', { name: 'Luyện lại từ sai' }))
      await waitFor(() => expect(startTopicPracticeSession).toHaveBeenCalledTimes(1))
      expect(startTopicPracticeSession).toHaveBeenCalledWith(11, {
        focusLexemeIds: [102],
        mode: 'RECOGNITION',
      })
      expect(await screen.findByText('爸爸')).toBeVisible()
      expect(abandonSession).not.toHaveBeenCalled()
      expect(finishSession).toHaveBeenCalledTimes(1)
      expect(router.state.location.state).toBeNull()
    },
  )

  it('FR044_failed focused start retains state without looping and retries only on click', async () => {
    const old = recognitionSession(101)
    vi.mocked(findActiveSession).mockResolvedValue(old)
    vi.mocked(getSession).mockResolvedValue(old)
    vi.mocked(startTopicPracticeSession)
      .mockRejectedValueOnce(
        new ApiError({
          errorCode: 'INTERNAL_ERROR',
          message: 'x',
          status: 500,
        }),
      )
      .mockResolvedValueOnce(recognitionSession(102))
    const { router } = renderEntry({
      pathname: '/learn/topics/11/practice/recognition',
      state: { focusLexemeIds: [102] },
    })
    const user = userEvent.setup()
    const retry = await screen.findByRole('button', { name: 'Thử lại' })
    expect(router.state.location.state).toEqual({ focusLexemeIds: [102] })
    expect(startTopicPracticeSession).toHaveBeenCalledTimes(1)
    await user.click(retry)
    expect(await screen.findByText('爸爸')).toBeVisible()
    expect(startTopicPracticeSession).toHaveBeenCalledTimes(2)
    expect(router.state.location.state).toBeNull()
    expect(abandonSession).not.toHaveBeenCalled()
  })

  it('FR045_refresh after focus state is cleared only restores the focused session', async () => {
    vi.mocked(findActiveSession).mockResolvedValue(recognitionSession(101))
    vi.mocked(getSession).mockResolvedValue(recognitionSession(101))
    vi.mocked(startTopicPracticeSession).mockResolvedValue(recognitionSession(102))
    const first = renderEntry({
      pathname: '/learn/topics/11/practice/recognition',
      state: { focusLexemeIds: [102] },
    })
    await screen.findByText('爸爸')
    expect(first.router.state.location.state).toBeNull()
    first.unmount()
    vi.mocked(findActiveSession).mockResolvedValue(recognitionSession(102))
    vi.mocked(getSession).mockResolvedValue(recognitionSession(102))
    renderEntry({ pathname: '/learn/topics/11/practice/recognition', state: null })
    expect(await screen.findByText('爸爸')).toBeVisible()
    expect(startTopicPracticeSession).toHaveBeenCalledTimes(1)
  })

  it('FR044_focus with an active listening session still asks before abandoning it', async () => {
    const listening = testSession({
      feedback: 'IMMEDIATE',
      items: items.filter((item) => item.type === 'AUDIO_WORD_CHOICE'),
      sessionId: 101,
      source: 'TOPIC_PRACTICE',
    })
    vi.mocked(findActiveSession).mockResolvedValue(listening)
    vi.mocked(getSession).mockResolvedValue(listening)
    vi.mocked(startTopicPracticeSession).mockResolvedValue(recognitionSession(102))
    renderEntry({
      pathname: '/learn/topics/11/practice/recognition',
      state: { focusLexemeIds: [102] },
    })
    const user = userEvent.setup()
    await screen.findByText('Có lượt luyện đang dở')
    expect(startTopicPracticeSession).not.toHaveBeenCalled()
    expect(abandonSession).not.toHaveBeenCalled()
    vi.mocked(abandonSession).mockResolvedValue(undefined)
    await user.click(screen.getByRole('button', { name: 'Bỏ lượt đó, bắt đầu lượt mới' }))
    await user.click(screen.getByRole('button', { name: 'Bỏ lượt và bắt đầu mới' }))
    expect(await screen.findByText('爸爸')).toBeVisible()
    expect(startTopicPracticeSession).toHaveBeenCalledWith(11, {
      focusLexemeIds: [102],
      mode: 'RECOGNITION',
    })
    expect(abandonSession).toHaveBeenCalledWith(101)
  })
})
