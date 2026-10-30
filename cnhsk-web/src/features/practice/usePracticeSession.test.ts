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
  it('FR045_restores only the fields present in a DEFERRED snapshot', async () => {
    const restored = createSession({
      feedback: 'DEFERRED',
      source: 'TOPIC_TEST',
      answered: [{ no: 1, answer: 'A' }, { no: 2 }],
    })
    findActiveSessionMock.mockResolvedValue(restored)
    getSessionMock.mockResolvedValue(restored)
    const { result } = renderHook(() =>
      usePracticeSession({ source: 'TOPIC_TEST', start: vi.fn() }),
    )

    await waitFor(() => expect(result.current.state).toBe('ready'))

    expect(result.current.drafts).toEqual({ 1: { no: 1, answer: 'A' }, 2: { no: 2 } })
    expect(result.current.results).toEqual({})
    expect(answerItemMock).not.toHaveBeenCalled()
    expect(finishSessionMock).not.toHaveBeenCalled()
  })

  it('FR045_finishes after reload without resending restored answers or invented evidence', async () => {
    const restored = createSession({
      feedback: 'DEFERRED',
      source: 'TOPIC_TEST',
      answered: [{ no: 1, answer: 'A' }],
    })
    findActiveSessionMock.mockResolvedValue(restored)
    getSessionMock.mockResolvedValue(restored)
    const { result } = renderHook(() =>
      usePracticeSession({ source: 'TOPIC_TEST', start: vi.fn() }),
    )
    await waitFor(() => expect(result.current.state).toBe('ready'))

    // The existing screen sends all displayed drafts with newly measured durations.
    await act(() => result.current.finish([{ no: 1, answer: 'A', durationMs: 400 }]))

    expect(finishSessionMock).toHaveBeenCalledOnce()
    expect(finishSessionMock).toHaveBeenCalledWith(42, undefined)
    expect(answerItemMock).not.toHaveBeenCalled()
  })

  it('FR045_keeps complete evidence for answers edited after reload and excludes old drafts', async () => {
    const restored = createSession({
      feedback: 'DEFERRED',
      source: 'TOPIC_TEST',
      answered: [
        { no: 1, answer: 'A' },
        { no: 2, answer: 'A' },
      ],
    })
    findActiveSessionMock.mockResolvedValue(restored)
    getSessionMock.mockResolvedValue(restored)
    const { result } = renderHook(() =>
      usePracticeSession({ source: 'TOPIC_TEST', start: vi.fn() }),
    )
    await waitFor(() => expect(result.current.state).toBe('ready'))
    const edited = { no: 2, answer: 'B', durationMs: 1250, replayCount: 2 }

    await act(() => result.current.answer(edited.no, edited))
    expect(answerItemMock).toHaveBeenCalledOnce()
    expect(answerItemMock).toHaveBeenCalledWith(42, edited)
    expect(result.current.drafts[2]).toEqual(edited)
    await act(() => result.current.finish([{ no: 1, answer: 'A', durationMs: 500 }, edited]))

    expect(finishSessionMock).toHaveBeenCalledOnce()
    expect(finishSessionMock).toHaveBeenCalledWith(42, { answers: [edited] })
  })

  it('FR045_includes an unsaved edit passed directly to finish after reload', async () => {
    const restored = createSession({
      feedback: 'DEFERRED',
      source: 'TOPIC_TEST',
      answered: [{ no: 1, answer: 'A' }],
    })
    findActiveSessionMock.mockResolvedValue(restored)
    getSessionMock.mockResolvedValue(restored)
    const { result } = renderHook(() =>
      usePracticeSession({ source: 'TOPIC_TEST', start: vi.fn() }),
    )
    await waitFor(() => expect(result.current.state).toBe('ready'))
    const edited = { no: 1, answer: 'B', durationMs: 900 }

    await act(() => result.current.finish([edited]))

    expect(finishSessionMock).toHaveBeenCalledOnce()
    expect(finishSessionMock).toHaveBeenCalledWith(42, { answers: [edited] })
  })

  it('FR045_does not invent acceptance or grading fields when restoring RECORD items', async () => {
    const restored = createSession({
      feedback: 'RECORD',
      source: 'TOPIC_STUDY',
      answered: [{ no: 1 }, { no: 2, correct: null }],
    })
    findActiveSessionMock.mockResolvedValue(restored)
    getSessionMock.mockResolvedValue(restored)
    const { result } = renderHook(() =>
      usePracticeSession({ source: 'TOPIC_STUDY', start: vi.fn() }),
    )

    await waitFor(() => expect(result.current.state).toBe('ready'))

    expect(result.current.results).toEqual({ 1: { no: 1 }, 2: { no: 2, correct: null } })
  })

  it('FR045_keeps a real answer submitted again by the learner after reload', async () => {
    const restored = createSession({
      feedback: 'DEFERRED',
      source: 'TOPIC_TEST',
      answered: [{ no: 1, answer: 'A' }],
    })
    findActiveSessionMock.mockResolvedValue(restored)
    getSessionMock.mockResolvedValue(restored)
    const { result } = renderHook(() =>
      usePracticeSession({ source: 'TOPIC_TEST', start: vi.fn() }),
    )
    await waitFor(() => expect(result.current.state).toBe('ready'))
    const edited = { no: 1, answer: 'A', durationMs: 1500, replayCount: 1 }

    await act(() => result.current.answer(edited.no, edited))
    await act(() => result.current.finish([edited]))

    expect(result.current.drafts[1]).toEqual(edited)
    expect(finishSessionMock).toHaveBeenCalledWith(42, { answers: [edited] })
  })

  it('FR045_preserves a newly reported audio failure when finishing a restored draft', async () => {
    const restored = createSession({
      feedback: 'DEFERRED',
      source: 'TOPIC_TEST',
      answered: [{ no: 1, answer: 'A' }],
    })
    findActiveSessionMock.mockResolvedValue(restored)
    getSessionMock.mockResolvedValue(restored)
    const { result } = renderHook(() =>
      usePracticeSession({ source: 'TOPIC_TEST', start: vi.fn() }),
    )
    await waitFor(() => expect(result.current.state).toBe('ready'))
    const edited = { no: 1, audioFailed: true, durationMs: 1500, replayCount: 1 }

    await act(() => result.current.finish([edited]))

    expect(finishSessionMock).toHaveBeenCalledWith(42, { answers: [edited] })
  })

  it.each(['answer', 'finish', 'start', 'sync', 'abandon'])(
    'FR045_ignores a late %s completion after moving to another topic',
    async (operation) => {
      const oldSession = createSession({ sessionId: 1, refId: 11 })
      const newSession = createSession({ sessionId: 2, refId: 22 })
      let release = () => {}
      const blocked = new Promise<void>((resolve) => {
        release = resolve
      })
      const start = vi.fn().mockResolvedValue(oldSession)
      const { result, rerender } = renderHook(
        ({ refId }) => usePracticeSession({ source: 'TOPIC_PRACTICE', refId, start }),
        { initialProps: { refId: 11 } },
      )
      await waitFor(() => expect(result.current.state).toBe('idle'))
      await act(() => result.current.start())
      if (operation === 'answer')
        answerItemMock.mockImplementationOnce(async () => {
          await blocked
          return { no: 1, correct: true }
        })
      if (operation === 'finish')
        finishSessionMock.mockImplementationOnce(async () => {
          await blocked
          return { sessionId: 1, status: 'SUBMITTED', items: [], suspicious: false }
        })
      if (operation === 'start')
        start.mockImplementationOnce(async () => {
          await blocked
          return oldSession
        })
      if (operation === 'sync')
        getSessionMock.mockImplementationOnce(async () => {
          await blocked
          return oldSession
        })
      if (operation === 'abandon')
        abandonSessionMock.mockImplementationOnce(async () => {
          await blocked
        })
      let pending: Promise<unknown> = Promise.resolve()
      act(() => {
        if (operation === 'answer')
          pending = result.current.answer(1, { answer: 'A', durationMs: 800 })
        if (operation === 'finish') pending = result.current.finish()
        if (operation === 'start') pending = result.current.start()
        if (operation === 'sync') pending = result.current.sync()
        if (operation === 'abandon') pending = result.current.abandon()
      })
      getSessionMock.mockResolvedValue(newSession)
      findActiveSessionMock.mockResolvedValue(newSession)
      rerender({ refId: 22 })
      await waitFor(() => expect(result.current.session?.sessionId).toBe(2))

      await act(async () => {
        release()
        await pending
      })

      expect(result.current.session).toEqual(newSession)
      expect(result.current.state).toBe('ready')
      expect(result.current.results).toEqual({})
      expect(result.current.finishResult).toBeNull()
      expect(result.current.pending).toBeNull()
    },
  )

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

  it('keeps the session idle without an error when start returns null', async () => {
    const start = vi.fn().mockResolvedValue(null)
    const { result } = renderHook(() => usePracticeSession({ source: 'TOPIC_STUDY', start }))

    await waitFor(() => expect(result.current.state).toBe('idle'))

    await act(() => result.current.start())

    expect(result.current.state).toBe('idle')
    expect(result.current.session).toBeNull()
    expect(result.current.error).toBeNull()
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

  it('syncs the current session after ANSWER_ALREADY_SUBMITTED and clears the error', async () => {
    const active = createSession({ sessionId: 51, source: 'TOPIC_STUDY' })
    const submitted = createSession({
      answered: [{ answer: 'LEARNED', correct: null, no: 1 }],
      sessionId: 51,
      source: 'TOPIC_STUDY',
      status: 'SUBMITTED',
    })
    answerItemMock.mockRejectedValue(apiError('ANSWER_ALREADY_SUBMITTED', 409))
    getSessionMock.mockResolvedValue(submitted)
    const { result } = renderHook(() =>
      usePracticeSession({
        source: 'TOPIC_STUDY',
        start: vi.fn().mockResolvedValue(active),
      }),
    )
    await waitFor(() => expect(result.current.state).toBe('idle'))
    await act(() => result.current.start())
    await act(() => result.current.answer(1, { answer: 'LEARNED', durationMs: 500 }))

    expect(result.current.error?.errorCode).toBe('ANSWER_ALREADY_SUBMITTED')

    await act(() => result.current.sync())

    expect(getSessionMock).toHaveBeenCalledWith(51)
    expect(result.current.error).toBeNull()
    expect(result.current.state).toBe('finished')
    expect(result.current.session).toEqual(submitted)
    expect(result.current.finishResult).toBeNull()
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
