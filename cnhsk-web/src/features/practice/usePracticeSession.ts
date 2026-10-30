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

type PracticeDraft = Pick<AnswerRequest, 'no'> & Partial<AnswerRequest>

export interface UsePracticeSessionResult {
  state: PracticeSessionState
  session: SessionView | null
  results: Readonly<Record<number, AnswerResult>>
  drafts: Readonly<Record<number, Pick<AnswerRequest, 'no'> & Partial<AnswerRequest>>>
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
  drafts: Record<number, PracticeDraft>
  results: Record<number, AnswerResult>
} {
  if (session.feedback === 'DEFERRED') {
    return {
      drafts: Object.fromEntries(
        session.answered.map((item) => [
          item.no,
          { no: item.no, ...('answer' in item ? { answer: item.answer } : {}) },
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
          no: item.no,
          ...('correct' in item ? { correct: item.correct } : {}),
          ...('correctAnswer' in item ? { correctAnswer: item.correctAnswer } : {}),
          ...('counted' in item ? { counted: item.counted } : {}),
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
  const [drafts, setDrafts] = useState<Record<number, PracticeDraft>>({})
  const [finishResult, setFinishResult] = useState<FinishResult | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [pending, setPending] = useState<PracticePendingOperation>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const mountedRef = useRef(true)
  const generationRef = useRef(0)
  const operationRef = useRef<PracticePendingOperation>(null)
  const restoredDraftsRef = useRef<Record<number, PracticeDraft>>({})
  const editedSinceRestoreRef = useRef(new Set<number>())
  const startRef = useRef(startSession)

  startRef.current = startSession

  const applySession = useCallback((nextSession: SessionView) => {
    const restored = restoredState(nextSession)
    restoredDraftsRef.current = restored.drafts
    editedSinceRestoreRef.current.clear()
    setSession(nextSession)
    setDrafts(restored.drafts)
    setResults(restored.results)
    setFinishResult(null)
    setError(null)
    setState(stateForSession(nextSession))
  }, [])

  useEffect(() => {
    mountedRef.current = true
    generationRef.current += 1
    operationRef.current = null
    restoredDraftsRef.current = {}
    editedSinceRestoreRef.current.clear()
    const controller = new AbortController()
    let current = true

    setState('loading')
    setError(null)
    setPending(null)
    setSession(null)
    setResults({})
    setDrafts({})
    setFinishResult(null)

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
      generationRef.current += 1
      controller.abort()
    }
  }, [applySession, refId, reloadKey, source])

  const beginOperation = useCallback((operation: Exclude<PracticePendingOperation, null>) => {
    if (operationRef.current || !mountedRef.current) return null
    operationRef.current = operation
    setPending(operation)
    setError(null)
    return generationRef.current
  }, [])

  const isCurrent = useCallback(
    (generation: number) => mountedRef.current && generationRef.current === generation,
    [],
  )

  const endOperation = useCallback(
    (generation: number) => {
      if (!isCurrent(generation)) return
      operationRef.current = null
      setPending(null)
    },
    [isCurrent],
  )

  const start = useCallback(async () => {
    const generation = beginOperation('start')
    if (generation === null) return null
    try {
      const created = await startRef.current()
      if (!isCurrent(generation)) return null
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
      return created
    } catch (caught) {
      if (isCurrent(generation)) setError(practiceError(caught))
      return null
    } finally {
      endOperation(generation)
    }
  }, [applySession, beginOperation, endOperation, isCurrent])

  const answer = useCallback(
    async (no: number, request: Omit<AnswerRequest, 'no'>) => {
      if (!session) return null
      const generation = beginOperation('answer')
      if (generation === null) return null
      const completeRequest: AnswerRequest = { ...request, no }

      try {
        const answerResult = await answerItem(session.sessionId, completeRequest)
        if (!isCurrent(generation)) return null
        if (session.feedback === 'DEFERRED') {
          editedSinceRestoreRef.current.add(no)
          setDrafts((current) => ({ ...current, [no]: completeRequest }))
        } else {
          setResults((current) => ({ ...current, [no]: answerResult }))
        }
        return answerResult
      } catch (caught) {
        if (isCurrent(generation)) setError(practiceError(caught))
        return null
      } finally {
        endOperation(generation)
      }
    },
    [beginOperation, endOperation, isCurrent, session],
  )

  const finish = useCallback(
    async (answers?: AnswerRequest[]) => {
      if (!session) return null
      const generation = beginOperation('finish')
      if (generation === null) return null
      // Preserve the server's evidence for drafts restored from GET. Only edits
      // made in this page lifetime may overwrite those saved answers.
      const changedAnswers = answers?.filter((answer) => {
        const restored = restoredDraftsRef.current[answer.no]
        return (
          !restored ||
          editedSinceRestoreRef.current.has(answer.no) ||
          (answer.answer ?? null) !== (restored.answer ?? null) ||
          answer.skipped === true ||
          answer.audioFailed === true
        )
      })
      const request: FinishRequest | undefined = changedAnswers?.length
        ? { answers: changedAnswers }
        : undefined

      try {
        const completed = await finishSession(session.sessionId, request)
        if (!isCurrent(generation)) return null
        setFinishResult(completed)
        setSession((current) => (current ? { ...current, status: completed.status } : current))
        setState('finished')
        return completed
      } catch (caught) {
        if (isCurrent(generation)) setError(practiceError(caught))
        return null
      } finally {
        endOperation(generation)
      }
    },
    [beginOperation, endOperation, isCurrent, session],
  )

  const abandon = useCallback(async () => {
    if (!session) return false
    const generation = beginOperation('abandon')
    if (generation === null) return false
    try {
      await abandonSession(session.sessionId)
      if (!isCurrent(generation)) return false
      setSession((current) => (current ? { ...current, status: 'ABANDONED' } : current))
      setState('abandoned')
      return true
    } catch (caught) {
      if (isCurrent(generation)) setError(practiceError(caught))
      return false
    } finally {
      endOperation(generation)
    }
  }, [beginOperation, endOperation, isCurrent, session])

  const reload = useCallback(() => setReloadKey((current) => current + 1), [])

  const sync = useCallback(async () => {
    if (!session) return null
    const generation = beginOperation('sync')
    if (generation === null) return null
    try {
      const refreshed = await getSession(session.sessionId)
      if (!isCurrent(generation)) return null
      applySession(refreshed)
      return refreshed
    } catch (caught) {
      if (isCurrent(generation)) setError(practiceError(caught))
      return null
    } finally {
      endOperation(generation)
    }
  }, [applySession, beginOperation, endOperation, isCurrent, session])

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
