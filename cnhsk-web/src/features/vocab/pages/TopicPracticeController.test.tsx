import { render, screen, waitFor } from '@testing-library/react'
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
import type { SessionView } from '@/features/practice'
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

describe('TopicPracticeController', () => {
  beforeEach(() => {
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
})
