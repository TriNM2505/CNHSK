import { useCallback, useEffect, useRef, useState } from 'react'
import { useMatch, useParams, useSearchParams } from 'react-router-dom'

import { finishSession, getSession, usePracticeSession } from '@/features/practice'
import type { AnswerRequest, FinishResult, SessionView } from '@/features/practice'
import { toApiError, useApiQuery, type ApiError } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { SUPPORTED_HSK_LEVELS, type HskLevel } from '@/shared/ui'

import { getCharacter, getCharacters, recordWritingAttempt, startWritingChallenge } from '../api/characters'
import { CHARACTER_PAGE_SIZE, CharacterListView } from '../components/CharacterListView'
import { CharacterPracticeView } from '../components/CharacterPracticeView'
import {
  CHALLENGE_MAX_MISTAKES,
  CHALLENGE_SIZE,
  CHALLENGE_TIME_LIMIT_SECONDS,
  WritingChallengeView,
} from '../components/WritingChallengeView'
import type { HanziCanvasHandle } from '../components/HanziCanvas'
import type { WritingAttemptRequest, WritingAttemptResult, WritingMode } from '../types'
import { defaultWritingMode } from '../writingModes'

const CHALLENGE_PATH = '/learn/characters/writing/challenge'

export default function Scr014CharacterWritingPage() {
  const { lexemeId } = useParams()
  const onChallengeRoute = useMatch(CHALLENGE_PATH) !== null

  if (onChallengeRoute) return <WritingChallengeController />
  if (lexemeId !== undefined) return <CharacterPracticeController key={lexemeId} rawId={lexemeId} />
  return <CharacterListController />
}

function parseLevel(value: string | null): HskLevel | undefined {
  return SUPPORTED_HSK_LEVELS.find((level) => String(level) === value)
}

function parsePage(value: string | null) {
  const page = Number(value)
  return Number.isSafeInteger(page) && page >= 1 ? page : 1
}

function toViewError(error: ApiError | undefined) {
  return error ? { errorCode: error.errorCode, message: error.message, requestId: error.requestId } : undefined
}

function CharacterListController() {
  const [searchParams, setSearchParams] = useSearchParams()
  const q = (searchParams.get('q') ?? '').slice(0, 20)
  const hskLevel = parseLevel(searchParams.get('hskLevel'))
  const page = parsePage(searchParams.get('page'))

  const query = useApiQuery(
    (signal) =>
      getCharacters({ hskLevel, page: page - 1, q: q || undefined, size: CHARACTER_PAGE_SIZE }, signal),
    [hskLevel, page, q],
    { isEmpty: (data) => data.items.length === 0 },
  )

  // Đổi bộ lọc thì quay về trang 1.
  const update = (changes: { hskLevel?: HskLevel; page?: number; q?: string }) => {
    const next = new URLSearchParams()
    const nextQ = 'q' in changes ? changes.q : q
    const nextLevel = 'hskLevel' in changes ? changes.hskLevel : hskLevel
    if (nextQ) next.set('q', nextQ)
    if (nextLevel) next.set('hskLevel', String(nextLevel))
    if (changes.page && changes.page > 1) next.set('page', String(changes.page))
    setSearchParams(next)
  }

  return (
    <CharacterListView
      characters={query.data?.items ?? []}
      error={toViewError(query.error)}
      hskLevel={hskLevel}
      onHskLevelChange={(level) => update({ hskLevel: level })}
      onPageChange={(nextPage) => update({ page: nextPage })}
      onRetry={query.reload}
      onSearch={(nextQ) => update({ q: nextQ })}
      page={page}
      q={q}
      size={CHARACTER_PAGE_SIZE}
      state={query.state}
      total={query.data?.totalElements ?? 0}
    />
  )
}

function CharacterPracticeController({ rawId }: { rawId: string }) {
  const characterId = /^[1-9]\d*$/.test(rawId) ? Number(rawId) : undefined
  const [mode, setMode] = useState<WritingMode>()
  const [attemptState, setAttemptState] = useState<'idle' | 'submitting' | 'recorded' | 'unknown' | 'rejected'>('idle')
  const [attemptError, setAttemptError] = useState<ApiError | undefined>()
  const [attemptResult, setAttemptResult] = useState<WritingAttemptResult | null>(null)
  const [retryAfterSeconds, setRetryAfterSeconds] = useState<number>()
  const [restartKey, setRestartKey] = useState(0)
  const pendingPayloadRef = useRef<WritingAttemptRequest | null>(null)
  const requestInFlightRef = useRef(false)
  const query = useApiQuery(
    (signal) =>
      characterId === undefined
        ? Promise.reject(new Error('invalid-id'))
        : getCharacter(characterId, signal),
    [characterId],
  )
  const reloadCharacter = query.reload

  useEffect(() => {
    if (query.data && mode === undefined) setMode(defaultWritingMode(query.data))
  }, [mode, query.data])

  const sendAttempt = useCallback(async (request: WritingAttemptRequest) => {
    if (requestInFlightRef.current) return
    requestInFlightRef.current = true
    pendingPayloadRef.current = request
    setAttemptState('submitting')
    setAttemptError(undefined)
    setRetryAfterSeconds(undefined)
    try {
      const result = await recordWritingAttempt(request)
      setAttemptResult(result)
      setAttemptState('recorded')
    } catch (caught: unknown) {
      const apiError = toApiError(caught)
      setAttemptError(apiError)
      if (apiError.status === 0 || apiError.status >= 500) {
        setAttemptState('unknown')
      } else {
        setAttemptState('rejected')
      }
      if (apiError.errorCode === 'RATE_LIMITED' && typeof apiError.extra.retryAfterSeconds === 'number') {
        setRetryAfterSeconds(apiError.extra.retryAfterSeconds)
      }
      if (apiError.errorCode === 'MODE_LOCKED') {
        reloadCharacter()
        setMode('STROKE')
      }
    } finally {
      requestInFlightRef.current = false
    }
  }, [reloadCharacter])

  const retryUnknown = useCallback(() => {
    if (attemptState !== 'unknown' || !pendingPayloadRef.current || requestInFlightRef.current) return
    void sendAttempt(pendingPayloadRef.current)
  }, [attemptState, sendAttempt])

  const clearAttempt = useCallback(() => {
    setAttemptState('idle')
    setAttemptError(undefined)
    setAttemptResult(null)
    setRetryAfterSeconds(undefined)
    pendingPayloadRef.current = null
  }, [])

  const restartPractice = useCallback(() => {
    clearAttempt()
    setRestartKey((current) => current + 1)
  }, [clearAttempt])

  const changeMode = useCallback((nextMode: WritingMode) => {
    if (nextMode === mode) {
      if (attemptState === 'recorded' || attemptState === 'rejected') {
        clearAttempt()
        setRestartKey((current) => current + 1)
      }
      return
    }
    clearAttempt()
    setMode(nextMode)
  }, [attemptState, clearAttempt, mode])

  const notFound =
    characterId === undefined ||
    query.error?.errorCode === 'CHARACTER_NOT_FOUND' ||
    query.error?.status === 404

  const character = query.data && attemptResult
    ? { ...query.data, writing: { ...query.data.writing, recallUnlocked: attemptResult.recallUnlocked } }
    : query.data

  return (
    <CharacterPracticeView
      attemptError={attemptError ? { errorCode: attemptError.errorCode, message: attemptError.message } : undefined}
      attemptResult={attemptResult}
      attemptState={attemptState}
      character={character}
      error={toViewError(query.error)}
      mode={mode}
      onModeChange={changeMode}
      onRetry={query.reload}
      onRetryUnknown={retryUnknown}
      onRestart={restartPractice}
      onSubmitAttempt={(request) => void sendAttempt(request)}
      restartKey={restartKey}
      retryAfterSeconds={retryAfterSeconds}
      state={
        notFound ? 'not-found' : query.state === 'ready' ? 'ready' : query.state === 'loading' ? 'loading' : 'error'
      }
    />
  )
}

function numberFromError(error: ApiError | null, key: string) {
  if (!error) return undefined
  const direct = error.extra[key]
  if (typeof direct === 'number') return direct
  if (error.details && typeof error.details === 'object' && !Array.isArray(error.details)) {
    const detail = (error.details as Record<string, unknown>)[key]
    if (typeof detail === 'number') return detail
  }
  return undefined
}

type ChallengeAnswer = AnswerRequest & { answer: 'COMPLETED' | 'FAILED' | 'INCOMPLETE' }

interface StoredChallenge {
  answers: ChallengeAnswer[]
  finishPayload?: ChallengeAnswer[]
  sessionId: number
  userId: number
  version: 1
}

const CHALLENGE_STORAGE_PREFIX = 'cnhsk:hanzi:challenge'

function challengeStorageKey(userId: number, sessionId: number) {
  return `${CHALLENGE_STORAGE_PREFIX}:${userId}:${sessionId}`
}

function readStoredChallenge(userId: number, sessionId: number): StoredChallenge | null {
  try {
    const raw = sessionStorage.getItem(challengeStorageKey(userId, sessionId))
    if (!raw) return null
    const value: unknown = JSON.parse(raw)
    if (!value || typeof value !== 'object') return null
    const candidate = value as Partial<StoredChallenge>
    if (candidate.version !== 1 || candidate.userId !== userId || candidate.sessionId !== sessionId
        || !Array.isArray(candidate.answers)) return null
    const answers = candidate.answers.filter(isChallengeAnswer)
    const finishPayload = Array.isArray(candidate.finishPayload)
      ? candidate.finishPayload.filter(isChallengeAnswer)
      : undefined
    return { answers, finishPayload, sessionId, userId, version: 1 }
  } catch {
    return null
  }
}

function isChallengeAnswer(value: unknown): value is ChallengeAnswer {
  if (!value || typeof value !== 'object') return false
  const answer = value as Partial<ChallengeAnswer>
  return Number.isInteger(answer.no) && ['COMPLETED', 'FAILED', 'INCOMPLETE'].includes(String(answer.answer))
    && Number.isInteger(answer.durationMs) && Boolean(answer.clientMeta)
}

function writeStoredChallenge(value: StoredChallenge) {
  try {
    sessionStorage.setItem(challengeStorageKey(value.userId, value.sessionId), JSON.stringify(value))
  } catch {
    // Storage can be disabled by the browser; the active in-memory challenge still works.
  }
}

function clearStoredChallenge(userId: number, sessionId: number) {
  try {
    sessionStorage.removeItem(challengeStorageKey(userId, sessionId))
  } catch {
    // A storage failure must not block the server-confirmed result.
  }
}

function buildRestoredFinishResult(session: SessionView): FinishResult {
  const completedChars = session.answered.filter((answer) => answer.answer === 'COMPLETED').length
  const failedChars = session.answered.filter((answer) => answer.answer === 'FAILED').length
  const incompleteChars = CHALLENGE_SIZE - completedChars - failedChars
  const finishedReason = failedChars === CHALLENGE_MAX_MISTAKES
    ? 'MAX_MISTAKES'
    : completedChars + failedChars === CHALLENGE_SIZE
      ? 'ALL_DONE'
      : 'TIME_UP'
  return {
    extra: { completedChars, failedChars, incompleteChars, total: CHALLENGE_SIZE, finishedReason },
    items: session.items.map((item) => {
      const answered = session.answered.find((answer) => answer.no === item.no)
      return { no: item.no, correct: null, yourAnswer: answered?.answer ?? null }
    }),
    maxScore: null,
    percent: null,
    score: null,
    sessionId: session.sessionId,
    status: 'SUBMITTED',
    suspicious: false,
  }
}

function challengePayload(
  session: SessionView,
  currentNo: number | undefined,
  answers: ChallengeAnswer[],
  currentDurationMs: number,
  currentMistakes: number,
) {
  const answeredNos = new Set(answers.map((answer) => answer.no))
  const current = session.items.find((item) => item.no === currentNo)
  const timeoutAnswer = current && !answeredNos.has(current.no)
    ? [makeChallengeAnswer(current, 'INCOMPLETE', currentDurationMs, currentMistakes)]
    : []
  return [...answers, ...timeoutAnswer]
}

function makeChallengeAnswer(
  item: SessionView['items'][number],
  answer: ChallengeAnswer['answer'],
  durationMs: number,
  totalMistakes: number,
): ChallengeAnswer {
  const rawStrokeCount = item.prompt.strokeCount
  const strokeCount = typeof rawStrokeCount === 'number' && rawStrokeCount > 0 ? rawStrokeCount : 0
  const accuracy = strokeCount > 0
    ? Math.round(Math.max(0, 1 - totalMistakes / strokeCount) * 100) / 100
    : 0
  return {
    answer,
    clientMeta: { accuracy, strokeMisses: Math.min(totalMistakes, strokeCount * 10) },
    durationMs: Math.min(Math.max(Math.round(durationMs), 0), 3_600_000),
    no: item.no,
    replayCount: 0,
  }
}

function WritingChallengeController() {
  const { user } = useCurrentUser()
  const userId = user?.id
  const canvasRef = useRef<HanziCanvasHandle>(null)
  const quizGenerationRef = useRef(0)
  const activeNoRef = useRef<number>()
  const characterStartedAtRef = useRef<number | null>(null)
  const finishStartedRef = useRef(false)
  const storageRecoveryStartedRef = useRef(false)
  const storedFinishRef = useRef<{ sessionId: number; payload: ChallengeAnswer[] } | null>(null)
  const answersRef = useRef<ChallengeAnswer[]>([])
  const mistakesRef = useRef(0)
  const [now, setNow] = useState(() => Date.now())
  const [answers, setAnswers] = useState<ChallengeAnswer[]>([])
  const [answersHydrated, setAnswersHydrated] = useState(false)
  const [currentMistakes, setCurrentMistakes] = useState(0)
  const [canvasReady, setCanvasReady] = useState(false)
  const [dataError, setDataError] = useState(false)
  const [finished, setFinished] = useState<FinishResult | null>(null)
  const [finishPending, setFinishPending] = useState(false)
  const [finishUnknown, setFinishUnknown] = useState(false)
  const [sessionExpired, setSessionExpired] = useState(false)
  const [challengeError, setChallengeError] = useState<ApiError | null>(null)
  const [finishStopped, setFinishStopped] = useState(false)
  const [dataErrorNotice, setDataErrorNotice] = useState<string>()
  const start = useCallback(() => startWritingChallenge(), [])
  const practice = usePracticeSession({ refId: null, source: 'WRITING_CHALLENGE', start })
  const session = practice.session

  useEffect(() => () => {
    quizGenerationRef.current += 1
    canvasRef.current?.cancelQuiz()
    canvasRef.current?.cancelAnimations()
  }, [])

  answersRef.current = answers
  activeNoRef.current = session?.items.find((item) => !answers.some((answer) => answer.no === item.no))?.no

  const persistAnswers = useCallback((nextAnswers: ChallengeAnswer[], finishPayload?: ChallengeAnswer[]) => {
    answersRef.current = nextAnswers
    setAnswers(nextAnswers)
    if (userId && session) {
      writeStoredChallenge({
        answers: nextAnswers,
        ...(finishPayload ? { finishPayload } : {}),
        sessionId: session.sessionId,
        userId,
        version: 1,
      })
    }
  }, [session, userId])

  const reconcileSubmitted = useCallback(async (sessionId: number, expectedUserId: number) => {
    try {
      const restored = await getSession(sessionId)
      if (restored.status === 'SUBMITTED') {
        clearStoredChallenge(expectedUserId, sessionId)
        storedFinishRef.current = null
        setFinished(buildRestoredFinishResult(restored))
        setFinishUnknown(false)
        return true
      }
      if (restored.status === 'EXPIRED' || restored.status === 'ABANDONED') {
        clearStoredChallenge(expectedUserId, sessionId)
        storedFinishRef.current = null
        setSessionExpired(true)
        setFinishUnknown(false)
        return true
      }
      return false
    } catch (caught: unknown) {
      const apiError = toApiError(caught)
      if (apiError.errorCode === 'SESSION_EXPIRED') {
        clearStoredChallenge(expectedUserId, sessionId)
        storedFinishRef.current = null
        setSessionExpired(true)
        setFinishUnknown(false)
        return true
      }
      throw caught
    }
  }, [])

  useEffect(() => {
    if (!session || !userId || practice.state !== 'ready') return
    const stored = readStoredChallenge(userId, session.sessionId)
    const restoredAnswers = stored?.answers ?? []
    persistAnswers(restoredAnswers, stored?.finishPayload)
    if (stored?.finishPayload) {
      storedFinishRef.current = { sessionId: session.sessionId, payload: stored.finishPayload }
      setFinishUnknown(true)
      finishStartedRef.current = false
    }
    setAnswersHydrated(true)
    storageRecoveryStartedRef.current = false
  }, [persistAnswers, practice.state, session, userId])

  useEffect(() => {
    if (!session || !userId) return
    if (session.status === 'SUBMITTED' || practice.state === 'finished') {
      clearStoredChallenge(userId, session.sessionId)
      storedFinishRef.current = null
      setFinished(buildRestoredFinishResult(session))
      setFinishUnknown(false)
      return
    }
    if (session.status === 'EXPIRED' || session.status === 'ABANDONED' || practice.state === 'abandoned') {
      clearStoredChallenge(userId, session.sessionId)
      storedFinishRef.current = null
      setSessionExpired(true)
      setFinishUnknown(false)
    }
  }, [practice.state, session, userId])

  useEffect(() => {
    if (!userId || session || !['idle', 'error'].includes(practice.state) || storageRecoveryStartedRef.current) return
    storageRecoveryStartedRef.current = true
    let pendingRecord: StoredChallenge | null = null
    try {
      const prefix = `${CHALLENGE_STORAGE_PREFIX}:${userId}:`
      for (let index = 0; index < sessionStorage.length; index += 1) {
        const key = sessionStorage.key(index)
        if (!key?.startsWith(prefix)) continue
        const sessionId = Number(key.slice(prefix.length))
        if (!Number.isSafeInteger(sessionId) || sessionId <= 0) continue
        const record = readStoredChallenge(userId, sessionId)
        if (record?.finishPayload) {
          pendingRecord = record
          break
        }
      }
    } catch {
      pendingRecord = null
    }
    if (!pendingRecord?.finishPayload) return
    storedFinishRef.current = { sessionId: pendingRecord.sessionId, payload: pendingRecord.finishPayload }
    setFinishUnknown(true)
    void reconcileSubmitted(pendingRecord.sessionId, userId).catch((caught: unknown) => {
      setChallengeError(toApiError(caught))
    })
  }, [practice.state, reconcileSubmitted, session, userId])

  const submitFinish = useCallback(async (payload: ChallengeAnswer[], sessionId = session?.sessionId) => {
    if (!sessionId || !userId || finishStartedRef.current) return
    finishStartedRef.current = true
    setFinishPending(true)
    setChallengeError(null)
    quizGenerationRef.current += 1
    canvasRef.current?.cancelQuiz()
    storedFinishRef.current = { sessionId, payload }
    writeStoredChallenge({
      answers: payload,
      finishPayload: payload,
      sessionId,
      userId,
      version: 1,
    })
    try {
      const result = await finishSession(sessionId, { answers: payload })
      clearStoredChallenge(userId, sessionId)
      storedFinishRef.current = null
      setFinished(result)
      setFinishUnknown(false)
      finishStartedRef.current = true
    } catch (caught: unknown) {
      const apiError = toApiError(caught)
      setChallengeError(apiError)
      if (apiError.errorCode === 'SESSION_EXPIRED') {
        clearStoredChallenge(userId, sessionId)
        storedFinishRef.current = null
        setSessionExpired(true)
        setFinishUnknown(false)
      } else if (apiError.errorCode === 'SESSION_ALREADY_SUBMITTED') {
        try {
          const reconciled = await reconcileSubmitted(sessionId, userId)
          if (!reconciled) setFinishUnknown(true)
        } catch (syncError: unknown) {
          setChallengeError(toApiError(syncError))
          setFinishUnknown(true)
        }
      } else if (apiError.status === 0 || apiError.status >= 500 || apiError.status === 429) {
        setFinishUnknown(true)
      } else {
        setFinishUnknown(false)
        setFinishStopped(true)
      }
      finishStartedRef.current = false
    } finally {
      setFinishPending(false)
    }
  }, [reconcileSubmitted, session?.sessionId, userId])

  const markItem = useCallback((answer: ChallengeAnswer['answer'], totalMistakes: number) => {
    if (!session || finishStartedRef.current) return
    const item = session.items.find((candidate) => candidate.no === activeNoRef.current)
    if (!item || answersRef.current.some((saved) => saved.no === item.no)) return
    quizGenerationRef.current += 1
    canvasRef.current?.cancelQuiz()
    const durationMs = characterStartedAtRef.current === null ? 0 : performance.now() - characterStartedAtRef.current
    characterStartedAtRef.current = null
    const strokeCount = typeof item.prompt.strokeCount === 'number' ? item.prompt.strokeCount : 0
    const finalAnswer: ChallengeAnswer['answer'] = answer === 'COMPLETED' && durationMs < strokeCount * 200
      ? 'INCOMPLETE'
      : answer
    const result = makeChallengeAnswer(item, finalAnswer, durationMs, totalMistakes)
    const nextAnswers = [...answersRef.current, result]
    persistAnswers(nextAnswers)
    mistakesRef.current = 0
    setCurrentMistakes(0)
    setCanvasReady(false)
    setDataError(false)
    const failedCount = nextAnswers.filter((saved) => saved.answer === 'FAILED').length
    const allHandled = nextAnswers.length === session.items.length
    if (failedCount >= CHALLENGE_MAX_MISTAKES || allHandled) {
      void submitFinish(nextAnswers, session.sessionId)
    }
  }, [persistAnswers, session, submitFinish])

  const startQuiz = useCallback(() => {
    const item = session?.items.find((candidate) => candidate.no === activeNoRef.current)
    if (!item || !canvasReady || dataError || finishStopped || finishStartedRef.current) return
    quizGenerationRef.current += 1
    const generation = quizGenerationRef.current
    mistakesRef.current = 0
    setCurrentMistakes(0)
    characterStartedAtRef.current = performance.now()
    canvasRef.current?.quiz({
      quizStartStrokeNum: 0,
      showHintAfterMisses: false,
      onMistake: (stroke) => {
        if (generation !== quizGenerationRef.current || finishStartedRef.current) return
        mistakesRef.current = stroke.totalMistakes
        setCurrentMistakes(stroke.totalMistakes)
        if (stroke.totalMistakes >= 3) markItem('FAILED', stroke.totalMistakes)
      },
      onComplete: (summary) => {
        if (generation !== quizGenerationRef.current || finishStartedRef.current) return
        markItem('COMPLETED', summary.totalMistakes)
      },
    })
  }, [canvasReady, dataError, finishStopped, markItem, session])

  useEffect(() => {
    if (!session || practice.state !== 'ready' || !userId || finished || sessionExpired) return undefined
    const updateNow = () => setNow(Date.now())
    const timer = window.setInterval(updateNow, 250)
    document.addEventListener('visibilitychange', updateNow)
    return () => {
      window.clearInterval(timer)
      document.removeEventListener('visibilitychange', updateNow)
    }
  }, [finished, practice.state, session, sessionExpired, userId])

  const limitSeconds = CHALLENGE_TIME_LIMIT_SECONDS
  const deadline = session ? Date.parse(session.servedAt) + limitSeconds * 1000 : 0
  const remainingSeconds = session ? Math.max(0, (deadline - now) / 1000) : limitSeconds

  useEffect(() => {
    if (!session || !answersHydrated || practice.state !== 'ready' || remainingSeconds > 0
        || finished || sessionExpired || finishUnknown || finishStopped || finishStartedRef.current) return
    const duration = characterStartedAtRef.current === null
      ? 0
      : performance.now() - characterStartedAtRef.current
    const payload = challengePayload(session, activeNoRef.current, answersRef.current, duration, mistakesRef.current)
    persistAnswers(payload)
    quizGenerationRef.current += 1
    canvasRef.current?.cancelQuiz()
    void submitFinish(payload, session.sessionId)
  }, [answersHydrated, finishStopped, finishUnknown, finished, persistAnswers, practice.state, remainingSeconds, session, sessionExpired, submitFinish])

  useEffect(() => {
    if (!session || practice.state !== 'ready' || !answersHydrated || !canvasReady || dataError
        || finishStopped || finishUnknown || finishStartedRef.current) return
    if (answersRef.current.some((answer) => answer.no === activeNoRef.current)) return
    startQuiz()
  }, [answersHydrated, canvasReady, dataError, finishStopped, finishUnknown, practice.state, session, startQuiz])

  const handleDataError = useCallback(() => {
    if (!session || finishStartedRef.current || dataError) return
    setDataError(true)
    setDataErrorNotice('Chữ này chưa có dữ liệu nét')
    markItem('INCOMPLETE', mistakesRef.current)
  }, [dataError, markItem, session])

  const handleCanvasReady = useCallback(() => {
    setCanvasReady(true)
    setDataErrorNotice(undefined)
  }, [])

  const handleRetryUnknown = useCallback(async () => {
    const pending = storedFinishRef.current
    if (!pending || !userId || finishStartedRef.current) return
    finishStartedRef.current = true
    setFinishPending(true)
    try {
      const terminal = await reconcileSubmitted(pending.sessionId, userId)
      if (terminal) return
      finishStartedRef.current = false
      setFinishPending(false)
      await submitFinish(pending.payload, pending.sessionId)
    } catch (caught: unknown) {
      setChallengeError(toApiError(caught))
      setFinishUnknown(true)
    } finally {
      finishStartedRef.current = false
      setFinishPending(false)
    }
  }, [reconcileSubmitted, submitFinish, userId])

  const error = practice.error
  const notEnough = error?.errorCode === 'NOT_ENOUGH_CHARACTERS'
    ? { have: numberFromError(error, 'have') ?? 0, need: numberFromError(error, 'need') ?? CHALLENGE_SIZE }
    : undefined
  const answeredNos = new Set(answers.map((answer) => answer.no))
  const currentIndex = session?.items.findIndex((item) => !answeredNos.has(item.no)) ?? -1
  const currentItem = currentIndex >= 0 ? session?.items[currentIndex] : undefined
  const failedCount = answers.filter((answer) => answer.answer === 'FAILED').length
  const completedCount = answers.filter((answer) => answer.answer === 'COMPLETED').length

  const state = finishUnknown
    ? 'unknown'
    : sessionExpired
      ? 'expired'
      : practice.state === 'loading' || practice.pending === 'start'
        ? 'loading'
        : practice.state === 'error' && !session
          ? 'error'
          : practice.state === 'ready'
            ? 'ready'
            : 'intro'

  return (
    <WritingChallengeView
      canvasRef={canvasRef}
      completed={completedCount}
      current={currentIndex >= 0
        ? currentIndex + 1
        : Math.min(session?.items.length ?? CHALLENGE_SIZE, Math.max(1, answers.length))}
      currentMistakes={currentMistakes}
      dataError={dataError}
      error={toViewError(challengeError ?? error ?? undefined)}
      failed={failedCount}
      finishResult={finished}
      item={currentItem}
      notEnough={notEnough}
      notice={dataErrorNotice}
      onCanvasReady={handleCanvasReady}
      onDataError={handleDataError}
      onRetry={practice.reload}
      onRetryFinish={() => void handleRetryUnknown()}
      onStart={() => void practice.start()}
      pending={finishPending || practice.pending !== null}
      processed={answers.length}
      retryAfterSeconds={typeof challengeError?.extra.retryAfterSeconds === 'number'
        ? challengeError.extra.retryAfterSeconds
        : undefined}
      remainingSeconds={remainingSeconds}
      state={state}
      total={session?.items.length ?? CHALLENGE_SIZE}
    />
  )
}
