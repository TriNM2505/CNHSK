import { render, screen, waitFor, within } from '@testing-library/react'
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
import type { FinishResult, SessionView } from '@/features/practice'
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

  afterEach(() => vi.restoreAllMocks())

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
})
