import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import {
  type AnswerRequest,
  type FinishResult,
  type ServedItemView,
  usePracticeSession,
} from '@/features/practice'
import { useApiQuery } from '@/shared/api'

import { getTopic, startTopicStudySession } from '../api/topics'
import { StudyStepView, TopicStudyView } from '../components/TopicStudyView'
import {
  isTopicNotFound,
  parseTopicStep,
  practiceErrorMessage,
  topicStepPath,
  type TopicStep,
} from '../topicStudyFlow'
import type { TopicDetail } from '../types'
import type { StartTopicStudyResult } from '../types'
import { TopicPracticeController } from './TopicPracticeController'

const DUPLICATE_ERROR_CODES = new Set(['ANSWER_ALREADY_SUBMITTED', 'SESSION_ALREADY_SUBMITTED'])
const RESTART_ERROR_CODES = new Set(['SESSION_EXPIRED', 'SESSION_NOT_FOUND', 'ATTEMPT_NOT_OWNED'])

function isNoNewWords(
  response: StartTopicStudyResult,
): response is Extract<StartTopicStudyResult, { session: null }> {
  return 'reason' in response && response.reason === 'NO_NEW_WORDS'
}

function parsePositiveId(value: string | undefined) {
  if (!value || !/^[1-9]\d*$/.test(value)) return undefined
  const id = Number(value)
  return Number.isSafeInteger(id) ? id : undefined
}

type StudyControllerProps = {
  onTopicFinished: () => void
  topic: TopicDetail
}

function TopicStudyController({ onTopicFinished, topic }: StudyControllerProps) {
  const [noNewWords, setNoNewWords] = useState(false)
  const [replayCount, setReplayCount] = useState(0)
  const autoStartAttemptedRef = useRef(false)
  const cardShownAtRef = useRef(Date.now())
  const finishingSessionRef = useRef<number>()
  const refreshedSessionIdsRef = useRef(new Set<number>())
  const syncedErrorRef = useRef<unknown>()

  const startSession = useCallback(async () => {
    const response = await startTopicStudySession(topic.id)
    if (isNoNewWords(response)) {
      setNoNewWords(true)
      return null
    }
    setNoNewWords(false)
    return response
  }, [topic.id])

  const practice = usePracticeSession({
    refId: topic.id,
    source: 'TOPIC_STUDY',
    start: startSession,
  })

  const answeredNumbers = useMemo(() => {
    const answered = new Set(practice.session?.answered.map((item) => item.no) ?? [])
    Object.keys(practice.results).forEach((no) => answered.add(Number(no)))
    return answered
  }, [practice.results, practice.session?.answered])

  const currentItem = practice.session?.items.find((item) => !answeredNumbers.has(item.no))
  const currentIndex = currentItem
    ? (practice.session?.items.findIndex((item) => item.no === currentItem.no) ?? 0) + 1
    : (practice.session?.items.length ?? 0)

  useEffect(() => {
    if (
      practice.state !== 'idle' ||
      practice.error ||
      noNewWords ||
      autoStartAttemptedRef.current
    ) {
      return
    }
    autoStartAttemptedRef.current = true
    void practice.start()
  }, [noNewWords, practice])

  useEffect(() => {
    if (!currentItem) return
    cardShownAtRef.current = Date.now()
    setReplayCount(0)
  }, [currentItem])

  useEffect(() => {
    const error = practice.error
    if (!error || !DUPLICATE_ERROR_CODES.has(error.errorCode) || syncedErrorRef.current === error) {
      return
    }
    syncedErrorRef.current = error
    void practice.sync()
  }, [practice])

  useEffect(() => {
    const session = practice.session
    if (
      practice.state !== 'finished' ||
      !session ||
      refreshedSessionIdsRef.current.has(session.sessionId)
    ) {
      return
    }
    refreshedSessionIdsRef.current.add(session.sessionId)
    onTopicFinished()
  }, [onTopicFinished, practice.session, practice.state])

  const finishCurrentSession = useCallback(async () => {
    const sessionId = practice.session?.sessionId
    if (!sessionId || finishingSessionRef.current === sessionId) return
    finishingSessionRef.current = sessionId
    const result = await practice.finish()
    if (!result && practice.error?.errorCode !== 'SESSION_ALREADY_SUBMITTED') {
      finishingSessionRef.current = undefined
    }
  }, [practice])

  useEffect(() => {
    if (
      practice.state === 'ready' &&
      practice.session?.items.length &&
      !currentItem &&
      practice.pending === null
    ) {
      void finishCurrentSession()
    }
  }, [
    currentItem,
    finishCurrentSession,
    practice.pending,
    practice.session?.items.length,
    practice.state,
  ])

  const submitAnswer = async (
    answer: 'LEARNED' | 'KNOWN' | undefined,
    audioFailure?: { audioFailed: true; replayCount: number },
  ) => {
    if (!currentItem) return
    const request: Omit<AnswerRequest, 'no'> = {
      answer,
      durationMs: Math.min(Math.max(Date.now() - cardShownAtRef.current, 0), 3_600_000),
      replayCount: audioFailure?.replayCount ?? replayCount,
      ...(audioFailure ? { audioFailed: true } : {}),
    }
    if (answer === undefined) delete request.answer

    const result = await practice.answer(currentItem.no, request)
    if (!result) return
    if (answeredNumbers.size + 1 >= (practice.session?.items.length ?? 0)) {
      await finishCurrentSession()
    }
  }

  const session = practice.session
  const summaryResult: FinishResult | undefined =
    practice.finishResult ??
    (session?.status === 'SUBMITTED'
      ? {
          items: session.answered.map((item) => ({
            correct: item.correct,
            correctAnswer: item.correctAnswer,
            no: item.no,
            yourAnswer: item.answer,
          })),
          maxScore: null,
          score: null,
          sessionId: session.sessionId,
          status: 'SUBMITTED',
          suspicious: false,
        }
      : undefined)

  const restartError = practice.error && RESTART_ERROR_CODES.has(practice.error.errorCode)
  const duplicateSyncing =
    practice.pending === 'sync' ||
    Boolean(practice.error && DUPLICATE_ERROR_CODES.has(practice.error.errorCode))
  const viewState = noNewWords
    ? 'no-new'
    : duplicateSyncing || practice.state === 'loading' || practice.pending === 'start'
      ? 'loading'
      : practice.state === 'finished'
        ? 'finished'
        : restartError
          ? 'restart'
          : practice.error
            ? 'error'
            : practice.state === 'ready'
              ? 'ready'
              : 'loading'

  return (
    <StudyStepView
      current={currentIndex}
      error={
        practice.error
          ? { message: practiceErrorMessage(practice.error), requestId: practice.error.requestId }
          : undefined
      }
      finishResult={summaryResult}
      item={currentItem as ServedItemView | undefined}
      learnedCount={answeredNumbers.size}
      onAudioFailed={(payload) => void submitAnswer(undefined, payload)}
      onContinue={() => void submitAnswer('LEARNED')}
      onKnown={() => void submitAnswer('KNOWN')}
      onReplayCountChange={setReplayCount}
      onRetry={practice.reload}
      onStartAgain={() => {
        setNoNewWords(false)
        void practice.start()
      }}
      pending={practice.pending === 'answer' || practice.pending === 'finish'}
      recognitionPath={topicStepPath(topic.id, 'recognition')}
      state={viewState}
      total={practice.session?.items.length}
    />
  )
}

function TopicStudyPageContent({
  activeStep,
  topicId,
}: {
  activeStep: TopicStep
  topicId: number
}) {
  const topicQuery = useApiQuery((signal) => getTopic(topicId, signal), [topicId])
  const topic = topicQuery.data
  const state = topic
    ? 'ready'
    : isTopicNotFound(topicQuery.error)
      ? 'empty'
      : topicQuery.state === 'empty'
        ? 'empty'
        : topicQuery.state

  return (
    <TopicStudyView
      activeStep={activeStep}
      error={topicQuery.error}
      onRetry={topicQuery.reload}
      state={state}
      stepPanel={
        topic && activeStep === 'recognition' && topic.actions.canPracticeRecognition ? (
          <TopicPracticeController mode="RECOGNITION" topic={topic} />
        ) : topic && activeStep === 'listening' && topic.actions.canPracticeListening ? (
          <TopicPracticeController mode="LISTENING" topic={topic} />
        ) : undefined
      }
      studyPanel={
        topic && activeStep === 'study' && topic.actions.canStudy ? (
          <TopicStudyController onTopicFinished={topicQuery.reload} topic={topic} />
        ) : undefined
      }
      topic={topic}
    />
  )
}

export default function Scr012TopicStudyPage() {
  const navigate = useNavigate()
  const params = useParams()
  const topicId = parsePositiveId(params.id)
  const parsedStep = parseTopicStep(params['*'])
  const activeStep = parsedStep ?? 'study'

  useEffect(() => {
    if (topicId && !parsedStep) navigate(topicStepPath(topicId, 'study'), { replace: true })
  }, [navigate, parsedStep, topicId])

  if (!topicId) {
    return <TopicStudyView activeStep="study" onRetry={() => undefined} state="empty" />
  }

  return <TopicStudyPageContent activeStep={activeStep} topicId={topicId} />
}
