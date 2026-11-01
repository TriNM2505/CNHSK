import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { usePracticeSession } from '@/features/practice'

import { startTopicTest } from '../api/topics'
import { TopicTestStepView, type TopicTestResult } from '../components/TopicTestStepView'
import {
  countUnanswered,
  draftsToAnswers,
  getTestStartFailure,
  isAnswered,
  PINYIN_DRAFT_DEBOUNCE_MS,
  readTestExtra,
  type TestDraft,
  toAnswerRequest,
} from '../topicTestFlow'
import { practiceErrorMessage, topicStepPath } from '../topicStudyFlow'
import type { TopicDetail } from '../types'

const DUPLICATE_ERRORS = new Set(['ANSWER_ALREADY_SUBMITTED', 'SESSION_ALREADY_SUBMITTED'])
const MAX_DURATION_MS = 3_600_000

function durationSince(shownAt: number | undefined) {
  return Math.min(Math.max(Date.now() - (shownAt ?? Date.now()), 0), MAX_DURATION_MS)
}

export function TopicTestController({
  onTopicFinished,
  topic,
}: {
  onTopicFinished: () => void
  topic: TopicDetail
}) {
  const navigate = useNavigate()
  const startSession = useCallback(() => startTopicTest(topic.id), [topic.id])
  const practice = usePracticeSession({
    refId: topic.id,
    source: 'TOPIC_TEST',
    start: startSession,
  })
  const practiceRef = useRef(practice)
  practiceRef.current = practice

  const [activeNo, setActiveNo] = useState<number>()
  const [drafts, setDrafts] = useState<Record<number, TestDraft>>({})
  const draftsRef = useRef(drafts)
  draftsRef.current = drafts
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [submitRequested, setSubmitRequested] = useState(false)
  const [waitSeconds, setWaitSeconds] = useState(0)
  const [failedSaveNo, setFailedSaveNo] = useState<number>()
  const failedSaveNoRef = useRef<number>()
  const clearableRef = useRef(new Set<number>())
  const initializedSessionRef = useRef<number>()
  const refreshedSessionRef = useRef<number>()
  const syncedErrorRef = useRef<unknown>()
  const firstShownRef = useRef<Record<number, number>>({})
  const unsavedRef = useRef(new Set<number>())
  const pinyinTimerRef = useRef<number>()
  const debouncingNoRef = useRef<number>()
  const savingRef = useRef(false)
  const saveNextRef = useRef<() => Promise<void>>()

  const session = practice.session
  const items = useMemo(() => session?.items ?? [], [session])

  // Lượt mới hoặc vừa khôi phục: lấy nháp server làm điểm xuất phát, mở câu chưa làm đầu tiên.
  useEffect(() => {
    if (!session || initializedSessionRef.current === session.sessionId) return
    initializedSessionRef.current = session.sessionId
    const restored: Record<number, TestDraft> = {}
    Object.values(practice.drafts).forEach((draft) => {
      restored[draft.no] = {
        answer: draft.answer ?? undefined,
        audioFailed: draft.audioFailed,
        replayCount: draft.replayCount ?? 0,
      }
    })
    unsavedRef.current.clear()
    failedSaveNoRef.current = undefined
    setFailedSaveNo(undefined)
    clearableRef.current = new Set(
      Object.entries(restored)
        .filter(([, draft]) => draft.answer?.trim())
        .map(([no]) => Number(no)),
    )
    const firstNo = (
      session.items.find((item) => !isAnswered(restored[item.no])) ?? session.items[0]
    )?.no
    firstShownRef.current = firstNo === undefined ? {} : { [firstNo]: Date.now() }
    setDrafts(restored)
    setActiveNo(firstNo)
  }, [practice.drafts, session])

  useEffect(() => {
    if (activeNo !== undefined && firstShownRef.current[activeNo] === undefined) {
      firstShownRef.current[activeNo] = Date.now()
    }
  }, [activeNo])

  /** Lưu lần lượt các câu chưa lưu; hook chỉ cho một thao tác chạy tại một thời điểm. */
  const flushDrafts = useCallback(async () => {
    const current = practiceRef.current
    if (
      savingRef.current ||
      failedSaveNoRef.current !== undefined ||
      current.pending !== null ||
      current.state !== 'ready'
    )
      return
    // Câu đang chờ debounce chỉ được lưu khi hết 500 ms hoặc khi rời câu/nộp bài.
    const no = [...unsavedRef.current].find((candidate) => candidate !== debouncingNoRef.current)
    if (no === undefined) return
    const draft = draftsRef.current[no]
    if (!draft) {
      unsavedRef.current.delete(no)
      return
    }
    savingRef.current = true
    try {
      const saved = await current.answer(
        no,
        toAnswerRequest(no, draft, durationSince(firstShownRef.current[no])),
      )
      if (!saved) {
        failedSaveNoRef.current = no
        setFailedSaveNo(no)
        return
      }
      if (draftsRef.current[no] === draft) {
        unsavedRef.current.delete(no)
        if (draft.skipped) clearableRef.current.delete(no)
      }
    } finally {
      savingRef.current = false
    }
    if (saveNextRef.current) void saveNextRef.current()
  }, [])
  saveNextRef.current = flushDrafts

  useEffect(() => {
    if (practice.pending === null && unsavedRef.current.size && !submitRequested) {
      void flushDrafts()
    }
  }, [flushDrafts, practice.pending, submitRequested])

  const updateDraft = (
    no: number,
    change: Partial<TestDraft>,
    save: 'now' | 'debounce' | 'none',
  ) => {
    // Cập nhật ref ngay để lần lưu chạy cùng lượt đọc được nháp mới nhất.
    const current = draftsRef.current
    const next = { ...current, [no]: { replayCount: 0, ...current[no], ...change } }
    draftsRef.current = next
    setDrafts(next)
    if (save === 'none') return
    if (change.answer?.trim()) clearableRef.current.add(no)
    if (failedSaveNoRef.current === no) {
      failedSaveNoRef.current = undefined
      setFailedSaveNo(undefined)
    }
    unsavedRef.current.add(no)
    window.clearTimeout(pinyinTimerRef.current)
    if (save === 'debounce') {
      debouncingNoRef.current = no
      pinyinTimerRef.current = window.setTimeout(() => {
        debouncingNoRef.current = undefined
        void flushDrafts()
      }, PINYIN_DRAFT_DEBOUNCE_MS)
    } else {
      debouncingNoRef.current = undefined
      void flushDrafts()
    }
  }

  useEffect(() => () => window.clearTimeout(pinyinTimerRef.current), [])

  // Nộp khi không còn thao tác nào đang chạy, kèm mọi nháp mới nhất (E-F1).
  useEffect(() => {
    if (!submitRequested || practice.pending !== null || !session) return
    setSubmitRequested(false)
    window.clearTimeout(pinyinTimerRef.current)
    unsavedRef.current.clear()
    const durations = Object.fromEntries(
      items.map((item) => [item.no, durationSince(firstShownRef.current[item.no])]),
    )
    void practice.finish(draftsToAnswers(items, draftsRef.current, durations)).then(() => {
      setConfirmOpen(false)
    })
  }, [items, practice, session, submitRequested])

  useEffect(() => {
    const error = practice.error
    if (!error || !DUPLICATE_ERRORS.has(error.errorCode) || syncedErrorRef.current === error) return
    syncedErrorRef.current = error
    setConfirmOpen(false)
    void practice.sync()
  }, [practice])

  useEffect(() => {
    if (practice.state !== 'finished' || !session) return
    if (refreshedSessionRef.current === session.sessionId) return
    refreshedSessionRef.current = session.sessionId
    onTopicFinished()
  }, [onTopicFinished, practice.state, session])

  const startFailure =
    practice.state === 'idle' && practice.error ? getTestStartFailure(practice.error) : undefined
  const retryAfter =
    startFailure?.kind === 'retry-later' ? startFailure.retryAfterSeconds : undefined

  useEffect(() => {
    if (retryAfter === undefined) return
    setWaitSeconds(retryAfter)
    const timer = window.setInterval(() => {
      setWaitSeconds((seconds) => {
        if (seconds <= 1) window.clearInterval(timer)
        return Math.max(0, seconds - 1)
      })
    }, 1000)
    return () => window.clearInterval(timer)
  }, [retryAfter])

  const error = practice.error
  const expired =
    practice.state === 'abandoned' || (error?.errorCode === 'SESSION_EXPIRED' && !startFailure)
  const duplicateSyncing =
    practice.pending === 'sync' || Boolean(error && DUPLICATE_ERRORS.has(error.errorCode))

  let result: TopicTestResult | undefined
  if (practice.finishResult) {
    const extra = readTestExtra(practice.finishResult)
    result = {
      firstCompletion: extra.firstCompletion ?? false,
      maxScore: practice.finishResult.maxScore,
      passed: extra.passed,
      percent: practice.finishResult.percent,
      score: practice.finishResult.score,
      wrongWords: extra.wrongWords ?? [],
    }
  }

  const state = (() => {
    if (practice.state === 'loading' || duplicateSyncing) return 'loading' as const
    if (practice.state === 'finished')
      return result ? ('finished' as const) : ('submitted' as const)
    if (expired) return 'expired' as const
    if (startFailure?.kind === 'not-enough') return 'not-enough' as const
    if (startFailure?.kind === 'retry-later') return 'retry-later' as const
    if (practice.state === 'idle' && !error) return 'intro' as const
    if (practice.state === 'ready') return 'ready' as const
    return 'error' as const
  })()

  const activeDraft = activeNo === undefined ? undefined : drafts[activeNo]

  return (
    <TopicTestStepView
      activeNo={activeNo}
      confirmOpen={confirmOpen}
      drafts={drafts}
      error={
        error
          ? {
              message:
                startFailure?.kind === 'other' ? startFailure.message : practiceErrorMessage(error),
              requestId: error.requestId,
            }
          : undefined
      }
      items={items}
      learnedWords={startFailure?.kind === 'not-enough' ? startFailure.learnedWords : undefined}
      onAudioFailed={(replayCount) => {
        if (activeNo !== undefined) {
          updateDraft(activeNo, { answer: undefined, audioFailed: true, replayCount }, 'now')
        }
      }}
      onCancelSubmit={() => {
        if (!submitRequested && practice.pending !== 'finish') setConfirmOpen(false)
      }}
      onChoose={(answer) => {
        if (activeNo !== undefined)
          updateDraft(activeNo, { answer, audioFailed: false, skipped: false }, 'now')
      }}
      onConfirmSubmit={() => setSubmitRequested(true)}
      onPinyinChange={(value) => {
        if (activeNo === undefined) return
        const hasAnswer = Boolean(value.trim())
        const skipped = !hasAnswer && clearableRef.current.has(activeNo)
        updateDraft(
          activeNo,
          { answer: value, skipped },
          hasAnswer || skipped ? 'debounce' : 'none',
        )
      }}
      onPracticeWrongWords={() =>
        navigate(topicStepPath(topic.id, 'recognition'), {
          state: { focusLexemeIds: result?.wrongWords.map((word) => word.lexemeId) ?? [] },
        })
      }
      onReplayCountChange={(count) => {
        if (activeNo !== undefined && count !== (activeDraft?.replayCount ?? 0)) {
          updateDraft(activeNo, { replayCount: count }, 'none')
        }
      }}
      onRequestSubmit={() => setConfirmOpen(true)}
      onRetry={practice.reload}
      onRetrySave={() => {
        failedSaveNoRef.current = undefined
        setFailedSaveNo(undefined)
        void flushDrafts()
      }}
      onSelectQuestion={(no) => {
        // Rời câu đang gõ thì lưu luôn, không chờ hết debounce.
        if (unsavedRef.current.size) {
          window.clearTimeout(pinyinTimerRef.current)
          debouncingNoRef.current = undefined
          void flushDrafts()
        }
        setActiveNo(no)
      }}
      onStart={() => {
        initializedSessionRef.current = undefined
        void practice.start()
      }}
      requiredWords={startFailure?.kind === 'not-enough' ? startFailure.requiredWords : undefined}
      result={result}
      saveFailed={failedSaveNo !== undefined}
      starting={practice.pending === 'start'}
      state={state}
      submitting={submitRequested || practice.pending === 'finish'}
      unanswered={countUnanswered(items, drafts)}
      waitSeconds={waitSeconds}
    />
  )
}
