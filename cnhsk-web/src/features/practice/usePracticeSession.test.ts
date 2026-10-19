import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  abandonSession,
  answerItem,
  findActiveSession,
  finishSession,
  getSession,
} from '@/features/practice/api'
import type { AnswerResult, SessionView } from '@/features/practice/types'
import { usePracticeSession } from '@/features/practice/usePracticeSession'
import { ApiError } from '@/shared/api'

vi.mock('@/features/practice/api', () => ({
  abandonSession: vi.fn(),
  answerItem: vi.fn(),
  findActiveSession: vi.fn(),
  finishSession: vi.fn(),
  getSession: vi.fn(),
}))

const findActiveSessionMock = vi.mocked(findActiveSession)
const getSessionMock = vi.mocked(getSession)
const answerItemMock = vi.mocked(answerItem)
const finishSessionMock = vi.mocked(finishSession)
const abandonSessionMock = vi.mocked(abandonSession)

function createSession(overrides: Partial<SessionView> = {}): SessionView {
  return {
    answered: [],
    expiresAt: '2026-10-20T12:30:00Z',
    feedback: 'IMMEDIATE',
    items: [],
    servedAt: '2026-10-20T12:00:00Z',
    sessionId: 42,
    source: 'TOPIC_PRACTICE',
    status: 'IN_PROGRESS',
    ...overrides,
  }
}

function apiError(errorCode: string, status: number): ApiError {
  return new ApiError({ errorCode, message: 'Thông báo kỹ thuật từ máy chủ', status })
}

beforeEach(() => {
  vi.clearAllMocks()
  findActiveSessionMock.mockResolvedValue(null)
  getSessionMock.mockResolvedValue(createSession())
  answerItemMock.mockResolvedValue({ no: 1 })
  finishSessionMock.mockResolvedValue({
    items: [],
    sessionId: 42,
    status: 'SUBMITTED',
    suspicious: false,
  })
  abandonSessionMock.mockResolvedValue(undefined)
})

describe('usePracticeSession', () => {
  it('restores an active session with the full session endpoint', async () => {
    const active = createSession({ sessionId: 7 })
    const restored = createSession({
      answered: [{ answer: 'B', correct: true, no: 1 }],
      sessionId: 7,
    })
    findActiveSessionMock.mockResolvedValue(active)
    getSessionMock.mockResolvedValue(restored)

    const { result } = renderHook(() =>
      usePracticeSession({ source: 'TOPIC_PRACTICE', refId: 9, start: vi.fn() }),
    )

    await waitFor(() => expect(result.current.state).toBe('ready'))
    expect(findActiveSessionMock).toHaveBeenCalledWith('TOPIC_PRACTICE', 9, expect.any(AbortSignal))
    expect(getSessionMock).toHaveBeenCalledWith(7, expect.any(AbortSignal))
    expect(result.current.results[1]?.correct).toBe(true)
  })

  it('waits for the screen to start when there is no active session', async () => {
    const created = createSession()
    const start = vi.fn().mockResolvedValue(created)
    const { result } = renderHook(() => usePracticeSession({ source: 'TOPIC_PRACTICE', start }))

    await waitFor(() => expect(result.current.state).toBe('idle'))
    expect(start).not.toHaveBeenCalled()

    await act(() => result.current.start())
    expect(start).toHaveBeenCalledOnce()
    expect(result.current.session).toEqual(created)
  })

  it('shows only the IMMEDIATE result returned by the server', async () => {
    const serverResult: AnswerResult = {
      correct: false,
      correctAnswer: 'B',
      explanation: 'Âm đầu đúng là zh.',
      no: 1,
    }
    answerItemMock.mockResolvedValue(serverResult)
    const { result } = renderHook(() =>
      usePracticeSession({
        source: 'TOPIC_PRACTICE',
        start: vi.fn().mockResolvedValue(createSession()),
      }),
    )
    await waitFor(() => expect(result.current.state).toBe('idle'))
    await act(() => result.current.start())

    await act(() => result.current.answer(1, { answer: 'A', durationMs: 800 }))

    expect(result.current.results[1]).toBe(serverResult)
    expect(result.current.drafts[1]).toBeUndefined()
  })

  it('lets a DEFERRED answer change without grading it on the client', async () => {
    const deferred = createSession({ feedback: 'DEFERRED' })
    const { result } = renderHook(() =>
      usePracticeSession({ source: 'TOPIC_TEST', start: vi.fn().mockResolvedValue(deferred) }),
    )
    await waitFor(() => expect(result.current.state).toBe('idle'))
    await act(() => result.current.start())

    await act(() => result.current.answer(1, { answer: 'A', durationMs: 400 }))
    await act(() => result.current.answer(1, { answer: 'B', durationMs: 700 }))

    expect(result.current.drafts[1]).toEqual({ answer: 'B', durationMs: 700, no: 1 })
    expect(result.current.results).toEqual({})
  })

  it.each([
    ['ANSWER_ALREADY_SUBMITTED', 409, 'Câu này đã được trả lời. Hãy tải lại lượt luyện.'],
    [
      'SESSION_ALREADY_SUBMITTED',
      409,
      'Lượt luyện này đã được nộp. Hãy xem kết quả hoặc bắt đầu lượt mới.',
    ],
    ['SESSION_EXPIRED', 422, 'Lượt luyện đã hết hạn. Hãy bắt đầu lượt mới.'],
  ])('handles %s without leaving the operation pending', async (errorCode, status, message) => {
    answerItemMock.mockRejectedValue(apiError(errorCode, status))
    const { result } = renderHook(() =>
      usePracticeSession({
        source: 'TOPIC_PRACTICE',
        start: vi.fn().mockResolvedValue(createSession()),
      }),
    )
    await waitFor(() => expect(result.current.state).toBe('idle'))
    await act(() => result.current.start())

    await act(() => result.current.answer(1, { answer: 'A', durationMs: 500 }))

    expect(result.current.error?.message).toBe(message)
    expect(result.current.pending).toBeNull()
    expect(result.current.state).toBe('ready')
  })

  it('sends the total audio play count, including zero when audio was not played', async () => {
    const { result } = renderHook(() =>
      usePracticeSession({
        source: 'TOPIC_PRACTICE',
        start: vi.fn().mockResolvedValue(createSession()),
      }),
    )
    await waitFor(() => expect(result.current.state).toBe('idle'))
    await act(() => result.current.start())

    await act(() => result.current.answer(1, { answer: 'A', durationMs: 500, replayCount: 3 }))
    await act(() => result.current.answer(2, { answer: 'B', durationMs: 600, replayCount: 0 }))

    expect(answerItemMock).toHaveBeenNthCalledWith(1, 42, {
      answer: 'A',
      durationMs: 500,
      no: 1,
      replayCount: 3,
    })
    expect(answerItemMock).toHaveBeenNthCalledWith(2, 42, {
      answer: 'B',
      durationMs: 600,
      no: 2,
      replayCount: 0,
    })
  })
})
