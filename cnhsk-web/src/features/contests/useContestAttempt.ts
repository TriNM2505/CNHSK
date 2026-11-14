import { useCallback, useEffect, useRef, useState } from 'react'

import type { AnswerRequest, SessionStatus } from '@/features/practice/types'
import { ApiError, invalidResponseError, toApiError } from '@/shared/api'

import {
  enterContest,
  getContestDraft,
  reportContestTabHidden,
  saveContestAnswer,
  submitContest,
} from './api/contests'
import { createCountdown, remainingMilliseconds, type CountdownAnchor } from './countdown'
import type { ContestDraft, ContestSession } from './types'

export type SaveState = 'saving' | 'saved' | 'failed'
type Bootstrap = { session: ContestSession; anchor: CountdownAnchor; draft: ContestDraft }

async function bootstrap(contestId: number): Promise<Bootstrap> {
  const session = await enterContest(contestId)
  if (
    session.contestId !== contestId ||
    !Number.isFinite(Date.parse(session.deadline)) ||
    !Number.isFinite(Date.parse(session.serverTime))
  ) {
    throw invalidResponseError()
  }
  const anchor = createCountdown(session.deadline, session.serverTime)
  const draft = await getContestDraft(session.sessionId)
  return { session, anchor, draft }
}

export function useContestAttempt(contestId: number) {
  const [load, setLoad] = useState<'loading' | 'ready' | 'error'>('loading')
  const [loadError, setLoadError] = useState<ApiError>()
  const [session, setSession] = useState<ContestSession>()
  const [choices, setChoices] = useState<Record<number, string>>({})
  const [saveStates, setSaveStates] = useState<Record<number, SaveState>>({})
  const [remainingMs, setRemainingMs] = useState(0)
  const [locked, setLocked] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState(false)
  const [submitError, setSubmitError] = useState<ApiError>()
  const [reloadKey, setReloadKey] = useState(0)
  const mounted = useRef(false)
  const bootRequest = useRef<Promise<Bootstrap> | null>(null)
  const sessionRef = useRef<ContestSession>()
  const statusRef = useRef<SessionStatus>('IN_PROGRESS')
  const anchorRef = useRef<CountdownAnchor>()
  const lockedRef = useRef(false)
  const startedSubmission = useRef(false)
  const submission = useRef<Promise<boolean> | null>(null)
  const saveQueue = useRef<Promise<unknown>>(Promise.resolve())
  const pendingSaves = useRef(new Set<Promise<unknown>>())
  const revisions = useRef(new Map<number, number>())
  const enteredAt = useRef(performance.now())

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  const performSubmit = useCallback(
    (retry: boolean): Promise<boolean> => {
      if (submission.current) return submission.current
      if (statusRef.current === 'SUBMITTED') return Promise.resolve(true)
      if (!sessionRef.current || (startedSubmission.current && !retry))
        return Promise.resolve(false)
      startedSubmission.current = true
      lockedRef.current = true
      if (mounted.current) {
        setLocked(true)
        setSubmitting(true)
        setSubmitError(undefined)
      }
      const request = (async () => {
        // Freeze input first, then drain every answer already queued before sending the final request.
        await Promise.allSettled([...pendingSaves.current])
        try {
          await submitContest(contestId)
          statusRef.current = 'SUBMITTED'
          if (mounted.current) setSubmitted(true)
          return true
        } catch (error) {
          if (mounted.current) setSubmitError(toApiError(error))
          return false
        } finally {
          submission.current = null
          if (mounted.current) setSubmitting(false)
        }
      })()
      submission.current = request
      return request
    },
    [contestId],
  )

  useEffect(() => {
    let active = true
    setLoad('loading')
    setLoadError(undefined)
    // StrictMode replays effects; the same mount still owns exactly one entry request.
    bootRequest.current ??= bootstrap(contestId)
    void bootRequest.current
      .then(({ session: opened, anchor, draft }) => {
        if (!active) return
        if (draft.status === 'EXPIRED' || draft.status === 'ABANDONED') {
          throw new ApiError({
            status: 409,
            errorCode: 'CONTEST_ALREADY_ATTEMPTED',
            message: 'Lượt thi đã kết thúc. Bạn không thể bắt đầu lượt mới.',
          })
        }
        sessionRef.current = opened
        anchorRef.current = anchor
        statusRef.current = draft.status
        const allowed = new Map(opened.items.map((item) => [item.no, item.options ?? []]))
        setChoices(
          Object.fromEntries(
            draft.answered.flatMap(({ no, answer }) =>
              typeof answer === 'string' && allowed.get(no)?.some(({ key }) => key === answer)
                ? [[no, answer]]
                : [],
            ),
          ),
        )
        setSaveStates(Object.fromEntries(draft.answered.map(({ no }) => [no, 'saved'])))
        setSession(opened)
        setRemainingMs(remainingMilliseconds(anchor))
        setLoad('ready')
        setSubmitted(draft.status === 'SUBMITTED')
        lockedRef.current = draft.status === 'SUBMITTED'
        setLocked(lockedRef.current)
        enteredAt.current = performance.now()
      })
      .catch((error: unknown) => {
        if (active) {
          setLoadError(toApiError(error))
          setLoad('error')
        }
      })
    return () => {
      active = false
    }
  }, [contestId, reloadKey])

  useEffect(() => {
    if (load !== 'ready' || !anchorRef.current || submitted) return
    const tick = () => {
      const remaining = remainingMilliseconds(anchorRef.current!)
      setRemainingMs(remaining)
      if (remaining === 0 && statusRef.current === 'IN_PROGRESS') {
        void performSubmit(false)
      }
    }
    tick()
    const timer = window.setInterval(tick, 250)
    return () => window.clearInterval(timer)
  }, [load, performSubmit, submitted])

  useEffect(() => {
    let lastHiddenAt = -Infinity
    const handleVisibility = () => {
      const now = performance.now()
      if (
        !document.hidden ||
        !sessionRef.current ||
        statusRef.current !== 'IN_PROGRESS' ||
        now - lastHiddenAt < 10_000
      )
        return
      lastHiddenAt = now
      void reportContestTabHidden(contestId).catch(() => {
        // Event recording is informational and must not interrupt saving or submission.
      })
    }
    document.addEventListener('visibilitychange', handleVisibility)
    return () => document.removeEventListener('visibilitychange', handleVisibility)
  }, [contestId])

  const choose = useCallback(async (no: number, answer: string) => {
    const opened = sessionRef.current
    if (
      !opened ||
      lockedRef.current ||
      !anchorRef.current ||
      remainingMilliseconds(anchorRef.current) === 0 ||
      !opened.items.find((item) => item.no === no)?.options?.some(({ key }) => key === answer)
    ) {
      return false
    }
    const revision = (revisions.current.get(no) ?? 0) + 1
    revisions.current.set(no, revision)
    setChoices((current) => ({ ...current, [no]: answer }))
    setSaveStates((current) => ({ ...current, [no]: 'saving' }))
    const payload: AnswerRequest = {
      no,
      answer,
      durationMs: Math.min(
        3_600_000,
        Math.max(0, Math.round(performance.now() - enteredAt.current)),
      ),
      replayCount: 0,
    }
    // Preserve the learner's edit order even when responses have different network delays.
    const request = saveQueue.current.then(() => saveContestAnswer(opened.sessionId, payload))
    saveQueue.current = request.catch(() => undefined)
    pendingSaves.current.add(request)
    try {
      await request
      if (mounted.current && revisions.current.get(no) === revision) {
        setSaveStates((current) => ({ ...current, [no]: 'saved' }))
      }
      return true
    } catch {
      if (mounted.current && revisions.current.get(no) === revision) {
        setSaveStates((current) => ({ ...current, [no]: 'failed' }))
      }
      // The local choice remains accepted; the failed save has its own retry action.
      return true
    } finally {
      pendingSaves.current.delete(request)
    }
  }, [])

  return {
    load,
    loadError,
    session,
    choices,
    saveStates,
    remainingMs,
    locked: locked || remainingMs === 0,
    submitting,
    submitted,
    submitError,
    choose,
    selectQuestion: () => {
      enteredAt.current = performance.now()
    },
    submit: () => performSubmit(false),
    retrySubmit: () => performSubmit(true),
    reload: () => {
      bootRequest.current = null
      setReloadKey((key) => key + 1)
    },
  }
}
