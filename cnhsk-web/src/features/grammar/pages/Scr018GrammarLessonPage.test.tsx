import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { answerItem, findActiveSession, finishSession, getSession } from '@/features/practice/api'
import type { FinishResult, SessionView } from '@/features/practice'
import { ApiError } from '@/shared/api'

import { getGrammarLesson, getGrammarPoints, startGrammarLesson } from '../api/grammar'
import type { GrammarLesson } from '../types'
import Scr018GrammarLessonPage from './Scr018GrammarLessonPage'

const { summaryResults } = vi.hoisted(() => ({ summaryResults: vi.fn() }))

vi.mock('@/features/practice', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/practice')>()
  return {
    ...actual,
    SessionSummary: (props: Parameters<typeof actual.SessionSummary>[0]) => {
      summaryResults(props.result)
      return <actual.SessionSummary {...props} />
    },
  }
})

vi.mock('../api/grammar', () => ({
  getGrammarLesson: vi.fn(),
  getGrammarPoints: vi.fn(),
  startGrammarLesson: vi.fn(),
}))

vi.mock('@/features/practice/api', () => ({
  abandonSession: vi.fn(),
  answerItem: vi.fn(),
  findActiveSession: vi.fn(),
  finishSession: vi.fn(),
  getSession: vi.fn(),
}))

const getGrammarLessonMock = vi.mocked(getGrammarLesson)
const getGrammarPointsMock = vi.mocked(getGrammarPoints)
const startGrammarLessonMock = vi.mocked(startGrammarLesson)
const findActiveSessionMock = vi.mocked(findActiveSession)
const getSessionMock = vi.mocked(getSession)
const answerItemMock = vi.mocked(answerItem)
const finishSessionMock = vi.mocked(finishSession)

const lesson: GrammarLesson = {
  bestPercent: null,
  commonMistake: null,
  examples: [{ cn: '我是昨天来的。', pinyin: 'Wǒ shì zuótiān lái de.', vi: 'Tôi đến hôm qua.' }],
  exerciseCount: 2,
  explanation: 'Giải thích bài học.',
  hskLevel: 2,
  id: 21,
  learned: false,
  structure: 'S + 是 + … + 的',
  text: '是……的',
  titleVi: 'Nhấn mạnh chi tiết',
}

function session(status: SessionView['status'] = 'IN_PROGRESS'): SessionView {
  return {
    answered: [{ answer: 'UNDERSTOOD', no: 1 }],
    expiresAt: '2026-10-20T12:00:00Z',
    feedback: 'IMMEDIATE',
    items: [
      {
        no: 1,
        prompt: { text: 'Nội dung đã đọc' },
        rules: {},
        skill: 'GRAMMAR',
        type: 'LESSON_READ',
      },
      {
        no: 2,
        options: [
          { key: 'A', text: '是' },
          { key: 'B', text: '在' },
        ],
        prompt: { sentence: '他___坐飞机来的。' },
        rules: { minDurationMs: 800 },
        skill: 'GRAMMAR',
        type: 'GRAMMAR_CHOICE',
      },
      {
        no: 3,
        options: [
          { key: 'A', text: '的' },
          { key: 'B', text: '了' },
        ],
        prompt: { sentence: '我是昨天来___。' },
        rules: { minDurationMs: 800 },
        skill: 'GRAMMAR',
        type: 'GRAMMAR_CHOICE',
      },
    ],
    refId: 21,
    servedAt: '2026-10-20T10:00:00Z',
    sessionId: 101,
    source: 'GRAMMAR_LESSON',
    status,
  }
}

function finish(extra: Record<string, unknown>): FinishResult {
  return {
    extra,
    items: [],
    sessionId: 101,
    status: 'SUBMITTED',
    suspicious: false,
  }
}

const READ_ANSWER = { answer: 'UNDERSTOOD', counted: false, no: 1 }

function submitted(
  answered: SessionView['answered'],
  items: SessionView['items'] = session().items,
): SessionView {
  return { ...session('SUBMITTED'), answered, items }
}

function lastSummary() {
  return summaryResults.mock.calls.at(-1)?.[0] as FinishResult
}

async function chooseFirstOption(user: ReturnType<typeof userEvent.setup>) {
  const choices = await screen.findByRole('group', { name: 'Các phương án' })
  await user.click(within(choices).getAllByRole('button')[0]!)
}

function renderPage(path = '/learn/grammar/21') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<Scr018GrammarLessonPage />} path="/learn/grammar/:id" />
      </Routes>
    </MemoryRouter>,
  )
}

describe('SCR-018 Grammar Lesson', () => {
  afterEach(() => vi.restoreAllMocks())

  beforeEach(() => {
    summaryResults.mockClear()
    getGrammarLessonMock.mockReset().mockResolvedValue(lesson)
    getGrammarPointsMock.mockReset().mockResolvedValue([lesson])
    startGrammarLessonMock.mockReset().mockResolvedValue(session())
    findActiveSessionMock.mockReset().mockResolvedValue(null)
    getSessionMock.mockReset().mockResolvedValue(session())
    answerItemMock.mockReset().mockResolvedValue({
      accepted: true,
      correct: true,
      correctAnswer: 'A',
      counted: true,
      explanation: 'Lời giải từ máy chủ.',
      no: 2,
    })
    finishSessionMock.mockReset().mockResolvedValue(
      finish({
        answered: 2,
        correct: 2,
        exercisesTotal: 2,
        skippedExercises: false,
      }),
    )
  })

  it('đo readDurationMs bằng performance.now khi bấm Đã hiểu', async () => {
    let now = 100
    vi.spyOn(performance, 'now').mockImplementation(() => now)
    const user = userEvent.setup()
    renderPage()

    const understood = await screen.findByRole('button', { name: 'Đã hiểu' })
    now = 1600
    await user.click(understood)

    expect(startGrammarLessonMock).toHaveBeenCalledWith(21, 1500)
  })

  it('khôi phục lượt dở và không gọi POST lần nữa', async () => {
    findActiveSessionMock.mockResolvedValue(session())
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Tiếp tục bài tập' }))

    expect(startGrammarLessonMock).not.toHaveBeenCalled()
    expect(await screen.findByLabelText('Tiến độ lượt luyện: câu 1 trên 2')).toBeVisible()
  })

  it('không đếm LESSON_READ như một câu hỏi', async () => {
    findActiveSessionMock.mockResolvedValue(session())
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Tiếp tục bài tập' }))

    expect(await screen.findByText('Câu 1/2')).toBeVisible()
    expect(screen.queryByText('Nội dung đã đọc')).not.toBeInTheDocument()
  })

  it('hiện lời giải ngay rồi chuyển bằng nút Câu tiếp', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Đã hiểu' }))
    const choices = await screen.findByRole('group', { name: 'Các phương án' })
    await user.click(within(choices).getAllByRole('button')[0]!)

    expect(await screen.findByText('Lời giải từ máy chủ.')).toBeVisible()
    await user.click(screen.getByRole('button', { name: /Câu tiếp/ }))
    expect(await screen.findByText('Câu 2/2')).toBeVisible()
  })

  it('hiện kết quả E1 ngay và không gọi finish lần nữa', async () => {
    startGrammarLessonMock.mockResolvedValue({
      finish: finish({
        answered: 0,
        correct: 0,
        exercisesTotal: 0,
        skippedExercises: false,
      }),
      session: session('SUBMITTED'),
    })
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Đã hiểu' }))

    expect(await screen.findByRole('heading', { name: 'Đã ghi nhận bài học' })).toBeVisible()
    expect(screen.getByText('Điểm này chưa có bài tập.')).toBeVisible()
    expect(finishSessionMock).not.toHaveBeenCalled()
  })

  it('chỉ bỏ qua bài tập sau khi xác nhận qua ConfirmPanel', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Đã hiểu' }))

    await user.click(await screen.findByRole('button', { name: 'Bỏ qua bài tập' }))
    expect(screen.getByRole('dialog', { name: 'Bỏ qua phần bài tập?' })).toBeVisible()
    expect(finishSessionMock).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Bỏ qua và ghi nhận bài học' }))
    expect(finishSessionMock).toHaveBeenCalledWith(101, undefined)
  })

  it('hiện nút Học điểm tiếp theo khi còn điểm cùng cấp', async () => {
    getGrammarPointsMock.mockResolvedValue([
      lesson,
      { ...lesson, id: 22, text: '越来越……', titleVi: 'Mức độ tăng dần' },
    ])
    startGrammarLessonMock.mockResolvedValue({
      finish: finish({ exercisesTotal: 0, skippedExercises: false }),
      session: session('SUBMITTED'),
    })
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Đã hiểu' }))

    expect(await screen.findByRole('link', { name: /Học điểm tiếp theo/ })).toHaveAttribute(
      'href',
      '/learn/grammar/22',
    )
    expect(getGrammarPointsMock).toHaveBeenCalledWith(2, expect.any(AbortSignal))
  })

  it('ẩn nút Học điểm tiếp theo khi đang ở điểm cuối', async () => {
    startGrammarLessonMock.mockResolvedValue({
      finish: finish({ exercisesTotal: 0, skippedExercises: false }),
      session: session('SUBMITTED'),
    })
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Đã hiểu' }))
    await waitFor(() => expect(getGrammarPointsMock).toHaveBeenCalled())

    expect(screen.queryByRole('link', { name: /Học điểm tiếp theo/ })).not.toBeInTheDocument()
  })

  it('hiện empty state cho lỗi GRAMMAR_POINT_NOT_FOUND', async () => {
    getGrammarLessonMock.mockRejectedValue(
      new ApiError({
        errorCode: 'GRAMMAR_POINT_NOT_FOUND',
        message: 'Không tìm thấy.',
        requestId: 'req-404',
        status: 404,
      }),
    )
    renderPage()

    expect(
      await screen.findByRole('heading', { name: 'Không tìm thấy điểm ngữ pháp' }),
    ).toBeVisible()
    expect(screen.getByRole('link', { name: 'Quay lại danh sách' })).toHaveAttribute(
      'href',
      '/learn/grammar',
    )
  })

  it.each([null, '', '   '])(
    'FR-006 hiện đủ nội dung bài và ẩn Lỗi thường gặp khi rỗng (%j)',
    async (commonMistake) => {
      getGrammarLessonMock.mockResolvedValue({ ...lesson, commonMistake })
      renderPage()

      expect(await screen.findByRole('heading', { name: 'Cấu trúc' })).toBeVisible()
      expect(screen.getByText('HSK 2')).toBeVisible()
      expect(screen.getByText('是……的')).toBeVisible()
      expect(screen.getByText('Nhấn mạnh chi tiết')).toBeVisible()
      expect(screen.getByText('S + 是 + … + 的')).toBeVisible()
      expect(screen.getByRole('heading', { name: 'Giải thích' })).toBeVisible()
      expect(screen.getByText('Giải thích bài học.')).toBeVisible()
      expect(screen.getByRole('heading', { name: 'Ví dụ' })).toBeVisible()
      expect(screen.getByText('我是昨天来的。')).toBeVisible()
      expect(screen.getByText('Wǒ shì zuótiān lái de.')).toBeVisible()
      expect(screen.getByText('Tôi đến hôm qua.')).toBeVisible()
      expect(screen.queryByRole('heading', { name: 'Lỗi thường gặp' })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Đã hiểu' })).toBeVisible()
    },
  )

  it('FR-006 hiện Lỗi thường gặp khi có nội dung', async () => {
    getGrammarLessonMock.mockResolvedValue({ ...lesson, commonMistake: 'Không bỏ 的 ở cuối câu.' })
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Lỗi thường gặp' })).toBeVisible()
    expect(screen.getByText('Không bỏ 的 ở cuối câu.')).toBeVisible()
  })

  it('FR-008 không hiện Lần ôn tiếp theo khi đọc và khi kết thúc', async () => {
    startGrammarLessonMock.mockResolvedValue({
      finish: finish({ exercisesTotal: 0, skippedExercises: false }),
      session: session('SUBMITTED'),
    })
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Đã hiểu' }))
    expect(await screen.findByRole('heading', { name: 'Đã ghi nhận bài học' })).toBeVisible()
    expect(screen.queryByText(/Lần ôn tiếp theo/)).not.toBeInTheDocument()
  })

  it.each([
    [2, 'Đúng 2/2'],
    [1, 'Đúng 1/2'],
  ])('FR-018 làm hết bài tập (%i đúng) thì hiện "%s"', async (correct, title) => {
    finishSessionMock.mockResolvedValue(
      finish({
        answered: 2,
        correct,
        exercisesTotal: 2,
        percent: correct * 50,
        skippedExercises: false,
      }),
    )
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Đã hiểu' }))
    await chooseFirstOption(user)
    await user.click(await screen.findByRole('button', { name: /Câu tiếp/ }))
    expect(await screen.findByText('Câu 2/2')).toBeVisible()
    await chooseFirstOption(user)
    await user.click(await screen.findByRole('button', { name: /Câu tiếp/ }))

    expect(await screen.findByRole('heading', { name: title })).toBeVisible()
    expect(screen.queryByText('Điểm này chưa có bài tập.')).not.toBeInTheDocument()
    expect(finishSessionMock).toHaveBeenCalledTimes(1)
  })

  it('FR-017 FR-018 bỏ qua sau xác nhận thì hiện Đã ghi nhận bài học', async () => {
    finishSessionMock.mockResolvedValue(
      finish({
        answered: 0,
        correct: 0,
        exercisesTotal: 2,
        percent: null,
        skippedExercises: true,
      }),
    )
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Đã hiểu' }))
    await user.click(await screen.findByRole('button', { name: 'Bỏ qua bài tập' }))
    await user.click(screen.getByRole('button', { name: 'Bỏ qua và ghi nhận bài học' }))

    expect(await screen.findByRole('heading', { name: 'Đã ghi nhận bài học' })).toBeVisible()
    expect(screen.queryByText('Điểm này chưa có bài tập.')).not.toBeInTheDocument()
  })

  it('khôi phục lượt đã nộp ở nơi khác và dựng lại kết quả kèm percent', async () => {
    answerItemMock.mockRejectedValue(
      new ApiError({
        errorCode: 'SESSION_ALREADY_SUBMITTED',
        message: 'Lượt luyện đã kết thúc.',
        requestId: 'req-409',
        status: 409,
      }),
    )
    getSessionMock.mockResolvedValue(
      submitted([
        READ_ANSWER,
        { answer: 'A', correct: true, correctAnswer: 'A', counted: true, no: 2 },
        { answer: 'A', correct: true, correctAnswer: 'A', counted: true, no: 3 },
      ]),
    )
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Đã hiểu' }))
    await chooseFirstOption(user)

    expect(await screen.findByRole('heading', { name: 'Đúng 2/2' })).toBeVisible()
    expect(getSessionMock).toHaveBeenCalledWith(101)
    expect(screen.getByText('2/2')).toBeVisible()
    expect(screen.queryByText('Điểm này chưa có bài tập.')).not.toBeInTheDocument()
    expect(lastSummary()).toMatchObject({
      extra: { answered: 2, correct: 2, exercisesTotal: 2, percent: 100, skippedExercises: false },
      maxScore: 2,
      percent: 100,
      score: 2,
    })
    expect(lastSummary().extra).not.toHaveProperty('firstTimeLearned')
    expect(lastSummary().items.map((item) => item.no)).toEqual([2, 3])
    expect(finishSessionMock).not.toHaveBeenCalled()
  })

  it('khôi phục lượt đã bỏ qua một câu thì hiện Đã ghi nhận bài học', async () => {
    findActiveSessionMock.mockResolvedValue(session())
    getSessionMock.mockResolvedValue(
      submitted([
        READ_ANSWER,
        { answer: 'A', correct: true, correctAnswer: 'A', counted: true, no: 2 },
        { answer: null, correct: null, counted: false, no: 3 },
      ]),
    )
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Đã ghi nhận bài học' })).toBeVisible()
    expect(screen.queryByText('Điểm này chưa có bài tập.')).not.toBeInTheDocument()
    expect(lastSummary()).toMatchObject({
      extra: { answered: 1, correct: 1, exercisesTotal: 2, percent: 100, skippedExercises: true },
      maxScore: 1,
      percent: 100,
      score: 1,
    })
  })

  it('khôi phục lượt không có bài tập vẫn hiện Điểm này chưa có bài tập', async () => {
    findActiveSessionMock.mockResolvedValue(session())
    getSessionMock.mockResolvedValue(submitted([READ_ANSWER], [session().items[0]!]))
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Đã ghi nhận bài học' })).toBeVisible()
    expect(screen.getByText('Điểm này chưa có bài tập.')).toBeVisible()
    expect(screen.getByText('Không chấm điểm')).toBeVisible()
    expect(screen.queryByText('0/0')).not.toBeInTheDocument()
    expect(lastSummary()).toMatchObject({
      extra: { answered: 0, correct: 0, exercisesTotal: 0, percent: null, skippedExercises: false },
      maxScore: null,
      percent: null,
      score: null,
    })
  })

  it('POST trả lượt dở thì nhảy tới câu chưa trả lời đầu tiên', async () => {
    startGrammarLessonMock.mockResolvedValue({
      ...session(),
      answered: [
        READ_ANSWER,
        { answer: 'A', correct: true, correctAnswer: 'A', counted: true, no: 2 },
      ],
    })
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Đã hiểu' }))

    expect(await screen.findByText('Câu 2/2')).toBeVisible()
    expect(screen.getByText('我是昨天来___。')).toBeVisible()
    expect(finishSessionMock).not.toHaveBeenCalled()
  })
})
