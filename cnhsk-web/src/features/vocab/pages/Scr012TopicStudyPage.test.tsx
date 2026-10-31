import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react'
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
import type { AnswerResult, FinishResult, ServedItemView, SessionView } from '@/features/practice'
import { ApiError } from '@/shared/api'

import { getTopic, startTopicPracticeSession, startTopicStudySession } from '../api/topics'
import type { StartTopicStudyResult, TopicDetail } from '../types'
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

const studyTopic: TopicDetail = { ...topic, hskLevel: 1 }
const studyItems: [ServedItemView, ServedItemView] = [
  {
    no: 1,
    lexemeId: 201,
    type: 'STUDY_CARD',
    skill: 'VOCAB',
    prompt: {
      text: '猫',
      pinyin: 'māo',
      sinoViet: 'miêu',
      meaning: 'mèo',
      audioUrl: '/audio/cat.mp3',
      examples: [{ cn: '猫很可爱。', pinyin: 'Māo hěn kěài.', vi: 'Mèo rất đáng yêu.' }],
    },
    rules: { hasAudio: true, maxReplays: 3, minDurationMs: 0 },
  },
  {
    no: 2,
    lexemeId: 202,
    type: 'STUDY_CARD',
    skill: 'VOCAB',
    prompt: { text: '狗', pinyin: 'gǒu', sinoViet: '  ', meaning: 'chó', audioUrl: null },
    rules: { hasAudio: false, maxReplays: 3, minDurationMs: 0 },
  },
]

function studySession(overrides: Partial<SessionView> = {}) {
  return session({ items: studyItems, ...overrides })
}

function pendingResponse<T>() {
  let settle: ((value: T) => void) | undefined
  const promise = new Promise<T>((resolve) => {
    settle = resolve
  })
  return {
    promise,
    async resolve(value: T) {
      const complete = settle
      if (!complete) throw new Error('Response resolver is unavailable')
      await act(async () => {
        complete(value)
      })
    },
  }
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
    vi.mocked(startTopicPracticeSession)
      .mockReset()
      .mockResolvedValue(
        session({
          feedback: 'IMMEDIATE',
          items: [
            {
              no: 1,
              options: [{ key: 'A', text: 'mẹ' }],
              prompt: { text: '妈妈' },
              rules: {},
              skill: 'VOCAB',
              type: 'MEANING_CHOICE',
            },
          ],
          sessionId: 81,
          source: 'TOPIC_PRACTICE',
        }),
      )
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

  it('bước nhận diện mở lượt luyện thật và không hiện nội dung mastery', async () => {
    renderPage('/learn/topics/11/practice/recognition')

    expect(await screen.findByRole('button', { name: /mẹ/ })).toBeVisible()
    expect(startTopicPracticeSession).toHaveBeenCalledWith(11, { mode: 'RECOGNITION' })
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
    expect(await screen.findByRole('button', { name: /mẹ/ })).toBeVisible()
    expect(abandonSession).not.toHaveBeenCalled()
  })

  describe('T-15 topic study acceptance', () => {
    beforeEach(() => {
      vi.mocked(getTopic).mockResolvedValue(studyTopic)
      vi.mocked(startTopicStudySession).mockResolvedValue(studySession())
      vi.mocked(getSession).mockResolvedValue(studySession())
    })

    it('FR042_rendersAllFourApprovedStepLabels', async () => {
      renderPage()
      const navigation = await screen.findByRole('navigation', { name: 'Các bước học chủ đề' })
      for (const label of ['Học từ mới', 'Luyện nhận diện', 'Luyện nghe', 'Kiểm tra cuối chủ đề']) {
        expect(within(navigation).getByText(label)).toBeVisible()
      }
      expect(within(navigation).getAllByRole('listitem')).toHaveLength(4)
    })

    it.each([
      ['recognition', 'Luyện nhận diện', 'Hãy học từ mới trước khi luyện nhận diện.'],
      ['listening', 'Luyện nghe', 'Chủ đề chưa có âm thanh.'],
      ['test', 'Kiểm tra cuối chủ đề', 'Chủ đề cần ít nhất 4 từ để mở bài kiểm tra.'],
    ])('FR042_AT40_blocksTheStepAndExplainsItsReason_%s', async (step, label, reason) => {
      vi.mocked(getTopic).mockResolvedValue({
        ...studyTopic,
        actions: {
          ...studyTopic.actions,
          canPracticeRecognition: false,
          canPracticeListening: false,
          canTakeTest: false,
          testBlockedReason: 'TOO_FEW_WORDS',
        },
      })
      const path = step === 'test' ? 'test' : `practice/${step}`
      renderPage(`/learn/topics/11/${path}`)
      const navigation = await screen.findByRole('navigation', { name: 'Các bước học chủ đề' })
      const blocked = within(navigation).getByText(label).closest('[aria-disabled="true"]')
      expect(blocked).toHaveAttribute('title', reason)
      expect(screen.getByText('Bước này đang bị khoá')).toBeVisible()
      expect(startTopicStudySession).not.toHaveBeenCalled()
      expect(startTopicPracticeSession).not.toHaveBeenCalled()
    })

    it('FR042_trustsServerActionsWithoutRecomputingTestEligibility', async () => {
      vi.mocked(getTopic).mockResolvedValue({
        ...studyTopic,
        actions: { ...studyTopic.actions, canTakeTest: true },
      })
      renderPage()
      const navigation = await screen.findByRole('navigation', { name: 'Các bước học chủ đề' })
      expect(
        within(navigation).getByRole('link', { name: /Kiểm tra cuối chủ đề/ }),
      ).toHaveAttribute('href', '/learn/topics/11/test')
      expect(within(navigation).queryByText(/Học thêm/)).not.toBeInTheDocument()
    })

    it('FR023_rendersTheFullCardAndHidesEmptyFieldsOnTheNextCard', async () => {
      const user = userEvent.setup()
      renderPage()
      expect(await screen.findByText('猫')).toHaveAttribute('lang', 'zh-CN')
      for (const value of [
        'māo',
        'miêu',
        'mèo',
        '猫很可爱。',
        'Māo hěn kěài.',
        'Mèo rất đáng yêu.',
      ]) {
        expect(screen.getByText(value)).toBeVisible()
      }
      expect(screen.getByRole('button', { name: 'Nghe từ' })).toBeVisible()
      expect(screen.getByRole('button', { name: 'Tôi đã biết từ này' })).toBeVisible()
      await user.click(screen.getByRole('button', { name: 'Tiếp' }))
      expect(await screen.findByText('狗')).toBeVisible()
      expect(screen.getByText('gǒu')).toBeVisible()
      expect(screen.getByText('chó')).toBeVisible()
      expect(screen.queryByRole('button', { name: 'Nghe từ' })).not.toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: 'Ví dụ' })).not.toBeInTheDocument()
      expect(screen.queryByText('miêu')).not.toBeInTheDocument()
    })

    it('FR023_recordsOnlyOneAnswerWhileTheRequestIsPending', async () => {
      const user = userEvent.setup()
      const response = pendingResponse<AnswerResult>()
      vi.mocked(answerItem).mockReturnValueOnce(response.promise)
      renderPage()
      await user.dblClick(await screen.findByRole('button', { name: 'Tiếp' }))
      expect(answerItem).toHaveBeenCalledTimes(1)
      expect(screen.getByRole('button', { name: 'Tiếp' })).toBeDisabled()
      expect(finishSession).not.toHaveBeenCalled()
      await response.resolve({ no: 1, recorded: true })
      expect(await screen.findByText('狗')).toBeVisible()
      expect(answerItem).toHaveBeenCalledTimes(1)
    })

    it('FR023_resetsReplayCountForTheNextCardEvenWhenItsAudioUrlIsIdentical', async () => {
      const user = userEvent.setup()
      const play = vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined)
      vi.mocked(startTopicStudySession).mockResolvedValue(
        studySession({
          items: studyItems.map((item) => ({
            ...item,
            prompt: { ...item.prompt, audioUrl: '/audio/shared.mp3' },
            rules: { ...item.rules, hasAudio: true },
          })),
        }),
      )
      renderPage()
      await screen.findByText('猫')
      expect(play).not.toHaveBeenCalled()
      for (let count = 0; count < 3; count++) {
        await user.click(screen.getByRole('button', { name: 'Nghe từ' }))
      }
      expect(screen.getByRole('button', { name: 'Đã hết lượt nghe' })).toBeDisabled()
      await user.click(screen.getByRole('button', { name: 'Tiếp' }))
      expect(await screen.findByText('狗')).toBeVisible()
      expect(screen.getByText('Đã nghe 0/3 lần')).toBeVisible()
      await user.click(screen.getByRole('button', { name: 'Nghe từ' }))
      expect(screen.getByText('Đã nghe 1/3 lần')).toBeVisible()
      await user.click(screen.getByRole('button', { name: 'Tôi đã biết từ này' }))
      expect(answerItem).toHaveBeenNthCalledWith(
        2,
        71,
        expect.objectContaining({
          no: 2,
          answer: 'KNOWN',
          replayCount: 1,
        }),
      )
      expect(play).toHaveBeenCalledTimes(4)
    })

    it('FR023_restoresAnUnansweredCardWithZeroReplaysAfterReload', async () => {
      const user = userEvent.setup()
      vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined)
      const active = studySession({ items: [studyItems[0]] })
      vi.mocked(findActiveSession).mockResolvedValue(active)
      vi.mocked(getSession).mockResolvedValue(active)
      renderPage()
      await screen.findByText('猫')
      await user.click(screen.getByRole('button', { name: 'Nghe từ' }))
      expect(screen.getByText('Đã nghe 1/3 lần')).toBeVisible()
      cleanup()
      renderPage()
      expect(await screen.findByText('猫')).toBeVisible()
      expect(screen.getByText('Đã nghe 0/3 lần')).toBeVisible()
      expect(answerItem).not.toHaveBeenCalled()
      expect(startTopicStudySession).not.toHaveBeenCalled()
    })

    it('FR023_audioFailureStillCountsAsLearnedWithoutSendingAnAnswer', async () => {
      const user = userEvent.setup()
      vi.mocked(startTopicStudySession).mockResolvedValue(studySession({ items: [studyItems[0]] }))
      renderPage()
      await user.click(await screen.findByRole('button', { name: 'Không nghe được' }))
      expect(await screen.findByRole('heading', { name: 'Đã học 1 từ mới' })).toBeVisible()
      expect(answerItem).toHaveBeenCalledWith(
        71,
        expect.objectContaining({
          no: 1,
          audioFailed: true,
          replayCount: 0,
          durationMs: expect.any(Number),
        }),
      )
      expect(vi.mocked(answerItem).mock.calls[0]?.[1]).not.toHaveProperty('answer')
      expect(screen.getByText('Không chấm điểm')).toBeVisible()
      expect(finishSession).toHaveBeenCalledTimes(1)
    })

    it('FR024_retriesStartOnlyAfterTheLearnerClicksRetry', async () => {
      const user = userEvent.setup()
      vi.mocked(startTopicStudySession).mockRejectedValueOnce(
        new ApiError({
          errorCode: 'INTERNAL_ERROR',
          message: 'Chưa tạo được lượt học.',
          requestId: 'start-failed',
          status: 500,
        }),
      )
      renderPage()
      expect(await screen.findByText('Chưa tạo được lượt học.')).toBeVisible()
      await act(async () => {})
      expect(startTopicStudySession).toHaveBeenCalledTimes(1)
      await user.click(screen.getByRole('button', { name: 'Thử lại' }))
      expect(await screen.findByText('猫')).toBeVisible()
      expect(startTopicStudySession).toHaveBeenCalledTimes(2)
      expect(findActiveSession).toHaveBeenCalledTimes(2)
    })

    it.each([500, 429])('FR024_doesNotAutomaticallyRetryFailedFinish_%s', async (status) => {
      const user = userEvent.setup()
      const retry = pendingResponse<FinishResult>()
      const active = studySession({ items: [studyItems[0]] })
      vi.mocked(startTopicStudySession).mockResolvedValue(active)
      // A second request stays pending, so a regression cannot spin indefinitely on the old controller.
      vi.mocked(finishSession)
        .mockRejectedValueOnce(
          new ApiError({
            errorCode: status === 429 ? 'RATE_LIMITED' : 'INTERNAL_ERROR',
            message: 'Chưa kết thúc được lượt học.',
            requestId: 'finish-failed',
            status,
            extra: status === 429 ? { retryAfterSeconds: 45 } : {},
          }),
        )
        .mockReturnValue(retry.promise)
      const router = renderPage()
      await user.click(await screen.findByRole('button', { name: 'Tiếp' }))
      expect(await screen.findByText('Mã lỗi: finish-failed')).toBeVisible()
      await act(async () => {
        await router.navigate('/learn/topics/11/study?retry-check=1')
      })
      expect(finishSession).toHaveBeenCalledTimes(1)
      const recorded = { ...active, answered: [{ no: 1, answer: 'LEARNED' }] }
      vi.mocked(findActiveSession).mockResolvedValue(recorded)
      vi.mocked(getSession).mockResolvedValue(recorded)
      await user.click(screen.getByRole('button', { name: 'Thử lại' }))
      await waitFor(() => expect(finishSession).toHaveBeenCalledTimes(2))
      await retry.resolve(finishResult)
      expect(await screen.findByRole('heading', { name: 'Đã học 1 từ mới' })).toBeVisible()
      expect(finishSession).toHaveBeenCalledTimes(2)
      expect(answerItem).toHaveBeenCalledTimes(1)
      expect(startTopicStudySession).toHaveBeenCalledTimes(1)
    })

    it('FR024_keepsTheSummaryAndRecognitionLinkWhenTheRefreshedTopicHasNoNewWords', async () => {
      const user = userEvent.setup()
      vi.mocked(startTopicStudySession).mockResolvedValue(studySession({ items: [studyItems[0]] }))
      vi.mocked(getTopic)
        .mockResolvedValueOnce(studyTopic)
        .mockResolvedValue({
          ...studyTopic,
          learnedWords: 10,
          percent: 100,
          actions: { ...studyTopic.actions, canStudy: false },
        })
      renderPage()
      await user.click(await screen.findByRole('button', { name: 'Tiếp' }))
      expect(await screen.findByText('Đã học 10/10 từ')).toBeVisible()
      expect(screen.getByRole('heading', { name: 'Đã học 1 từ mới' })).toBeVisible()
      expect(screen.getByRole('link', { name: 'Luyện nhận diện' })).toHaveAttribute(
        'href',
        '/learn/topics/11/practice/recognition',
      )
      expect(getTopic).toHaveBeenCalledTimes(2)
      expect(finishSession).toHaveBeenCalledTimes(1)
      expect(startTopicStudySession).toHaveBeenCalledTimes(1)
    })

    it.each(['pending', 'no-new'])(
      'FR042_resetsTheStudyControllerWhenTopicIdChanges_%s',
      async (state) => {
        const response = pendingResponse<StartTopicStudyResult>()
        const nextTopic: TopicDetail = { ...studyTopic, id: 12, name: 'Nước 水' }
        const nextSession = studySession({
          refId: 12,
          sessionId: 72,
          items: [
            {
              ...studyItems[0],
              prompt: { text: '水', pinyin: 'shuǐ', meaning: 'nước' },
              rules: { hasAudio: false },
            },
          ],
        })
        vi.mocked(getTopic).mockImplementation((id) =>
          Promise.resolve(id === 11 ? studyTopic : nextTopic),
        )
        vi.mocked(startTopicStudySession).mockImplementation((id) =>
          id === 12
            ? Promise.resolve(nextSession)
            : state === 'pending'
              ? response.promise
              : Promise.resolve({ reason: 'NO_NEW_WORDS', session: null }),
        )
        const router = renderPage()
        if (state === 'no-new') await screen.findByText('Bạn đã học hết từ mới của chủ đề này')
        else await waitFor(() => expect(startTopicStudySession).toHaveBeenCalledWith(11))
        await act(async () => {
          await router.navigate('/learn/topics/12/study')
        })
        expect(await screen.findByText('水')).toBeVisible()
        expect(screen.getByRole('heading', { name: 'Nước 水' })).toBeVisible()
        if (state === 'pending') await response.resolve(studySession())
        expect(screen.getByText('水')).toBeVisible()
        expect(screen.queryByText('猫')).not.toBeInTheDocument()
        expect(startTopicStudySession).toHaveBeenCalledWith(12)
        expect(findActiveSession).toHaveBeenLastCalledWith(
          'TOPIC_STUDY',
          12,
          expect.any(AbortSignal),
        )
        expect(abandonSession).not.toHaveBeenCalled()
      },
    )

    it.each([false, true])(
      'FR024_resumesAnExistingSessionWhenCanStudyIsFalse_allAnswered_%s',
      async (allAnswered) => {
        const user = userEvent.setup()
        vi.mocked(getTopic).mockResolvedValue({
          ...studyTopic,
          learnedWords: 10,
          percent: 100,
          actions: { ...studyTopic.actions, canStudy: false },
        })
        const active = studySession({
          answered: allAnswered
            ? [
                { no: 1, answer: 'LEARNED' },
                { no: 2, answer: 'KNOWN' },
              ]
            : [{ no: 1, answer: 'LEARNED' }],
        })
        vi.mocked(findActiveSession).mockResolvedValue(active)
        vi.mocked(getSession).mockResolvedValue(active)
        renderPage()
        if (!allAnswered) {
          expect(await screen.findByText('狗')).toBeVisible()
          expect(await screen.findByRole('link', { name: /Học từ mới/ })).toHaveAttribute(
            'aria-current',
            'step',
          )
          await user.click(screen.getByRole('button', { name: 'Tôi đã biết từ này' }))
        }
        expect(await screen.findByRole('heading', { name: 'Đã học 2 từ mới' })).toBeVisible()
        expect(findActiveSession).toHaveBeenCalledWith('TOPIC_STUDY', 11, expect.any(AbortSignal))
        expect(getSession).toHaveBeenCalledWith(71, expect.any(AbortSignal))
        expect(startTopicStudySession).not.toHaveBeenCalled()
        expect(finishSession).toHaveBeenCalledTimes(1)
        expect(answerItem).toHaveBeenCalledTimes(allAnswered ? 0 : 1)
        expect(screen.getByRole('link', { name: 'Luyện nhận diện' })).toHaveAttribute(
          'href',
          '/learn/topics/11/practice/recognition',
        )
      },
    )

    it('FR024_checksActiveAndShowsNoNewWordsWithoutPostingWhenCanStudyIsFalse', async () => {
      vi.mocked(getTopic).mockResolvedValue({
        ...studyTopic,
        learnedWords: 10,
        percent: 100,
        actions: { ...studyTopic.actions, canStudy: false },
      })
      renderPage()
      expect(await screen.findByText('Bạn đã học hết từ mới của chủ đề này')).toBeVisible()
      expect(findActiveSession).toHaveBeenCalledWith('TOPIC_STUDY', 11, expect.any(AbortSignal))
      expect(startTopicStudySession).not.toHaveBeenCalled()
      expect(answerItem).not.toHaveBeenCalled()
      expect(finishSession).not.toHaveBeenCalled()
    })
  })
})
