import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'

import type { FinishResult, ServedItemView } from '@/features/practice'
import { restoredFinishResult, usePracticeSession } from '@/features/practice'
import { useApiQuery } from '@/shared/api'

import { getGrammarLesson, getGrammarPoints, startGrammarLesson } from '../api/grammar'
import { GrammarLessonView } from '../components/GrammarLessonView'
import type { GrammarLesson, GrammarSummary } from '../types'
import { isCompletedGrammarLesson } from '../types'

const DUPLICATE_ERRORS = new Set(['ANSWER_ALREADY_SUBMITTED', 'SESSION_ALREADY_SUBMITTED'])

function parsePositiveId(value: string | undefined) {
  if (!value || !/^\d+$/.test(value)) return undefined
  const id = Number(value)
  return Number.isSafeInteger(id) && id > 0 ? id : undefined
}

function clampDuration(value: number) {
  return Math.min(Math.max(Math.round(value), 0), 3_600_000)
}

function isGrammarNotFound(error: { errorCode?: string; status?: number } | undefined) {
  return error?.status === 404 || error?.errorCode === 'GRAMMAR_POINT_NOT_FOUND'
}

function restoredGrammarFinishResult(
  session: ReturnType<typeof usePracticeSession>['session'],
): FinishResult | undefined {
  const restored = restoredFinishResult(session)
  if (!restored || !session) return undefined

  const choiceNumbers = new Set(
    session.items.filter((item) => item.type === 'GRAMMAR_CHOICE').map((item) => item.no),
  )
  const choiceAnswers = restored.items.filter((answer) => choiceNumbers.has(answer.no ?? -1))
  const exercisesTotal = choiceNumbers.size
  const answered = restored.maxScore ?? 0
  const correct = restored.score ?? 0
  const percent = answered === 0 ? null : Math.floor((correct * 100) / answered)
  const scored = exercisesTotal > 0
  return {
    ...restored,
    extra: {
      answered,
      correct,
      exercisesTotal,
      percent,
      skippedExercises: answered < exercisesTotal,
    },
    items: choiceAnswers,
    maxScore: scored ? answered : null,
    percent: scored ? percent : null,
    score: scored ? correct : null,
  }
}

function useNextGrammarPoint(lesson: GrammarLesson, finished: boolean) {
  const [state, setState] = useState<{
    error?: string
    pending: boolean
    point?: GrammarSummary
  }>({ pending: false })

  useEffect(() => {
    if (!finished) return
    const controller = new AbortController()
    setState({ pending: true })

    void getGrammarPoints(lesson.hskLevel, controller.signal)
      .then((points) => {
        if (controller.signal.aborted) return
        const currentIndex = points.findIndex((point) => point.id === lesson.id)
        setState({
          pending: false,
          point: currentIndex >= 0 ? points[currentIndex + 1] : undefined,
        })
      })
      .catch(() => {
        if (controller.signal.aborted) return
        setState({
          error: 'Chưa thể tải điểm ngữ pháp tiếp theo. Bạn vẫn có thể quay lại danh sách.',
          pending: false,
        })
      })

    return () => controller.abort()
  }, [finished, lesson.hskLevel, lesson.id])

  return state
}

function GrammarLessonController({ lesson }: { lesson: GrammarLesson }) {
  const [exerciseStarted, setExerciseStarted] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [activeNo, setActiveNo] = useState<number>()
  const [immediateFinish, setImmediateFinish] = useState<FinishResult>()
  const readStartedAtRef = useRef(performance.now())
  const questionStartedAtRef = useRef(performance.now())
  const initializedSessionRef = useRef<number>()
  const autoFinishedSessionRef = useRef<number>()
  const syncedErrorRef = useRef<unknown>()

  useEffect(() => {
    readStartedAtRef.current = performance.now()
  }, [lesson.id])

  const startSession = useCallback(async () => {
    const response = await startGrammarLesson(
      lesson.id,
      clampDuration(performance.now() - readStartedAtRef.current),
    )
    if (isCompletedGrammarLesson(response)) {
      setImmediateFinish(response.finish)
      return null
    }
    return response
  }, [lesson.id])

  const practice = usePracticeSession({
    refId: lesson.id,
    source: 'GRAMMAR_LESSON',
    start: startSession,
  })

  const choiceItems = useMemo(
    () => practice.session?.items.filter((item) => item.type === 'GRAMMAR_CHOICE') ?? [],
    [practice.session?.items],
  )
  const answeredNumbers = useMemo(() => {
    const answered = new Set(practice.session?.answered.map((answer) => answer.no) ?? [])
    Object.keys(practice.results).forEach((no) => answered.add(Number(no)))
    return answered
  }, [practice.results, practice.session?.answered])

  useEffect(() => {
    const session = practice.session
    if (!session || initializedSessionRef.current === session.sessionId) return
    initializedSessionRef.current = session.sessionId
    setActiveNo(choiceItems.find((item) => !answeredNumbers.has(item.no))?.no)
  }, [answeredNumbers, choiceItems, practice.session])

  useEffect(() => {
    questionStartedAtRef.current = performance.now()
  }, [activeNo])

  useEffect(() => {
    const error = practice.error
    if (!error || !DUPLICATE_ERRORS.has(error.errorCode) || syncedErrorRef.current === error) return
    syncedErrorRef.current = error
    void practice.sync()
  }, [practice])

  const item = choiceItems.find((candidate) => candidate.no === activeNo)
  const result = activeNo === undefined ? undefined : practice.results[activeNo]

  useEffect(() => {
    const sessionId = practice.session?.sessionId
    if (
      !exerciseStarted ||
      practice.state !== 'ready' ||
      !sessionId ||
      item ||
      choiceItems.some((choice) => !answeredNumbers.has(choice.no)) ||
      practice.pending !== null ||
      autoFinishedSessionRef.current === sessionId
    ) {
      return
    }
    autoFinishedSessionRef.current = sessionId
    void practice.finish()
  }, [answeredNumbers, choiceItems, exerciseStarted, item, practice])

  const begin = async () => {
    if (practice.pending) return
    if (practice.state === 'ready') {
      setExerciseStarted(true)
      return
    }
    const created = await practice.start()
    if (created) setExerciseStarted(true)
  }

  const answer = async (selected: string) => {
    if (!item) return
    const answerResult = await practice.answer(item.no, {
      answer: selected,
      durationMs: clampDuration(performance.now() - questionStartedAtRef.current),
      replayCount: 0,
    })
    if (!answerResult) throw new Error('answer-failed')
    return answerResult
  }

  const next = async () => {
    const nextItem = choiceItems.find((candidate) => !answeredNumbers.has(candidate.no))
    if (nextItem) {
      setActiveNo(nextItem.no)
      return
    }
    await practice.finish()
  }

  const skip = async () => {
    const finished = await practice.finish()
    if (finished) setConfirmOpen(false)
  }

  const finishResult =
    immediateFinish ?? practice.finishResult ?? restoredGrammarFinishResult(practice.session)
  const nextPoint = useNextGrammarPoint(lesson, Boolean(finishResult))
  const stage = finishResult ? 'finished' : exerciseStarted ? 'exercise' : 'reading'
  const current = item ? choiceItems.findIndex((candidate) => candidate.no === item.no) + 1 : 1

  return (
    <GrammarLessonView
      checkingActive={practice.state === 'loading' || practice.pending === 'sync'}
      confirmOpen={confirmOpen}
      current={current}
      finishResult={finishResult}
      hasActiveSession={practice.state === 'ready'}
      item={item as ServedItemView | undefined}
      lesson={lesson}
      nextLookupError={nextPoint.error}
      nextLookupPending={nextPoint.pending}
      nextPoint={nextPoint.point}
      onAnswer={answer}
      onBegin={() => void begin()}
      onNext={() => void next()}
      onRetry={practice.reload}
      onSkipCancel={() => setConfirmOpen(false)}
      onSkipConfirm={() => void skip()}
      onSkipRequest={() => setConfirmOpen(true)}
      pending={practice.pending !== null}
      practiceError={
        practice.error
          ? { message: practice.error.message, requestId: practice.error.requestId }
          : undefined
      }
      result={result}
      stage={stage}
      state="ready"
      total={choiceItems.length}
    />
  )
}

export default function Scr018GrammarLessonPage() {
  const params = useParams()
  const grammarId = parsePositiveId(params.id)
  const query = useApiQuery(
    (signal) =>
      grammarId ? getGrammarLesson(grammarId, signal) : Promise.reject(new Error('invalid-id')),
    [grammarId],
  )

  if (!grammarId || isGrammarNotFound(query.error)) {
    return <GrammarLessonView onRetry={() => undefined} state="empty" />
  }

  if (query.state === 'loading') {
    return <GrammarLessonView onRetry={query.reload} state="loading" />
  }

  if (query.state === 'error' || !query.data) {
    return <GrammarLessonView error={query.error} onRetry={query.reload} state="error" />
  }

  return <GrammarLessonController key={query.data.id} lesson={query.data} />
}
