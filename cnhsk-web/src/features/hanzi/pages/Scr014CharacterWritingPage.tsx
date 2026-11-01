import { useCallback, useEffect, useRef, useState } from 'react'
import { useMatch, useParams, useSearchParams } from 'react-router-dom'

import { usePracticeSession } from '@/features/practice'
import { toApiError, useApiQuery, type ApiError } from '@/shared/api'
import { SUPPORTED_HSK_LEVELS, type HskLevel } from '@/shared/ui'

import { getCharacter, getCharacters, recordWritingAttempt, startWritingChallenge } from '../api/characters'
import { CHARACTER_PAGE_SIZE, CharacterListView } from '../components/CharacterListView'
import { CharacterPracticeView } from '../components/CharacterPracticeView'
import {
  CHALLENGE_TIME_LIMIT_SECONDS,
  WritingChallengeView,
} from '../components/WritingChallengeView'
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
  return error ? { message: error.message, requestId: error.requestId } : undefined
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

  const changeMode = useCallback((nextMode: WritingMode) => {
    setAttemptState('idle')
    setAttemptError(undefined)
    setAttemptResult(null)
    setRetryAfterSeconds(undefined)
    pendingPayloadRef.current = null
    setMode(nextMode)
  }, [])

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
      onSubmitAttempt={(request) => void sendAttempt(request)}
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

function WritingChallengeController() {
  const start = useCallback(() => startWritingChallenge(), [])
  // FR-023: lượt thử thách đang làm được khôi phục thay vì tạo lượt mới.
  const practice = usePracticeSession({ refId: null, source: 'WRITING_CHALLENGE', start })
  const [now, setNow] = useState(() => Date.now())

  const session = practice.session
  const limitSeconds =
    typeof session?.meta?.timeLimitSeconds === 'number'
      ? session.meta.timeLimitSeconds
      : CHALLENGE_TIME_LIMIT_SECONDS

  useEffect(() => {
    if (practice.state !== 'ready') return undefined
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [practice.state])

  const error = practice.error
  const notEnough =
    error?.errorCode === 'NOT_ENOUGH_CHARACTERS'
      ? {
          have: numberFromError(error, 'have') ?? 0,
          need: numberFromError(error, 'need') ?? 10,
        }
      : undefined
  const remainingSeconds = session
    ? (Date.parse(session.servedAt) + limitSeconds * 1000 - now) / 1000
    : limitSeconds

  const state =
    practice.state === 'loading' || practice.pending === 'start'
      ? 'loading'
      : practice.state === 'error'
        ? 'error'
        : practice.state === 'ready'
          ? 'ready'
          : 'intro'

  return (
    <WritingChallengeView
      current={1}
      error={notEnough ? undefined : toViewError(error ?? undefined)}
      item={session?.items[0]}
      notEnough={notEnough}
      onRetry={practice.reload}
      onStart={() => void practice.start()}
      pending={practice.pending !== null}
      remainingSeconds={remainingSeconds}
      state={state}
      total={session?.items.length}
    />
  )
}
