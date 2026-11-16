import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import { type AnswerResult, type SessionView, usePracticeSession } from '@/features/practice'
import { DueReviewView } from '../components/DueReviewView'

import { getReviewSummary, startReviewSession } from '../api/reviews'
import type { FlashcardRating, NothingDue } from '../types'

function isNothingDue(value: SessionView | NothingDue): value is NothingDue {
  return 'reason' in value && value.reason === 'NOTHING_DUE'
}

function isChoiceType(type: string): boolean {
  return type === 'MEANING_CHOICE' || type === 'CHAR_MEANING_CHOICE'
}

function answerResultFor(
  session: SessionView,
  results: Readonly<Record<number, AnswerResult>>,
  no: number,
) {
  return results[no] ?? session.answered.find((answer) => answer.no === no)
}

function durationMs(shownAt: number): number {
  return Math.min(Math.max(Math.round(performance.now() - shownAt), 0), 3_600_000)
}

export default function Scr013DueReviewPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const requestedAhead = searchParams.get('ahead') === 'true'
  const [nothingDue, setNothingDue] = useState(false)
  const [activeNo, setActiveNo] = useState<number>()
  const [revealedFlashcardNo, setRevealedFlashcardNo] = useState<number>()
  const [nextDueAt, setNextDueAt] = useState<string | null>(null)
  const [summaryLoading, setSummaryLoading] = useState(true)
  const [summaryError, setSummaryError] = useState(false)
  const [finishError, setFinishError] = useState(false)
  const autoStartAttemptedRef = useRef(false)
  const aheadOverrideRef = useRef(false)
  const initializedSessionRef = useRef<number>()
  const finishingSessionRef = useRef<number>()
  const summarySessionRef = useRef<number>()
  const shownAtRef = useRef(performance.now())

  const startSession = useCallback(async () => {
    const ahead = aheadOverrideRef.current || requestedAhead
    aheadOverrideRef.current = false
    setNothingDue(false)
    const response = await startReviewSession(ahead ? { ahead: true } : {})
    if (isNothingDue(response)) {
      setNothingDue(true)
      return null
    }
    return response
  }, [requestedAhead])

  const practice = usePracticeSession({ refId: null, source: 'DUE_REVIEW', start: startSession })
  const {
    state: practiceState,
    error: practiceError,
    pending: practicePending,
    start: startPractice,
    finish: finishPractice,
  } = practice

  const answeredNumbers = useMemo(() => {
    const answered = new Set(practice.session?.answered.map((item) => item.no) ?? [])
    Object.keys(practice.results).forEach((no) => answered.add(Number(no)))
    return answered
  }, [practice.results, practice.session?.answered])

  const session = practice.session
  const currentItem = session?.items.find((item) => item.no === activeNo)
  const currentItemNo = currentItem?.no
  const remaining = session ? Math.max(0, session.items.length - answeredNumbers.size) : 0
  const result = currentItem && session ? practice.results[currentItem.no] : undefined
  const currentItemAnswered =
    currentItem !== undefined &&
    (Boolean(result) || session?.answered.some((answer) => answer.no === currentItem.no) === true)

  useEffect(() => {
    if (practiceState !== 'idle' || practiceError || practicePending !== null || nothingDue) return
    if (autoStartAttemptedRef.current) return
    autoStartAttemptedRef.current = true
    void startPractice()
  }, [nothingDue, practiceError, practicePending, practiceState, startPractice])

  useEffect(() => {
    if (!session || initializedSessionRef.current === session.sessionId) return
    initializedSessionRef.current = session.sessionId
    setActiveNo(session.items.find((item) => !answeredNumbers.has(item.no))?.no)
  }, [answeredNumbers, session])

  useEffect(() => {
    if (currentItemNo === undefined) return
    shownAtRef.current = performance.now()
  }, [currentItemNo])

  const finishSession = useCallback(async () => {
    const sessionId = session?.sessionId
    if (!sessionId || finishingSessionRef.current === sessionId) return
    finishingSessionRef.current = sessionId
    setFinishError(false)
    const finished = await finishPractice()
    if (!finished) {
      setFinishError(true)
      return
    }
    setFinishError(false)
  }, [finishPractice, session?.sessionId])

  useEffect(() => {
    if (
      practiceState !== 'ready' ||
      practiceError ||
      practicePending !== null ||
      !session ||
      session.status !== 'IN_PROGRESS' ||
      session.items.length === 0 ||
      activeNo !== undefined ||
      answeredNumbers.size !== session.items.length ||
      finishingSessionRef.current === session.sessionId
    ) {
      return
    }
    void finishSession()
  }, [
    activeNo,
    answeredNumbers.size,
    finishSession,
    practiceError,
    practicePending,
    practiceState,
    session,
  ])

  const loadNextDueAt = useCallback(async (signal?: AbortSignal) => {
    setSummaryLoading(true)
    setSummaryError(false)
    try {
      const summary = await getReviewSummary(signal)
      if (signal?.aborted) return
      setNextDueAt(summary.nextDueAt)
    } catch {
      if (signal?.aborted) return
      setSummaryError(true)
    } finally {
      if (!signal?.aborted) setSummaryLoading(false)
    }
  }, [])

  useEffect(() => {
    const sessionId = practiceState === 'finished' ? session?.sessionId : undefined
    if (!sessionId || summarySessionRef.current === sessionId) return
    summarySessionRef.current = sessionId
    const controller = new AbortController()
    void loadNextDueAt(controller.signal)
    return () => controller.abort()
  }, [loadNextDueAt, practiceState, session?.sessionId])

  const advanceAfter = async (completedNo: number) => {
    if (!session) return
    const next = session.items.find(
      (item) => item.no !== completedNo && !answeredNumbers.has(item.no),
    )
    if (next) {
      setActiveNo(next.no)
      return
    }
    setActiveNo(undefined)
    await finishSession()
  }

  const submitChoice = async (answer: string) => {
    if (!currentItem) return
    const saved = await practice.answer(currentItem.no, {
      answer,
      durationMs: durationMs(shownAtRef.current),
    })
    if (!saved) throw new Error('answer-failed')
    return saved
  }

  const submitRating = async (rating: FlashcardRating) => {
    if (!currentItem) return
    const saved = await practice.answer(currentItem.no, {
      answer: rating,
      durationMs: durationMs(shownAtRef.current),
    })
    if (saved) await advanceAfter(currentItem.no)
  }

  const skipCurrent = async () => {
    if (!currentItem) return
    const saved = await practice.answer(currentItem.no, {
      durationMs: durationMs(shownAtRef.current),
      skipped: true,
    })
    if (saved) await advanceAfter(currentItem.no)
  }

  const retryStart = () => {
    autoStartAttemptedRef.current = false
    setNothingDue(false)
    practice.reload()
  }

  const startAhead = () => {
    if (practice.pending !== null) return
    autoStartAttemptedRef.current = true
    aheadOverrideRef.current = true
    setNothingDue(false)
    navigate('/learn/review?ahead=true', { replace: true })
    void practice.start()
  }

  const retryFinish = () => {
    if (!session) return
    finishingSessionRef.current = undefined
    void finishSession()
  }

  const retrySummary = () => void loadNextDueAt()

  if (practiceState === 'loading' || practicePending === 'start') {
    return <DueReviewView state="loading" />
  }

  if (practiceError && !session) {
    return <DueReviewView state="error" error={practiceError.message} onRetry={retryStart} />
  }

  if (nothingDue) {
    return <DueReviewView state="empty" onStartAhead={startAhead} />
  }

  if (practiceState === 'finished' && session) {
    const gradedChoices = session.items
      .filter((item) => isChoiceType(item.type) && answeredNumbers.has(item.no))
      .map((item) => answerResultFor(session, practice.results, item.no))
      .filter((answer) => typeof answer?.correct === 'boolean')
    const correctChoices = gradedChoices.filter((answer) => answer?.correct === true).length

    return (
      <DueReviewView
        state="finished"
        answeredCount={answeredNumbers.size}
        correctChoices={correctChoices}
        gradedCount={gradedChoices.length}
        summaryLoading={summaryLoading}
        summaryError={summaryError}
        nextDueAt={nextDueAt}
        onRetrySummary={retrySummary}
      />
    )
  }

  if (!session) {
    return <DueReviewView state="loading" />
  }

  if (!currentItem && finishError) {
    return (
      <DueReviewView state="error" error="Không thể kết thúc phiên ôn." onRetry={retryFinish} />
    )
  }

  if (!currentItem) {
    return <DueReviewView state="loading" />
  }

  const pending = practicePending !== null

  return (
    <DueReviewView
      state="ready"
      session={session}
      currentItem={currentItem}
      remaining={remaining}
      pending={pending}
      result={result}
      currentItemAnswered={currentItemAnswered}
      isChoice={isChoiceType(currentItem.type)}
      revealed={revealedFlashcardNo === currentItem.no}
      error={practiceError?.message}
      selectedAnswer={practice.session?.answered.find((item) => item.no === currentItem.no)?.answer}
      onAnswer={submitChoice}
      onRating={(value) => void submitRating(value)}
      onReveal={() => setRevealedFlashcardNo(currentItem.no)}
      onAdvance={() => void advanceAfter(currentItem.no)}
      onSkip={() => void skipCurrent()}
      onPause={() => navigate('/learn')}
      onSync={() => void practice.sync()}
    />
  )
}
