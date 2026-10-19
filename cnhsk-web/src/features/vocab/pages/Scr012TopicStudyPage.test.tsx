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
import type { FinishResult, SessionView } from '@/features/practice'
import { ApiError } from '@/shared/api'

import { getTopic, startTopicStudySession } from '../api/topics'
import type { TopicDetail } from '../types'
import Scr012TopicStudyPage from './Scr012TopicStudyPage'

vi.mock('../api/topics', () => ({
  getTopic: vi.fn(),
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
    canPracticeListening: false,
    canPracticeRecognition: true,
    canStudy: true,
    canTakeTest: false,
    requiredWords: 8,
    retryAvailableAt: '2026-10-20T03:30:00Z',
    testBlockedReason: 'NOT_ENOUGH_LEARNED',
  },
  bestTestPercent: null,
  code: 'FAMILY',
  hskLevel: 9,
  id: 11,
  learnedWords: 6,
  name: 'Gia đình 家庭',
  percent: 61,
  status: 'IN_PROGRESS',
  totalWords: 10,
  words: [],
}

const items: SessionView['items'] = [
  {
    lexemeId: 101,
    no: 1,
    prompt: {
      audioUrl: null,
      examples: [{ cn: '我爱妈妈。', pinyin: 'Wǒ ài māma.', vi: 'Tôi yêu mẹ.' }],
      meaning: 'mẹ',
      pinyin: 'māma',
      sinoViet: '',
      text: '妈妈',
    },
    rules: { hasAudio: false, maxReplays: 3 },
    skill: 'VOCAB',
    type: 'STUDY_CARD',
  },
  {
    lexemeId: 102,
    no: 2,
    prompt: {
      audioUrl: '/audio/baba.mp3',
      meaning: 'bố',
      pinyin: 'bàba',
      sinoViet: 'ba ba',
      text: '爸爸',
    },
    rules: { hasAudio: true, maxReplays: 3 },
    skill: 'VOCAB',
    type: 'STUDY_CARD',
  },
]

function session(overrides: Partial<SessionView> = {}): SessionView {
  return {
    answered: [],
    expiresAt: '2026-10-20T12:00:00Z',
    feedback: 'RECORD',
    items,
    refId: 11,
    servedAt: '2026-10-20T10:00:00Z',
    sessionId: 71,
    source: 'TOPIC_STUDY',
    status: 'IN_PROGRESS',
    ...overrides,
  }
}

const finishResult: FinishResult = {
  items: [],
  maxScore: null,
  score: null,
  sessionId: 71,
  status: 'SUBMITTED',
  suspicious: false,
}

function renderPage(entry = '/learn/topics/11/study') {
  const router = createMemoryRouter(
    [{ element: <Scr012TopicStudyPage />, path: '/learn/topics/:id/*' }],
    { initialEntries: [entry] },
  )
  render(<RouterProvider router={router} />)
  return router
}

describe('Scr012TopicStudyPage', () => {
  beforeEach(() => {
    vi.mocked(getTopic).mockReset().mockResolvedValue(topic)
    vi.mocked(startTopicStudySession).mockReset().mockResolvedValue(session())
    vi.mocked(findActiveSession).mockReset().mockResolvedValue(null)
    vi.mocked(getSession).mockReset().mockResolvedValue(session())
    vi.mocked(answerItem)
      .mockReset()
      .mockImplementation(async (_sessionId, request) => ({ no: request.no }))
    vi.mocked(finishSession).mockReset().mockResolvedValue(finishResult)
    vi.mocked(abandonSession).mockReset().mockResolvedValue(undefined)
  })

  afterEach(() => vi.restoreAllMocks())

  it.each(['/learn/topics/abc/study', '/learn/topics/0/study', '/learn/topics/-1/study'])(
    'ID không hợp lệ hiện trạng thái không tìm thấy: %s',
    async (path) => {
      renderPage(path)

      expect(screen.getByText('Không tìm thấy chủ đề')).toBeVisible()
      expect(getTopic).not.toHaveBeenCalled()
      expect(findActiveSession).not.toHaveBeenCalled()
    },
  )

  it('404 TOPIC_NOT_FOUND hiện trạng thái không tìm thấy', async () => {
    vi.mocked(getTopic).mockRejectedValue(
      new ApiError({
        errorCode: 'TOPIC_NOT_FOUND',
        message: 'Không tồn tại.',
        requestId: 'req-topic-404',
        status: 404,
      }),
    )
    renderPage()

    expect(await screen.findByText('Không tìm thấy chủ đề')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Về danh sách chủ đề' })).toHaveAttribute(
      'href',
      '/learn/topics',
    )
  })

  it('hiện dữ liệu đầu trang từ server và chuẩn hoá route thiếu bước về Học từ mới', async () => {
    const router = renderPage('/learn/topics/11')

    expect(await screen.findByRole('heading', { name: 'Gia đình 家庭' })).toBeVisible()
    expect(screen.getByText('Gia đình 家庭')).toHaveAttribute('lang', 'zh-CN')
    expect(screen.getByText('HSK 9')).toBeVisible()
    expect(screen.getByText('Đã học 6/10 từ')).toBeVisible()
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '61')
    await waitFor(() => expect(router.state.location.pathname).toBe('/learn/topics/11/study'))
    expect(router.state.historyAction).toBe('REPLACE')
  })

  it('hiện lý do khoá theo actions, gồm số từ còn thiếu', async () => {
    renderPage('/learn/topics/11/test')

    expect(await screen.findByText('Bước này đang bị khoá')).toBeVisible()
    expect(screen.getAllByText('Học thêm 2 từ để mở bài kiểm tra.')).toHaveLength(2)
    expect(screen.getByText('Luyện nghe').closest('[aria-disabled="true"]')).toHaveAttribute(
      'title',
      'Chủ đề chưa có âm thanh.',
    )
  })

  it('định dạng thời điểm được kiểm tra lại theo giờ Việt Nam', async () => {
    vi.mocked(getTopic).mockResolvedValue({
      ...topic,
      actions: {
        ...topic.actions,
        testBlockedReason: 'RETRY_TOO_SOON',
      },
    })
    renderPage('/learn/topics/11/test')

    expect(await screen.findAllByText('Bạn có thể kiểm tra lại từ 10:30 20/10/2026.')).toHaveLength(
      2,
    )
  })

  it('dùng khối trung tính chung cho bước FE-26 và không hiện nội dung mastery', async () => {
    renderPage('/learn/topics/11/practice/recognition')

    expect(await screen.findAllByRole('heading', { name: 'Luyện nhận diện' })).toHaveLength(2)
    expect(
      screen.getByText('Nội dung của bước này sẽ được bổ sung trong phần tiếp theo.'),
    ).toBeVisible()
    expect(screen.queryByText(/TODO|thành thạo|mở khóa khi/i)).not.toBeInTheDocument()
  })

  it('khôi phục lượt đang học và mở ở từ chưa trả lời mà không tạo lượt mới', async () => {
    const active = session({ answered: [{ answer: 'LEARNED', correct: null, no: 1 }] })
    vi.mocked(findActiveSession).mockResolvedValue(active)
    vi.mocked(getSession).mockResolvedValue(active)
    renderPage()

    expect(await screen.findByText('爸爸')).toBeVisible()
    expect(screen.getByText('Câu 2/2')).toBeVisible()
    expect(startTopicStudySession).not.toHaveBeenCalled()
    expect(getSession).toHaveBeenCalledWith(71, expect.any(AbortSignal))
  })

  it('gửi LEARNED và KNOWN cùng durationMs đo từ lúc thẻ hiện', async () => {
    const user = userEvent.setup()
    const now = vi.spyOn(Date, 'now').mockReturnValue(1_000)
    renderPage()
    await screen.findByText('妈妈')
    expect(screen.queryByRole('button', { name: /Nghe từ/ })).not.toBeInTheDocument()
    expect(screen.queryByText('ma ma')).not.toBeInTheDocument()

    now.mockReturnValue(2_450)
    await user.click(screen.getByRole('button', { name: 'Tiếp' }))
    expect(answerItem).toHaveBeenNthCalledWith(1, 71, {
      answer: 'LEARNED',
      durationMs: 1450,
      no: 1,
      replayCount: 0,
    })

    expect(await screen.findByText('爸爸')).toBeVisible()
    now.mockReturnValue(4_000)
    await user.click(screen.getByRole('button', { name: 'Tôi đã biết từ này' }))
    expect(answerItem).toHaveBeenNthCalledWith(2, 71, {
      answer: 'KNOWN',
      durationMs: 1550,
      no: 2,
      replayCount: 0,
    })
  })

  it('hiện trạng thái đã học hết khi endpoint trả NO_NEW_WORDS', async () => {
    vi.mocked(startTopicStudySession).mockResolvedValue({ reason: 'NO_NEW_WORDS', session: null })
    renderPage()

    expect(await screen.findByText('Bạn đã học hết từ mới của chủ đề này')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Luyện nhận diện' })).toHaveAttribute(
      'href',
      '/learn/topics/11/practice/recognition',
    )
  })

  it('kết thúc lượt, hiện tổng kết và tải lại chủ đề đúng một lần', async () => {
    const user = userEvent.setup()
    vi.mocked(startTopicStudySession).mockResolvedValue(session({ items: [items[0]!] }))
    renderPage()
    await screen.findByText('妈妈')

    await user.click(screen.getByRole('button', { name: 'Tiếp' }))

    expect(await screen.findByRole('heading', { name: 'Đã học 1 từ mới' })).toBeVisible()
    expect(finishSession).toHaveBeenCalledTimes(1)
    await waitFor(() => expect(getTopic).toHaveBeenCalledTimes(2))
  })

  it('sync được session SUBMITTED khi finishResult null và không crash', async () => {
    const user = userEvent.setup()
    const oneItemSession = session({ items: [items[0]!] })
    const submitted = session({
      answered: [{ answer: 'LEARNED', correct: null, no: 1 }],
      items: [items[0]!],
      status: 'SUBMITTED',
    })
    vi.mocked(startTopicStudySession).mockResolvedValue(oneItemSession)
    vi.mocked(answerItem).mockRejectedValue(
      new ApiError({
        errorCode: 'ANSWER_ALREADY_SUBMITTED',
        message: 'Đã trả lời.',
        requestId: 'req-duplicate',
        status: 409,
      }),
    )
    vi.mocked(getSession).mockResolvedValue(submitted)
    renderPage()
    await screen.findByText('妈妈')

    await user.click(screen.getByRole('button', { name: 'Tiếp' }))

    expect(await screen.findByRole('heading', { name: 'Đã học 1 từ mới' })).toBeVisible()
    expect(getSession).toHaveBeenCalledWith(71)
    expect(screen.queryByText('Đã trả lời.')).not.toBeInTheDocument()
    await waitFor(() => expect(getTopic).toHaveBeenCalledTimes(2))
  })

  it('sync được session sau SESSION_ALREADY_SUBMITTED từ finish', async () => {
    const user = userEvent.setup()
    const oneItemSession = session({ items: [items[0]!] })
    const submitted = session({
      answered: [{ answer: 'LEARNED', correct: null, no: 1 }],
      items: [items[0]!],
      status: 'SUBMITTED',
    })
    vi.mocked(startTopicStudySession).mockResolvedValue(oneItemSession)
    vi.mocked(finishSession).mockRejectedValue(
      new ApiError({
        errorCode: 'SESSION_ALREADY_SUBMITTED',
        message: 'Đã kết thúc.',
        requestId: 'req-finish-race',
        status: 409,
      }),
    )
    vi.mocked(getSession).mockResolvedValue(submitted)
    renderPage()
    await screen.findByText('妈妈')

    await user.click(screen.getByRole('button', { name: 'Tiếp' }))

    expect(await screen.findByRole('heading', { name: 'Đã học 1 từ mới' })).toBeVisible()
    expect(getSession).toHaveBeenCalledWith(71)
    expect(screen.queryByText('Đã kết thúc.')).not.toBeInTheDocument()
    await waitFor(() => expect(getTopic).toHaveBeenCalledTimes(2))
  })

  it.each([
    ['SESSION_EXPIRED', 'Lượt học đã hết hạn. Hãy bắt đầu lượt mới.'],
    ['SESSION_NOT_FOUND', 'Không thể tiếp tục lượt học này. Hãy bắt đầu lại.'],
    ['ATTEMPT_NOT_OWNED', 'Không thể tiếp tục lượt học này. Hãy bắt đầu lại.'],
  ])('mời bắt đầu lại khi API trả %s', async (errorCode, message) => {
    const user = userEvent.setup()
    vi.mocked(answerItem).mockRejectedValue(
      new ApiError({
        errorCode,
        message: 'Thông báo kỹ thuật.',
        requestId: `req-${errorCode}`,
        status:
          errorCode === 'ATTEMPT_NOT_OWNED' ? 403 : errorCode === 'SESSION_NOT_FOUND' ? 404 : 422,
      }),
    )
    renderPage()
    await screen.findByText('妈妈')

    await user.click(screen.getByRole('button', { name: 'Tiếp' }))

    expect(await screen.findByText(message)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Bắt đầu lượt mới' })).toBeVisible()
  })

  it('hiện thời gian chờ khi server giới hạn tần suất', async () => {
    vi.mocked(startTopicStudySession).mockRejectedValue(
      new ApiError({
        errorCode: 'RATE_LIMITED',
        extra: { retryAfterSeconds: 45 },
        message: 'Rate limit.',
        requestId: 'req-rate',
        status: 429,
      }),
    )
    renderPage()

    expect(
      await screen.findByText('Bạn thao tác quá nhanh. Vui lòng thử lại sau 45 giây.'),
    ).toBeVisible()
    expect(screen.getByText('Mã lỗi: req-rate')).toBeVisible()
  })

  it('không abandon khi rời bước học giữa chừng', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('妈妈')

    await user.click(screen.getByRole('link', { name: /Luyện nhận diện/ }))
    expect(
      await screen.findByText('Nội dung của bước này sẽ được bổ sung trong phần tiếp theo.'),
    ).toBeVisible()
    expect(abandonSession).not.toHaveBeenCalled()
  })
})
