import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import type { AnswerRequest, FinishResult, SessionView } from '@/features/practice'
import { usePracticeSession } from '@/features/practice'

import { startTopicPracticeSession } from '../api/topics'
import { TopicPracticeStepView } from '../components/TopicPracticeStepView'
import { practiceErrorMessage, topicStepPath } from '../topicStudyFlow'
import type { TopicDetail, TopicPracticeMode } from '../types'

const DUPLICATE_ERRORS = new Set(['ANSWER_ALREADY_SUBMITTED', 'SESSION_ALREADY_SUBMITTED'])
const RESTART_ERRORS = new Set(['SESSION_EXPIRED', 'SESSION_NOT_FOUND', 'ATTEMPT_NOT_OWNED'])
const UNAVAILABLE_ERRORS = new Set(['NO_LEARNED_WORDS', 'NO_AUDIO_WORDS', 'NOT_ENOUGH_WORDS'])

function focusIdsFromState(state: unknown) {
  if (!state || typeof state !== 'object' || !('focusLexemeIds' in state)) return undefined
  const value = (state as { focusLexemeIds?: unknown }).focusLexemeIds
  if (!Array.isArray(value)) return undefined
  const ids = value.filter((id): id is number => Number.isSafeInteger(id) && id > 0)
  return ids.length ? ids : undefined
}

function modeFromSession(session: SessionView | null): TopicPracticeMode | undefined {
  const type = session?.items.find(
    (item) => item.type === 'MEANING_CHOICE' || item.type === 'AUDIO_WORD_CHOICE',
  )?.type
  if (type === 'MEANING_CHOICE') return 'RECOGNITION'
  if (type === 'AUDIO_WORD_CHOICE') return 'LISTENING'
  return undefined
}

function unavailableMessage(errorCode: string) {
  if (errorCode === 'NO_LEARNED_WORDS') return 'Bạn cần học ít nhất một từ trước khi luyện.'
  if (errorCode === 'NO_AUDIO_WORDS') return 'Chưa có từ đã học nào có âm thanh.'
  return 'Chưa có đủ từ phù hợp để tạo lượt luyện.'
}

function restoredFinishResult(session: SessionView | null): FinishResult | undefined {
  if (session?.status !== 'SUBMITTED') return undefined
  const score = session.answered.filter((answer) => answer.correct === true).length
  const maxScore = session.answered.filter((answer) => answer.counted === true).length
  return {
    extra: {},
    items: session.answered.map((answer) => ({
      correct: answer.correct,
      correctAnswer: answer.correctAnswer,
      no: answer.no,
      yourAnswer: answer.answer,
    })),
    maxScore,
    percent: maxScore === 0 ? null : Math.floor((score * 100) / maxScore),
    score,
    sessionId: session.sessionId,
    status: 'SUBMITTED',
    suspicious: false,
  }
}

type TopicPracticeControllerProps = {
  mode: TopicPracticeMode
  topic: TopicDetail
}

export function TopicPracticeController({ mode, topic }: TopicPracticeControllerProps) {
  return <TopicPracticeSession key={`${topic.id}:${mode}`} mode={mode} topic={topic} />
}

function TopicPracticeSession({ mode, topic }: TopicPracticeControllerProps) {
  const location = useLocation()
  const navigate = useNavigate()
  const mountedRef = useRef(true)
  const initialFocusIdsRef = useRef(focusIdsFromState(location.state))
  const [abandonConfirmOpen, setAbandonConfirmOpen] = useState(false)
  const [activeNo, setActiveNo] = useState<number>()
  const [replayCount, setReplayCount] = useState(0)
  const initializedSessionRef = useRef<number>()
  const autoStartAttemptedRef = useRef(false)
  const finishRequestedRef = useRef<number>()
  const shownAtRef = useRef(Date.now())
  const syncedErrorRef = useRef<unknown>()

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
    }
  }, [])

  const startSession = useCallback(async () => {
    const focusLexemeIds = initialFocusIdsRef.current
    const created = await startTopicPracticeSession(topic.id, {
      ...(focusLexemeIds ? { focusLexemeIds } : {}),
      mode,
    })
    if (focusLexemeIds && mountedRef.current) {
      initialFocusIdsRef.current = undefined
      navigate(location.pathname, { replace: true, state: null })
    }
    return created
  }, [location.pathname, mode, navigate, topic.id])

  const practice = usePracticeSession({
    refId: topic.id,
    source: 'TOPIC_PRACTICE',
    start: startSession,
  })
  const restoredMode = modeFromSession(practice.session)
  const mismatch = Boolean(restoredMode && restoredMode !== mode)
  const questionKey = practice.session ? `${practice.session.sessionId}:${activeNo}` : undefined
  const currentQuestionRef = useRef(questionKey)
  currentQuestionRef.current = questionKey

  const answeredNumbers = useMemo(() => {
    const values = new Set(practice.session?.answered.map((item) => item.no) ?? [])
    Object.keys(practice.results).forEach((no) => values.add(Number(no)))
    return values
  }, [practice.results, practice.session?.answered])

  const finishCurrentSession = useCallback(async () => {
    if (!practice.session) return
    finishRequestedRef.current = practice.session.sessionId
    await practice.finish()
  }, [practice])

  useEffect(() => {
    const session = practice.session
    if (
      !session ||
      mismatch ||
      practice.state !== 'ready' ||
      practice.error ||
      practice.pending !== null ||
      initializedSessionRef.current === session.sessionId
    )
      return
    initializedSessionRef.current = session.sessionId
    finishRequestedRef.current = undefined
    const nextItem = session.items.find((item) => !answeredNumbers.has(item.no))
    setActiveNo(nextItem?.no)
    // Only a newly restored session can finish here. Live answers wait for Next.
    if (!nextItem && session.items.length > 0) void finishCurrentSession()
  }, [answeredNumbers, finishCurrentSession, mismatch, practice])

  useEffect(() => {
    if (practice.state !== 'idle' || practice.error || autoStartAttemptedRef.current) return
    autoStartAttemptedRef.current = true
    void practice.start()
  }, [practice])

  useEffect(() => {
    shownAtRef.current = Date.now()
    setReplayCount(0)
  }, [questionKey])

  useEffect(() => {
    const error = practice.error
    if (!error || !DUPLICATE_ERRORS.has(error.errorCode) || syncedErrorRef.current === error) return
    syncedErrorRef.current = error
    void practice.sync()
  }, [practice])

  const item = practice.session?.items.find((candidate) => candidate.no === activeNo)
  const result = activeNo ? practice.results[activeNo] : undefined
  const current = item
    ? (practice.session?.items.findIndex((candidate) => candidate.no === item.no) ?? 0) + 1
    : 1

  const submit = async (request: Omit<AnswerRequest, 'no'>) => {
    if (!item) return null
    return practice.answer(item.no, {
      replayCount,
      ...request,
      durationMs: Math.min(Math.max(Date.now() - shownAtRef.current, 0), 3_600_000),
    })
  }

  // ChoiceQuestion bỏ chọn khi promise bị từ chối, nên chỉ lựa chọn đáp án mới ném lỗi.
  const submitChoice = async (answer: string) => {
    const answerResult = await submit({ answer, durationMs: 0 })
    if (!answerResult) throw new Error('answer-failed')
    return answerResult
  }

  const next = async () => {
    const nextItem = practice.session?.items.find((candidate) => !answeredNumbers.has(candidate.no))
    if (nextItem) {
      setActiveNo(nextItem.no)
      return
    }
    await finishCurrentSession()
  }

  const abandonAndRestart = async () => {
    const abandoned = await practice.abandon()
    if (!abandoned) return
    initializedSessionRef.current = undefined
    setAbandonConfirmOpen(false)
    await practice.start()
  }

  const startNormal = () => {
    initialFocusIdsRef.current = undefined
    navigate(location.pathname, { replace: true, state: null })
    initializedSessionRef.current = undefined
    void practice.start()
  }

  const error = practice.error
  const duplicateSyncing =
    practice.pending === 'sync' || Boolean(error && DUPLICATE_ERRORS.has(error.errorCode))
  const state = mismatch
    ? 'mismatch'
    : duplicateSyncing || practice.state === 'loading' || practice.pending === 'start'
      ? 'loading'
      : practice.pending === 'finish'
        ? 'finishing'
        : practice.state === 'finished'
          ? 'finished'
          : error?.errorCode === 'INVALID_FOCUS_WORDS'
            ? 'invalid-focus'
            : error && UNAVAILABLE_ERRORS.has(error.errorCode)
              ? 'unavailable'
              : error && RESTART_ERRORS.has(error.errorCode)
                ? 'restart'
                : error
                  ? 'error'
                  : practice.state === 'ready'
                    ? 'ready'
                    : practice.state === 'abandoned'
                      ? 'restart'
                      : 'loading'

  const summaryResult = practice.finishResult ?? restoredFinishResult(practice.session)

  return (
    <TopicPracticeStepView
      abandonConfirmOpen={abandonConfirmOpen}
      continuePath={
        restoredMode
          ? topicStepPath(topic.id, restoredMode === 'RECOGNITION' ? 'recognition' : 'listening')
          : undefined
      }
      current={current}
      error={
        error
          ? {
              message: UNAVAILABLE_ERRORS.has(error.errorCode)
                ? unavailableMessage(error.errorCode)
                : practiceErrorMessage(error),
              requestId: error.requestId,
            }
          : undefined
      }
      finishResult={summaryResult}
      item={item}
      mode={mode}
      onAbandonCancel={() => setAbandonConfirmOpen(false)}
      onAbandonConfirm={() => void abandonAndRestart()}
      onAbandonRequest={() => setAbandonConfirmOpen(true)}
      onAnswer={submitChoice}
      onAudioFailed={(payload) => void submit({ ...payload, durationMs: 0 })}
      onNext={() => void next()}
      onReplayCountChange={(count) => {
        if (mountedRef.current && currentQuestionRef.current === questionKey) setReplayCount(count)
      }}
      onRetry={() => {
        autoStartAttemptedRef.current = false
        if (practice.session && finishRequestedRef.current === practice.session.sessionId) {
          void finishCurrentSession()
          return
        }
        initializedSessionRef.current = undefined
        practice.reload()
      }}
      onSkip={() => void submit({ durationMs: 0, skipped: true })}
      onStartNormal={startNormal}
      pending={practice.pending !== null}
      recognitionPath={topicStepPath(topic.id, 'recognition')}
      result={result}
      state={state}
      studyPath={topicStepPath(topic.id, 'study')}
      total={practice.session?.items.length}
    />
  )
}
