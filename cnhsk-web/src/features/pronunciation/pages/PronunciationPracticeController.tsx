import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

import type { AnswerRequest, FinishResult } from '@/features/practice'
import { usePracticeSession } from '@/features/practice'
import type { ApiError } from '@/shared/api'

import { startPronunciationStageSession } from '../api/pronunciation'
import {
  PronunciationPracticeView,
  type PronunciationPracticeViewProps,
} from '../components/PronunciationPracticeView'
import type { PronunciationFinishExtra, PronunciationStageSummary } from '../types'

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
  const [finishError, setFinishError] = useState<ApiError>()
  const [alreadySubmitted, setAlreadySubmitted] = useState(false)
  const initializedSessionRef = useRef<number>()
  const autoStartAttemptedRef = useRef(false)
  const autoFinishedSessionRef = useRef<number>()
  const refreshedSessionRef = useRef<number>()
  const syncedErrorRef = useRef<unknown>()
  const finishSyncedRef = useRef(false)
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
  }, [activeNo, practice.session?.sessionId])

  useEffect(() => {
    const error = practice.error
    if (!error || practice.pending || syncedErrorRef.current === error) return
    syncedErrorRef.current = error
    if (error.errorCode === 'SESSION_ALREADY_SUBMITTED') {
      setAlreadySubmitted(true)
      return
    }
    if (error.errorCode === 'STAGE_INCOMPLETE') {
      finishSyncedRef.current = true
      setIncompleteRemaining(errorNumber(error, 'remaining'))
      initializedSessionRef.current = undefined
      void practice.sync()
      return
    }
    if (error.errorCode === 'ANSWER_ALREADY_SUBMITTED') {
      initializedSessionRef.current = undefined
      void practice.sync()
      return
    }
    if (
      autoFinishedSessionRef.current === practice.session?.sessionId &&
      practice.session &&
      !finishSyncedRef.current
    ) {
      finishSyncedRef.current = true
      setFinishError(error)
      initializedSessionRef.current = undefined
      void practice.sync()
    }
  }, [practice])

  const extra = finishExtra(practice.finishResult)

  useEffect(() => {
    const sessionId = practice.session?.sessionId
    if (
      !sessionId ||
      (practice.state !== 'finished' && !alreadySubmitted) ||
      refreshedSessionRef.current === sessionId
    )
      return
    refreshedSessionRef.current = sessionId
    onStagesRefresh()
  }, [alreadySubmitted, onStagesRefresh, practice.session?.sessionId, practice.state])

  const item = items.find((candidate) => candidate.no === activeNo)
  const result = activeNo === undefined ? undefined : practice.results[activeNo]

  const finishSession = practice.finish
  const sessionId = practice.session?.sessionId
  const finish = useCallback(async () => {
    if (!sessionId || autoFinishedSessionRef.current === sessionId) return
    autoFinishedSessionRef.current = sessionId
    await finishSession()
  }, [finishSession, sessionId])

  useEffect(() => {
    if (
      practice.state !== 'ready' ||
      practice.error ||
      finishError ||
      !practice.session ||
      !items.length ||
      answeredNumbers.size < items.length ||
      practice.pending !== null
    ) {
      return
    }
    void finish()
  }, [
    answeredNumbers.size,
    finish,
    finishError,
    items.length,
    practice.error,
    practice.pending,
    practice.session,
    practice.state,
  ])

  const submit = async (request: Omit<AnswerRequest, 'no'>) => {
    if (!item) return null
    setIncompleteRemaining(undefined)
    setFinishError(undefined)
    autoFinishedSessionRef.current = undefined
    finishSyncedRef.current = false
    const answerResult = await practice.answer(item.no, {
      replayCount,
      ...request,
      durationMs: clampDuration(performance.now() - questionStartedAtRef.current),
    })
    if (!answerResult) return null

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
    if (practice.pending) return
    if (
      practice.state === 'ready' &&
      autoFinishedSessionRef.current === sessionId &&
      answeredNumbers.size >= items.length &&
      items.length
    ) {
      setFinishError(undefined)
      finishSyncedRef.current = false
      autoFinishedSessionRef.current = undefined
      void finish()
      return
    }
    setFinishError(undefined)
    autoStartAttemptedRef.current = false
    initializedSessionRef.current = undefined
    practice.reload()
  }

  const restart = async () => {
    if (practice.pending) return
    initializedSessionRef.current = undefined
    autoFinishedSessionRef.current = undefined
    finishSyncedRef.current = false
    setFinishError(undefined)
    setAlreadySubmitted(false)
    setIncompleteRemaining(undefined)
    await practice.start()
  }

  const error = finishError ?? practice.error
  const requiredStageIndex = errorNumber(error, 'requiredStageIndex')
  let state: PronunciationPracticeViewProps['state'] = 'loading'
  if (
    practice.pending === 'start' ||
    practice.pending === 'sync' ||
    practice.pending === 'finish'
  ) {
    state = 'loading'
  } else if ((practice.state === 'finished' && !practice.error) || alreadySubmitted) {
    state = extra ? 'finished' : 'submitted'
  } else if (error?.errorCode === 'STAGE_NOT_FOUND') {
    state = 'empty'
  } else if (error?.errorCode === 'STAGE_LOCKED') {
    state = 'locked'
  } else if (error?.errorCode === 'STAGE_HAS_NO_UNITS') {
    state = 'unavailable'
  } else if (error) {
    state = 'error'
  } else if (practice.state === 'ready') {
    state = 'ready'
  }

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
