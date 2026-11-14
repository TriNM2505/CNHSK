import { useEffect, useRef, useState } from 'react'
import { toApiError, type ApiError } from '@/shared/api'
import { startQuizAttempt } from './api/quizzes'
import type { QuizSession } from './types'

function handoff(value: unknown, topicId: number): QuizSession | undefined {
  // Browser history can retain navigation state across reload. Resume from the server then.
  const navigation =
    typeof performance.getEntriesByType === 'function'
      ? (performance.getEntriesByType('navigation') as PerformanceNavigationTiming[])
      : []
  if (navigation.some((entry) => entry.type === 'reload' && entry.name === window.location.href))
    return
  if (!value || typeof value !== 'object') return
  const candidate = (value as { quizSession?: QuizSession }).quizSession
  if (
    candidate?.topicId === topicId &&
    Number.isSafeInteger(candidate.sessionId) &&
    candidate.sessionId > 0 &&
    typeof candidate.rankingEligible === 'boolean' &&
    typeof candidate.expiresAt === 'string' &&
    Array.isArray(candidate.items)
  )
    return candidate
}

// The route owner keys this hook by user/topic. URL sessionId is never a session source.
export function useQuizAttempt(topicId: number, navigationState: unknown) {
  const [initial] = useState(() => handoff(navigationState, topicId))
  const [session, setSession] = useState(initial)
  const [error, setError] = useState<ApiError>()
  const [loading, setLoading] = useState(!initial)
  const [retry, setRetry] = useState(0)
  const request = useRef<{ retry: number; promise: Promise<QuizSession> }>()
  useEffect(() => {
    if (initial) return
    let current = true
    setLoading(true)
    setError(undefined)
    // StrictMode effect replay shares this POST instead of starting a second request.
    if (!request.current || request.current.retry !== retry) {
      request.current = { retry, promise: startQuizAttempt(topicId) }
    }
    void request.current.promise
      .then((value) => {
        if (!current) return
        if (value.topicId !== topicId) throw new Error('Lượt quiz không khớp chủ đề.')
        setSession(value)
      })
      .catch((caught: unknown) => {
        if (current) setError(toApiError(caught))
      })
      .finally(() => {
        if (current) setLoading(false)
      })
    return () => {
      current = false
    }
  }, [initial, retry, topicId])
  return { session, error, loading, reload: () => setRetry((value) => value + 1) }
}
