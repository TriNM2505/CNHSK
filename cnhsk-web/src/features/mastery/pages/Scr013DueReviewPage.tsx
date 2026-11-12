import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import {
  ChoiceQuestion,
  type AnswerResult,
  type SessionView,
  usePracticeSession,
} from '@/features/practice'
import { Button, Card, ErrorState, HanziText, PageHeader, Skeleton } from '@/shared/ui'

import { getReviewSummary, startReviewSession } from '../api/reviews'
import { formatMasteryDateTime } from '../formatDateTime'
import type { FlashcardRating, NothingDue } from '../types'

const RATINGS: readonly { label: string; value: FlashcardRating }[] = [
  { label: 'Quên', value: 'AGAIN' },
  { label: 'Khó', value: 'HARD' },
  { label: 'Được', value: 'GOOD' },
  { label: 'Dễ', value: 'EASY' },
]

function isNothingDue(value: SessionView | NothingDue): value is NothingDue {
  return 'reason' in value && value.reason === 'NOTHING_DUE'
}

function isChoiceType(type: string): boolean {
  return type === 'MEANING_CHOICE' || type === 'CHAR_MEANING_CHOICE'
}

function answerResultFor(session: SessionView, results: Readonly<Record<number, AnswerResult>>, no: number) {
  return results[no] ?? session.answered.find((answer) => answer.no === no)
}

export default function Scr013DueReviewPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const requestedAhead = searchParams.get('ahead') === 'true'
  const [nothingDue, setNothingDue] = useState(false)
  const [activeNo, setActiveNo] = useState<number>()
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
  const { state: practiceState, error: practiceError, pending: practicePending, start: startPractice, finish: finishPractice } = practice

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
  }, [activeNo, answeredNumbers.size, finishSession, practiceError, practicePending, practiceState, session])

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
      durationMs: Math.min(Math.max(Math.round(performance.now() - shownAtRef.current), 0), 3_600_000),
    })
    if (!saved) throw new Error('answer-failed')
    return saved
  }

  const submitRating = async (rating: FlashcardRating) => {
    if (!currentItem) return
    const saved = await practice.answer(currentItem.no, {
      answer: rating,
      durationMs: Math.min(Math.max(Math.round(performance.now() - shownAtRef.current), 0), 3_600_000),
    })
    if (saved) await advanceAfter(currentItem.no)
  }

  const skipCurrent = async () => {
    if (!currentItem) return
    const saved = await practice.answer(currentItem.no, {
      durationMs: Math.min(Math.max(Math.round(performance.now() - shownAtRef.current), 0), 3_600_000),
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
    return (
      <main className="mx-auto w-full max-w-reading space-y-5 px-4 py-6 sm:px-6">
        <PageHeader title="Ôn tập" />
        <Skeleton variant="card" />
      </main>
    )
  }

  if (practiceError && !session) {
    return (
      <main className="mx-auto w-full max-w-reading space-y-5 px-4 py-6 sm:px-6">
        <PageHeader title="Ôn tập" />
        <ErrorState message={practiceError.message} onRetry={retryStart} />
      </main>
    )
  }

  if (nothingDue) {
    return (
      <main className="mx-auto w-full max-w-reading space-y-5 px-4 py-6 sm:px-6">
        <PageHeader title="Ôn tập" />
        <Card className="space-y-4 text-center">
          <p role="status">Chưa có mục đến hạn</p>
          <Button onClick={startAhead}>Ôn trước</Button>
        </Card>
      </main>
    )
  }

  if (practiceState === 'finished' && session) {
    const gradedChoices = session.items
      .filter((item) => isChoiceType(item.type) && answeredNumbers.has(item.no))
      .map((item) => answerResultFor(session, practice.results, item.no))
      .filter((answer) => typeof answer?.correct === 'boolean')
    const correctChoices = gradedChoices.filter((answer) => answer?.correct === true).length

    return (
      <main className="mx-auto w-full max-w-reading space-y-5 px-4 py-6 sm:px-6">
        <PageHeader title="Đã hoàn thành ôn tập" />
        <Card className="space-y-4">
          <p role="status">Đã xử lý {answeredNumbers.size} mục</p>
          <p>Số câu chọn đúng: {correctChoices} / {gradedChoices.length}</p>
          {summaryLoading ? <Skeleton variant="text" /> : null}
          {summaryError ? (
            <ErrorState message="Không tải được hạn ôn kế tiếp" onRetry={retrySummary} />
          ) : null}
          {!summaryLoading && !summaryError && nextDueAt ? (
            <p>Lần ôn tiếp theo: {formatMasteryDateTime(nextDueAt)}</p>
          ) : null}
          {!summaryLoading && !summaryError && nextDueAt === null ? (
            <p>Chưa có lịch ôn tiếp theo</p>
          ) : null}
          <Button to="/learn/progress" variant="outline">Xem tiến độ</Button>
        </Card>
      </main>
    )
  }

  if (!session) {
    return (
      <main className="mx-auto w-full max-w-reading space-y-5 px-4 py-6 sm:px-6">
        <PageHeader title="Ôn tập" />
        <Skeleton variant="card" />
      </main>
    )
  }

  if (!currentItem && finishError) {
    return (
      <main className="mx-auto w-full max-w-reading space-y-5 px-4 py-6 sm:px-6">
        <PageHeader title="Ôn tập" />
        <ErrorState message="Không thể kết thúc phiên ôn." onRetry={retryFinish} />
      </main>
    )
  }

  if (!currentItem) {
    return (
      <main className="mx-auto w-full max-w-reading space-y-5 px-4 py-6 sm:px-6">
        <PageHeader title="Ôn tập" />
        <Skeleton variant="card" />
      </main>
    )
  }

  const pending = practicePending !== null

  return (
    <main className="mx-auto w-full max-w-reading space-y-5 px-4 py-6 sm:px-6">
      <PageHeader title="Ôn tập" />
      <div aria-live="polite" className="text-body-sm text-ink-secondary">
        {remaining} mục còn lại
      </div>
      {practiceError ? (
        <ErrorState message={practiceError.message} onRetry={() => void practice.sync()} />
      ) : null}
      <Card className="space-y-5">
        {isChoiceType(currentItem.type) ? (
          <ChoiceQuestion
            feedbackMode={session.feedback}
            item={currentItem}
            onAnswer={submitChoice}
            pending={pending}
            result={result}
            selectedAnswer={practice.session?.answered.find((item) => item.no === currentItem.no)?.answer}
          />
        ) : currentItem.type === 'FLASHCARD_RATING' ? (
          <section aria-labelledby="review-flashcard-title" className="space-y-5 text-center">
            <HanziText className="block" id="review-flashcard-title" size="lg">
              {currentItem.prompt.text}
            </HanziText>
            {currentItem.prompt.pinyin ? <p className="text-h3 text-brand-700">{currentItem.prompt.pinyin}</p> : null}
            {currentItem.prompt.sinoViet ? <p className="text-label text-ink-secondary">{currentItem.prompt.sinoViet}</p> : null}
            {currentItem.prompt.meaning ? <p className="text-body text-ink">{currentItem.prompt.meaning}</p> : null}
            {currentItem.prompt.examples?.length ? (
              <ul className="space-y-2 text-body-sm text-ink-secondary">
                {currentItem.prompt.examples.map((example, index) => (
                  <li key={`${example.cn ?? 'example'}-${index}`}>
                    {example.cn ? <span className="font-hanzi text-ink">{example.cn}</span> : null}
                    {example.vi ? <span> — {example.vi}</span> : null}
                  </li>
                ))}
              </ul>
            ) : null}
            <div aria-label="Đánh giá mức độ nhớ" className="grid grid-cols-2 gap-3 sm:grid-cols-4" role="group">
              {RATINGS.map(({ label, value }) => (
                <Button disabled={pending} key={value} onClick={() => void submitRating(value)}>
                  {label}
                </Button>
              ))}
            </div>
          </section>
        ) : (
          <ErrorState message={`Loại câu hỏi không được hỗ trợ: ${currentItem.type}`} onRetry={() => void practice.sync()} />
        )}
        {isChoiceType(currentItem.type) && result ? (
          <Button disabled={pending} onClick={() => void advanceAfter(currentItem.no)}>
            {remaining === 0 ? 'Kết thúc ôn tập' : 'Tiếp'}
          </Button>
        ) : null}
        <div className="flex flex-wrap justify-between gap-3">
          <Button disabled={pending} onClick={() => void skipCurrent()} variant="outline">
            Bỏ qua
          </Button>
          <Button disabled={pending} onClick={() => navigate('/learn')} variant="outline">
            Tạm dừng
          </Button>
        </div>
      </Card>
    </main>
  )
}
