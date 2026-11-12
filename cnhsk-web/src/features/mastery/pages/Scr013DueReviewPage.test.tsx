import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { StrictMode } from 'react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { AnswerRequest, SessionView } from '@/features/practice/types'

import { getReviewSummary, startReviewSession } from '../api/reviews'
import Scr013DueReviewPage from './Scr013DueReviewPage'

vi.mock('@/features/practice/api', () => ({
  abandonSession: vi.fn(),
  answerItem: vi.fn(),
  findActiveSession: vi.fn(),
  finishSession: vi.fn(),
  getSession: vi.fn(),
}))
vi.mock('../api/reviews', () => ({ getReviewSummary: vi.fn(), startReviewSession: vi.fn() }))

import { abandonSession, answerItem, findActiveSession, finishSession, getSession } from '@/features/practice/api'

const mocks = {
  abandon: vi.mocked(abandonSession),
  answer: vi.mocked(answerItem),
  findActive: vi.mocked(findActiveSession),
  finish: vi.mocked(finishSession),
  get: vi.mocked(getSession),
  summary: vi.mocked(getReviewSummary),
  start: vi.mocked(startReviewSession),
}

function choice(no: number) {
  return {
    no,
    type: 'MEANING_CHOICE',
    skill: 'VOCAB' as const,
    lexemeId: 200 + no,
    prompt: { text: '你好吗' },
    options: [
      { key: 'A', text: 'Bạn khỏe không' },
      { key: 'B', text: 'Tạm biệt' },
    ],
    rules: {},
  }
}

function flashcard(no: number) {
  return {
    no,
    type: 'FLASHCARD_RATING',
    skill: 'GRAMMAR' as const,
    lexemeId: 300 + no,
    prompt: {
      text: '我学习。',
      pinyin: 'Wǒ xuéxí.',
      sinoViet: 'Ngã học tập.',
      meaning: 'Tôi học.',
      examples: [{ cn: '我每天学习。', vi: 'Tôi học mỗi ngày.' }],
    },
    rules: {},
  }
}

function reviewSession(
  items: SessionView['items'] = [choice(1)],
  answered: SessionView['answered'] = [],
): SessionView {
  return {
    sessionId: 88,
    source: 'DUE_REVIEW',
    refId: null,
    feedback: 'IMMEDIATE',
    status: 'IN_PROGRESS',
    servedAt: '2026-11-12T00:00:00Z',
    expiresAt: '2026-11-12T02:00:00Z',
    items,
    answered,
  }
}

function CurrentPath() {
  const location = useLocation()
  return <output data-testid="location">{location.pathname + location.search}</output>
}

function renderPage(path = '/learn/review') {
  return render(
    <StrictMode>
      <MemoryRouter initialEntries={[path]}>
        <CurrentPath />
        <Routes>
          <Route element={<Scr013DueReviewPage />} path="/learn/review" />
          <Route element={<p>Trang học tập</p>} path="/learn" />
        </Routes>
      </MemoryRouter>
    </StrictMode>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.findActive.mockResolvedValue(null)
  mocks.get.mockResolvedValue(reviewSession())
  mocks.answer.mockImplementation(async (_sessionId: number, request: AnswerRequest) => ({
    no: request.no,
    correct: request.skipped ? null : request.answer === 'A',
    counted: !request.skipped,
  }))
  mocks.finish.mockImplementation(async (sessionId) => ({
    sessionId,
    status: 'SUBMITTED',
    suspicious: false,
    items: [],
  }))
  mocks.abandon.mockResolvedValue(undefined)
  mocks.summary.mockResolvedValue({ dueCount: 0, nextDueAt: '2026-11-13T02:30:00Z' })
  mocks.start.mockResolvedValue(reviewSession())
})

describe('SCR-013 Due Review (FR-026, AT-26)', () => {
  it('starts once under StrictMode, restores no duplicate start, and sends an empty default body', async () => {
    renderPage()

    expect(await screen.findByText('你好吗')).toBeVisible()
    expect(mocks.start).toHaveBeenCalledTimes(1)
    expect(mocks.start).toHaveBeenCalledWith({})
  })

  it('restores an active session without posting a new one', async () => {
    const active = reviewSession([choice(1)], [{ no: 1, answer: 'A', correct: true, counted: true }])
    mocks.findActive.mockResolvedValue(active)
    mocks.get.mockResolvedValue(active)

    renderPage()

    expect(await screen.findByRole('heading', { name: 'Đã hoàn thành ôn tập' })).toBeVisible()
    expect(mocks.start).not.toHaveBeenCalled()
    expect(mocks.get).toHaveBeenCalledWith(active.sessionId, expect.any(AbortSignal))
  })

  it('shows NOTHING_DUE as empty and starts ahead review with ahead=true', async () => {
    mocks.start
      .mockResolvedValueOnce({ session: null, reason: 'NOTHING_DUE' })
      .mockResolvedValueOnce(reviewSession())

    renderPage()

    expect(await screen.findByText('Chưa có mục đến hạn')).toBeVisible()
    expect(mocks.start).toHaveBeenNthCalledWith(1, {})
    fireEvent.click(screen.getByRole('button', { name: 'Ôn trước' }))

    expect(await screen.findByText('你好吗')).toBeVisible()
    expect(mocks.start).toHaveBeenNthCalledWith(2, { ahead: true })
    expect(screen.getByTestId('location')).toHaveTextContent('/learn/review?ahead=true')
  })

  it('reads ahead=true from the route and forwards it in the start request', async () => {
    mocks.start.mockResolvedValue({ session: null, reason: 'NOTHING_DUE' })

    renderPage('/learn/review?ahead=true')

    expect(await screen.findByText('Chưa có mục đến hạn')).toBeVisible()
    expect(mocks.start).toHaveBeenCalledTimes(1)
    expect(mocks.start).toHaveBeenCalledWith({ ahead: true })
  })

  it('shows immediate choice feedback, no rating buttons, and decrements remaining after success', async () => {
    mocks.start.mockResolvedValue(reviewSession([choice(1), choice(2)]))

    renderPage()

    expect(await screen.findByText('2 mục còn lại')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Dễ' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Quên' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Bạn khỏe không/ }))
    expect(await screen.findByText('Chính xác')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Bỏ qua' })).not.toBeInTheDocument()
    expect(mocks.answer).toHaveBeenCalledTimes(1)
    expect(mocks.answer).toHaveBeenCalledWith(88, expect.objectContaining({ no: 1, answer: 'A' }))
    expect(await screen.findByText('1 mục còn lại')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Tiếp' }))
    fireEvent.click(screen.getByRole('button', { name: /Bạn khỏe không/ }))
    expect(await screen.findByText('0 mục còn lại')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Kết thúc ôn tập' }))
    expect(mocks.answer).toHaveBeenCalledWith(88, expect.objectContaining({ no: 1, answer: 'A' }))
    expect(mocks.answer).toHaveBeenCalledWith(88, expect.objectContaining({ no: 2, answer: 'A' }))
  })

  it('sends each flashcard rating and advances after successful recording', async () => {
    mocks.start.mockResolvedValue(reviewSession([flashcard(1), flashcard(2), flashcard(3), flashcard(4)]))

    renderPage()

    for (const [label, answer] of [
      ['Quên', 'AGAIN'],
      ['Khó', 'HARD'],
      ['Được', 'GOOD'],
      ['Dễ', 'EASY'],
    ] as const) {
      expect(await screen.findByText('我学习。')).toBeVisible()
      expect(screen.queryByText('Tôi học.')).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Quên' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Hiện đáp án' })).toBeVisible()
      fireEvent.click(screen.getByRole('button', { name: 'Hiện đáp án' }))
      expect(await screen.findByText('Wǒ xuéxí.')).toBeVisible()
      expect(screen.getByText('Ngã học tập.')).toBeVisible()
      expect(screen.getByText('Tôi học.')).toBeVisible()
      expect(screen.getByText(/Tôi học mỗi ngày\./)).toBeVisible()
      expect(screen.getByRole('button', { name: 'Quên' })).toBeVisible()
      expect(screen.getByRole('button', { name: 'Khó' })).toBeVisible()
      expect(screen.getByRole('button', { name: 'Được' })).toBeVisible()
      expect(screen.getByRole('button', { name: 'Dễ' })).toBeVisible()
      fireEvent.click(screen.getByRole('button', { name: label }))
      await waitFor(() => expect(mocks.answer).toHaveBeenCalledWith(
        88,
        expect.objectContaining({ answer, durationMs: expect.any(Number) }),
      ))
    }

    expect(mocks.answer).toHaveBeenCalledTimes(4)
    expect(mocks.finish).toHaveBeenCalledTimes(1)
  })

  it('pauses by returning to /learn without abandoning or finishing the session', async () => {
    renderPage()

    expect(await screen.findByText('你好吗')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Tạm dừng' }))

    expect(await screen.findByText('Trang học tập')).toBeVisible()
    expect(screen.getByTestId('location')).toHaveTextContent('/learn')
    expect(mocks.abandon).not.toHaveBeenCalled()
    expect(mocks.finish).not.toHaveBeenCalled()
  })

  it('does not count skipped choices as incorrect and preserves completion if next-due lookup fails', async () => {
    mocks.start.mockResolvedValue(reviewSession([choice(1)]))
    mocks.summary.mockRejectedValue(new Error('summary unavailable'))

    renderPage()

    expect(await screen.findByText('你好吗')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Bỏ qua' }))

    expect(await screen.findByRole('heading', { name: 'Đã hoàn thành ôn tập' })).toBeVisible()
    expect(screen.getByText('Đã xử lý 1 mục')).toBeVisible()
    expect(screen.getByText('Số câu chọn đúng: 0 / 0')).toBeVisible()
    expect(await screen.findByText('Không tải được hạn ôn kế tiếp')).toBeVisible()
    expect(mocks.finish).toHaveBeenCalledTimes(1)
  })

  it('shows the next due time using the shared Vietnam formatter after finish', async () => {
    mocks.start.mockResolvedValue(reviewSession([choice(1)]))

    renderPage()
    expect(await screen.findByText('你好吗')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: /Bạn khỏe không/ }))
    expect(await screen.findByText('Chính xác')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Kết thúc ôn tập' }))

    expect(await screen.findByText('Lần ôn tiếp theo: 09:30 13 thg 11, 2026')).toBeVisible()
    expect(mocks.finish).toHaveBeenCalledTimes(1)
    expect(mocks.summary).toHaveBeenCalledTimes(1)
  })
})
