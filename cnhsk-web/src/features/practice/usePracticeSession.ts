import { useCallback, useEffect, useRef, useState } from 'react'

import {
  abandonSession,
  answerItem,
  findActiveSession,
  finishSession,
  getSession,
} from '@/features/practice/api'
import type {
  AnswerRequest,
  AnswerResult,
  FinishRequest,
  FinishResult,
  PracticeSource,
  SessionView,
} from '@/features/practice/types'
import { ApiError, toApiError } from '@/shared/api'

export type PracticeSessionState = 'loading' | 'idle' | 'ready' | 'finished' | 'abandoned' | 'error'

export type PracticePendingOperation = 'start' | 'answer' | 'finish' | 'abandon' | 'sync' | null

export interface UsePracticeSessionOptions {
  source: PracticeSource
  refId?: number | null
  start: () => Promise<SessionView | null>
}

export interface UsePracticeSessionResult {
  state: PracticeSessionState
  session: SessionView | null
  results: Readonly<Record<number, AnswerResult>>
  drafts: Readonly<Record<number, AnswerRequest>>
  finishResult: FinishResult | null
  error: ApiError | null
  pending: PracticePendingOperation
  start: () => Promise<SessionView | null>
  answer: (no: number, request: Omit<AnswerRequest, 'no'>) => Promise<AnswerResult | null>
  finish: (answers?: AnswerRequest[]) => Promise<FinishResult | null>
  abandon: () => Promise<boolean>
  reload: () => void
  sync: () => Promise<SessionView | null>
}

const PRACTICE_ERROR_MESSAGES: Partial<Record<string, string>> = {
  ANSWER_ALREADY_SUBMITTED: 'Câu này đã được trả lời. Hãy tải lại lượt luyện.',
  SESSION_ALREADY_SUBMITTED: 'Lượt luyện này đã được nộp. Hãy xem kết quả hoặc bắt đầu lượt mới.',
  SESSION_EXPIRED: 'Lượt luyện đã hết hạn. Hãy bắt đầu lượt mới.',
}

function practiceError(error: unknown): ApiError {
  const apiError = toApiError(error)
  const message = PRACTICE_ERROR_MESSAGES[apiError.errorCode]

  if (!message) return apiError

  return new ApiError({
    details: apiError.details,
    errorCode: apiError.errorCode,
    extra: apiError.extra,
    message,
    requestId: apiError.requestId,
    status: apiError.status,
  })
}

function restoredState(session: SessionView): {
  drafts: Record<number, AnswerRequest>
  results: Record<number, AnswerResult>
} {
  if (session.feedback === 'DEFERRED') {
    return {
      drafts: Object.fromEntries(
        session.answered.map((item) => [
          item.no,
          { answer: item.answer, durationMs: 0, no: item.no, replayCount: 0 },
        ]),
      ),
      results: {},
    }
  }

  return {
    drafts: {},
    results: Object.fromEntries(
      session.answered.map((item) => [
        item.no,
        {
          accepted: true,
          correct: item.correct,
          correctAnswer: item.correctAnswer,
          counted: item.counted,
          no: item.no,
        },
      ]),
    ),
  }
}

function stateForSession(session: SessionView): PracticeSessionState {
  if (session.status === 'SUBMITTED') return 'finished'
  if (session.status === 'ABANDONED' || session.status === 'EXPIRED') return 'abandoned'
  return 'ready'
}

export function usePracticeSession({
  source,
  refId,
  start: startSession,
}: UsePracticeSessionOptions): UsePracticeSessionResult {
  const [state, setState] = useState<PracticeSessionState>('loading')
  const [session, setSession] = useState<SessionView | null>(null)
  const [results, setResults] = useState<Record<number, AnswerResult>>({})
  const [drafts, setDrafts] = useState<Record<number, AnswerRequest>>({})
  const [finishResult, setFinishResult] = useState<FinishResult | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [pending, setPending] = useState<PracticePendingOperation>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const mountedRef = useRef(true)
  const operationRef = useRef<PracticePendingOperation>(null)
  const startRef = useRef(startSession)

  startRef.current = startSession

  const applySession = useCallback((nextSession: SessionView) => {
    const restored = restoredState(nextSession)
    setSession(nextSession)
    setDrafts(restored.drafts)
    setResults(restored.results)
    setFinishResult(null)
    setError(null)
    setState(stateForSession(nextSession))
  }, [])

  useEffect(() => {
    mountedRef.current = true
    const controller = new AbortController()
    let current = true

    setState('loading')
    setError(null)

    void findActiveSession(source, refId, controller.signal)
      .then(async (activeSession) => {
        if (!activeSession) return null
        return getSession(activeSession.sessionId, controller.signal)
      })
      .then((restoredSession) => {
        if (!current || !mountedRef.current) return
        if (restoredSession) {
          applySession(restoredSession)
          return
        }
        setSession(null)
        setResults({})
        setDrafts({})
        setFinishResult(null)
        setState('idle')
      })
      .catch((caught: unknown) => {
        if (!current || !mountedRef.current || controller.signal.aborted) return
        setError(practiceError(caught))
        setState('error')
      })

    return () => {
      current = false
      mountedRef.current = false
      controller.abort()
    }
  }, [applySession, refId, reloadKey, source])

  const beginOperation = useCallback((operation: Exclude<PracticePendingOperation, null>) => {
    if (operationRef.current) return false
    operationRef.current = operation
    setPending(operation)
    setError(null)
    return true
  }, [])

  const endOperation = useCallback(() => {
    operationRef.current = null
    if (mountedRef.current) setPending(null)
  }, [])

  const start = useCallback(async () => {
    if (!beginOperation('start')) return null
    try {
      const created = await startRef.current()
      if (mountedRef.current) {
        if (created) {
          applySession(created)
        } else {
          setSession(null)
          setResults({})
          setDrafts({})
          setFinishResult(null)
          setError(null)
          setState('idle')
        }
      }
      return created
    } catch (caught) {
      if (mountedRef.current) setError(practiceError(caught))
      return null
    } finally {
      endOperation()
    }
  }, [applySession, beginOperation, endOperation])

  const answer = useCallback(
    async (no: number, request: Omit<AnswerRequest, 'no'>) => {
      if (!session || !beginOperation('answer')) return null
      const completeRequest: AnswerRequest = { ...request, no }

      try {
        const answerResult = await answerItem(session.sessionId, completeRequest)
        if (mountedRef.current) {
          if (session.feedback === 'DEFERRED') {
            setDrafts((current) => ({ ...current, [no]: completeRequest }))
          } else {
            setResults((current) => ({ ...current, [no]: answerResult }))
          }
        }
        return answerResult
      } catch (caught) {
        if (mountedRef.current) setError(practiceError(caught))
        return null
      } finally {
        endOperation()
      }
    },
    [beginOperation, endOperation, session],
  )

  const finish = useCallback(
    async (answers?: AnswerRequest[]) => {
      if (!session || !beginOperation('finish')) return null
      const request: FinishRequest | undefined = answers ? { answers } : undefined

      try {
        const completed = await finishSession(session.sessionId, request)
        if (mountedRef.current) {
          setFinishResult(completed)
          setSession((current) => (current ? { ...current, status: completed.status } : current))
          setState('finished')
        }
        return completed
      } catch (caught) {
        if (mountedRef.current) setError(practiceError(caught))
        return null
      } finally {
        endOperation()
      }
    },
    [beginOperation, endOperation, session],
  )

  const abandon = useCallback(async () => {
    if (!session || !beginOperation('abandon')) return false
    try {
      await abandonSession(session.sessionId)
      if (mountedRef.current) {
        setSession((current) => (current ? { ...current, status: 'ABANDONED' } : current))
        setState('abandoned')
      }
      return true
    } catch (caught) {
      if (mountedRef.current) setError(practiceError(caught))
      return false
    } finally {
      endOperation()
    }
  }, [beginOperation, endOperation, session])

  const reload = useCallback(() => setReloadKey((current) => current + 1), [])

  const sync = useCallback(async () => {
    if (!session || !beginOperation('sync')) return null
    try {
      const refreshed = await getSession(session.sessionId)
      if (mountedRef.current) applySession(refreshed)
      return refreshed
    } catch (caught) {
      if (mountedRef.current) setError(practiceError(caught))
      return null
    } finally {
      endOperation()
    }
  }, [applySession, beginOperation, endOperation, session])

  return {
    abandon,
    answer,
    drafts,
    error,
    finish,
    finishResult,
    pending,
    reload,
    results,
    session,
    start,
    state,
    sync,
  }
}
