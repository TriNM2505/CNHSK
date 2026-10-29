import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

import type { AnswerRequest } from '@/features/practice'
import { usePracticeSession } from '@/features/practice'
import type { HskLevel } from '@/shared/ui'

import { startRecognitionSession } from '../api/recognition'
import { RecognitionView, type RecognitionViewState } from '../components/RecognitionView'
import { modeFromSession } from '../labels'
import type { RecognitionMode } from '../types'

const DUPLICATE_ERRORS = new Set(['ANSWER_ALREADY_SUBMITTED', 'SESSION_ALREADY_SUBMITTED'])
const RESTART_ERRORS = new Set(['SESSION_EXPIRED', 'SESSION_NOT_FOUND', 'ATTEMPT_NOT_OWNED'])
const QUESTIONS_PER_SESSION = 10

function clampDuration(value: number) {
  return Math.min(Math.max(Math.round(value), 0), 3_600_000)
}

export default function Scr015CharacterRecognitionPage() {
  const [mode, setMode] = useState<RecognitionMode>('CHAR_TO_MEANING')
  const [hskLevel, setHskLevel] = useState<HskLevel>(1)
  const [noCharacters, setNoCharacters] = useState(false)
  const [setupRequested, setSetupRequested] = useState(false)
  const [finishConfirmOpen, setFinishConfirmOpen] = useState(false)
  const [activeNo, setActiveNo] = useState<number>()
  const [replayCount, setReplayCount] = useState(0)
  const initializedSessionRef = useRef<number>()
  const syncedErrorRef = useRef<unknown>()
  const shownAtRef = useRef(performance.now())

  const startSession = useCallback(async () => {
    const created = await startRecognitionSession({ count: QUESTIONS_PER_SESSION, hskLevel, mode })
    setNoCharacters(created === null)
    return created
  }, [hskLevel, mode])

  // FR-004: lượt nhận diện không gắn chủ đề, nên không có `refId`.
  const practice = usePracticeSession({ refId: null, source: 'CHAR_RECOGNITION', start: startSession })
  const sessionMode = modeFromSession(practice.session) ?? mode

  const answeredNumbers = useMemo(() => {
    const values = new Set(practice.session?.answered.map((item) => item.no) ?? [])
    Object.keys(practice.results).forEach((no) => values.add(Number(no)))
    return values
  }, [practice.results, practice.session?.answered])

  useEffect(() => {
    const session = practice.session
    if (!session || initializedSessionRef.current === session.sessionId) return
    initializedSessionRef.current = session.sessionId
    setActiveNo(session.items.find((item) => !answeredNumbers.has(item.no))?.no)
  }, [answeredNumbers, practice.session])

  useEffect(() => {
    shownAtRef.current = performance.now()
    setReplayCount(0)
  }, [activeNo])

  useEffect(() => {
    const error = practice.error
    if (!error || !DUPLICATE_ERRORS.has(error.errorCode) || syncedErrorRef.current === error) return
    syncedErrorRef.current = error
    initializedSessionRef.current = undefined
    void practice.sync()
  }, [practice])

  const item = practice.session?.items.find((candidate) => candidate.no === activeNo)
  const result = activeNo === undefined ? undefined : practice.results[activeNo]
  const current = item
    ? (practice.session?.items.findIndex((candidate) => candidate.no === item.no) ?? 0) + 1
    : 1

  const submit = async (request: Omit<AnswerRequest, 'no'>) => {
    if (!item) return null
    return practice.answer(item.no, {
      replayCount,
      ...request,
      durationMs: clampDuration(performance.now() - shownAtRef.current),
    })
  }

  // ChoiceQuestion bỏ chọn khi promise bị từ chối, nên chỉ ném lỗi khi gửi đáp án thất bại.
  const answer = async (selected: string) => {
    const answerResult = await submit({ answer: selected, durationMs: 0 })
    if (!answerResult) throw new Error('answer-failed')
    return answerResult
  }

  const next = async () => {
    const nextItem = practice.session?.items.find((candidate) => !answeredNumbers.has(candidate.no))
    if (nextItem) {
      setActiveNo(nextItem.no)
      return
    }
    await practice.finish()
  }

  const start = async () => {
    initializedSessionRef.current = undefined
    const created = await practice.start()
    if (created) setSetupRequested(false)
  }

  const finishEarly = async () => {
    const completed = await practice.finish()
    if (completed) setFinishConfirmOpen(false)
  }

  const backToSetup = () => {
    setNoCharacters(false)
    setSetupRequested(true)
  }

  const error = practice.error
  const syncing =
    practice.pending === 'sync' || Boolean(error && DUPLICATE_ERRORS.has(error.errorCode))
  const state: RecognitionViewState =
    practice.state === 'loading' || syncing
      ? 'loading'
      : practice.state === 'error'
        ? 'error'
        : setupRequested || practice.state === 'idle' || practice.state === 'abandoned'
          ? 'setup'
          : error && RESTART_ERRORS.has(error.errorCode)
            ? 'restart'
            : practice.state === 'finished'
              ? practice.finishResult
                ? 'finished'
                : 'setup'
              : 'ready'

  return (
    <RecognitionView
      current={current}
      error={error ? { message: error.message, requestId: error.requestId } : undefined}
      finishConfirmOpen={finishConfirmOpen}
      finishResult={practice.finishResult ?? undefined}
      hskLevel={hskLevel}
      item={item}
      mode={state === 'setup' ? mode : sessionMode}
      noCharacters={noCharacters}
      onAnswer={answer}
      onAudioFailed={(payload) => void submit({ ...payload, durationMs: 0 })}
      onFinishCancel={() => setFinishConfirmOpen(false)}
      onFinishConfirm={() => void finishEarly()}
      onFinishRequest={() => setFinishConfirmOpen(true)}
      onHskLevelChange={(level) => {
        setNoCharacters(false)
        setHskLevel(level)
      }}
      onModeChange={(nextMode) => {
        setNoCharacters(false)
        setMode(nextMode)
      }}
      onNext={() => void next()}
      onReplayCountChange={setReplayCount}
      onRestart={backToSetup}
      onRetry={practice.reload}
      onSkip={() => void submit({ durationMs: 0, skipped: true })}
      onStart={() => void start()}
      pending={practice.pending !== null}
      result={result}
      state={state}
      total={practice.session?.items.length}
    />
  )
}
