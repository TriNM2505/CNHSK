import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

import type { AnswerRequest, FinishResult } from '@/features/practice'
import { usePracticeSession } from '@/features/practice'

import { startPronunciationStageSession } from '../api/pronunciation'
import { PronunciationPracticeView } from '../components/PronunciationPracticeView'
import type { PronunciationFinishExtra, PronunciationStageSummary } from '../types'

const DUPLICATE_ERRORS = new Set(['ANSWER_ALREADY_SUBMITTED', 'SESSION_ALREADY_SUBMITTED'])

function clampDuration(value: number) {
  return Math.min(Math.max(Math.round(value), 0), 3_600_000)
}

function numberFromExtra(extra: Record<string, unknown>, key: string) {
  const value = extra[key]
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined
}

function finishExtra(result: FinishResult | null): PronunciationFinishExtra | undefined {
  const extra = result?.extra
  if (!extra) return undefined
  const stageIndex = numberFromExtra(extra, 'stageIndex')
  const passPercent = numberFromExtra(extra, 'passPercent')
  if (stageIndex === undefined || passPercent === undefined) return undefined

  return {
    ...extra,
    accuracy: numberFromExtra(extra, 'accuracy') ?? null,
    bestAccuracy: numberFromExtra(extra, 'bestAccuracy') ?? null,
    completedAllStages: extra.completedAllStages === true,
    needed: numberFromExtra(extra, 'needed') ?? null,
    nextStageIndex: numberFromExtra(extra, 'nextStageIndex') ?? null,
    noScore: extra.noScore === true,
    passPercent,
    passed: extra.passed === true,
    stageIndex,
    unlockedNextStage: extra.unlockedNextStage === true,
  }
}

function errorNumber(
  error: ReturnType<typeof usePracticeSession>['error'],
  key: string,
): number | undefined {
  if (!error) return undefined
  const direct = error.extra[key]
  if (typeof direct === 'number' && Number.isFinite(direct)) return direct
  if (error.details && typeof error.details === 'object' && !Array.isArray(error.details)) {
    const detail = (error.details as Record<string, unknown>)[key]
    if (typeof detail === 'number' && Number.isFinite(detail)) return detail
  }
  return undefined
}

export function PronunciationPracticeController({
  onStagesRefresh,
  stage,
}: {
  onStagesRefresh: () => void
  stage: PronunciationStageSummary
}) {
  const [activeNo, setActiveNo] = useState<number>()
  const [replayCount, setReplayCount] = useState(0)
  const [incompleteRemaining, setIncompleteRemaining] = useState<number>()
  const initializedSessionRef = useRef<number>()
  const autoStartAttemptedRef = useRef(false)
  const autoFinishedSessionRef = useRef<number>()
  const refreshedSessionRef = useRef<number>()
  const syncedErrorRef = useRef<unknown>()
  const questionStartedAtRef = useRef(performance.now())

  const startSession = useCallback(
    () => startPronunciationStageSession(stage.stageIndex),
    [stage.stageIndex],
  )
  const practice = usePracticeSession({
    refId: stage.id,
    source: 'PRON_STAGE',
    start: startSession,
  })

  const items = useMemo(
    () => practice.session?.items.filter((item) => item.type === 'PRON_CHOICE') ?? [],
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
    setActiveNo(items.find((item) => !answeredNumbers.has(item.no))?.no)
  }, [answeredNumbers, items, practice.session])

  useEffect(() => {
    if (practice.state !== 'idle' || practice.error || autoStartAttemptedRef.current) return
    autoStartAttemptedRef.current = true
    void practice.start()
  }, [practice])

  useEffect(() => {
    questionStartedAtRef.current = performance.now()
    setReplayCount(0)
  }, [activeNo])

  useEffect(() => {
    const error = practice.error
    if (!error || syncedErrorRef.current === error) return
    if (error.errorCode === 'STAGE_INCOMPLETE') {
      syncedErrorRef.current = error
      setIncompleteRemaining(errorNumber(error, 'remaining'))
      initializedSessionRef.current = undefined
      autoFinishedSessionRef.current = undefined
      void practice.sync()
      return
    }
    if (DUPLICATE_ERRORS.has(error.errorCode)) {
      syncedErrorRef.current = error
      initializedSessionRef.current = undefined
      void practice.sync()
    }
  }, [practice])

  const extra = finishExtra(practice.finishResult)

  useEffect(() => {
    const sessionId = practice.finishResult?.sessionId
    if (!sessionId || !extra || refreshedSessionRef.current === sessionId) return
    refreshedSessionRef.current = sessionId
    onStagesRefresh()
  }, [extra, onStagesRefresh, practice.finishResult?.sessionId])

  const item = items.find((candidate) => candidate.no === activeNo)
  const result = activeNo === undefined ? undefined : practice.results[activeNo]

  const finish = async () => {
    const sessionId = practice.session?.sessionId
    if (!sessionId || autoFinishedSessionRef.current === sessionId) return
    autoFinishedSessionRef.current = sessionId
    const completed = await practice.finish()
    if (!completed) autoFinishedSessionRef.current = undefined
  }

  useEffect(() => {
    if (
      practice.state !== 'ready' ||
      !practice.session ||
      !items.length ||
      answeredNumbers.size < items.length ||
      practice.pending !== null
    ) {
      return
    }
    void finish()
  })

  const submit = async (request: Omit<AnswerRequest, 'no'>) => {
    if (!item) return null
    setIncompleteRemaining(undefined)
    const answerResult = await practice.answer(item.no, {
      replayCount,
      ...request,
      durationMs: clampDuration(performance.now() - questionStartedAtRef.current),
    })
    if (!answerResult) return null

    const answeredAfterSubmit = new Set(answeredNumbers)
    answeredAfterSubmit.add(item.no)
    if (answeredAfterSubmit.size >= items.length) await finish()
    return answerResult
  }

  const answer = async (selected: string) => {
    const answerResult = await submit({ answer: selected, durationMs: 0 })
    if (!answerResult) throw new Error('answer-failed')
    return answerResult
  }

  const next = () => {
    const nextItem = items.find((candidate) => !answeredNumbers.has(candidate.no))
    if (nextItem) setActiveNo(nextItem.no)
  }

  const retry = () => {
    autoStartAttemptedRef.current = false
    initializedSessionRef.current = undefined
    practice.reload()
  }

  const restart = async () => {
    if (practice.pending) return
    initializedSessionRef.current = undefined
    autoFinishedSessionRef.current = undefined
    setIncompleteRemaining(undefined)
    await practice.start()
  }

  const error = practice.error
  const requiredStageIndex = errorNumber(error, 'requiredStageIndex')
  const state =
    error?.errorCode === 'STAGE_NOT_FOUND'
      ? 'empty'
      : error?.errorCode === 'STAGE_LOCKED'
        ? 'locked'
        : error?.errorCode === 'STAGE_HAS_NO_UNITS'
          ? 'unavailable'
          : error?.errorCode === 'STAGE_INCOMPLETE' ||
              (error && DUPLICATE_ERRORS.has(error.errorCode)) ||
              practice.state === 'loading' ||
              practice.state === 'idle' ||
              practice.pending === 'start' ||
              practice.pending === 'sync'
            ? 'loading'
            : practice.state === 'finished' && extra
              ? 'finished'
              : error
                ? 'error'
                : practice.state === 'ready'
                  ? 'ready'
                  : 'loading'

  return (
    <PronunciationPracticeView
      answered={answeredNumbers.size}
      error={error ? { message: error.message, requestId: error.requestId } : undefined}
      finishExtra={extra}
      incompleteRemaining={incompleteRemaining}
      item={item}
      onAnswer={answer}
      onAudioFailed={(payload) => submit({ ...payload, durationMs: 0 })}
      onNext={next}
      onReplayCountChange={setReplayCount}
      onRestart={() => void restart()}
      onRetry={retry}
      pending={practice.pending !== null}
      requiredStageIndex={requiredStageIndex}
      result={result}
      stage={stage}
      state={state}
      total={items.length}
    />
  )
}
