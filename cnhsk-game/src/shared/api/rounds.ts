import { apiRequest } from './client'

export type ServedItem = {
  no: number
  type: string
  skill: string
  lexemeId?: number | null
  prompt: Record<string, unknown>
  options?: { key: string; text: string }[]
  rules: { minDurationMs: number; maxReplays: number; hasAudio: boolean }
}

export type RoundStart = {
  roundId: number
  gameCode: string
  serverTime: string
  startedAt: string
  deadline: string
  timeLimitSeconds: number
  pointsPerCorrect: number
  uiConfig: Record<string, unknown>
  items: ServedItem[]
}

export type RoundView = RoundStart & {
  status: 'IN_PROGRESS' | 'SUBMITTED' | 'ABANDONED' | 'EXPIRED'
  answered: { no: number; correct: boolean | null; correctAnswer: string | null }[]
  totalPoints: number
}

export type RoundResult = {
  roundId: number
  points: number
  maxPoints: number
  correct: number
  wrong: number
  missed: number
  accuracy: number | null
  durationMs: number
  skill: string
  bestPoints: number
  newBest: boolean
  suspicious: boolean
}

type RoundAnswer = { no: number; answer: string; durationMs: number }
type AnswerResult = {
  no: number
  correct: boolean
  correctAnswer: string
  pointsAwarded: number
  totalPoints: number
  answeredCount: number
  /** Wrong answers the round still tolerates; null when the game has no lives. */
  livesLeft: number | null
}

const base = '/api/community/games'
const roundPath = (id: number) => base + '/rounds/' + id

export function startRound(code: string, hskLevel: number, signal?: AbortSignal) {
  return apiRequest<RoundStart>(base + '/' + encodeURIComponent(code) + '/rounds', {
    method: 'POST',
    body: { hskLevel },
    signal,
  })
}

export function readRound(id: number, signal?: AbortSignal) {
  return apiRequest<RoundView>(roundPath(id), { signal })
}

export function answerRound(id: number, body: RoundAnswer, signal?: AbortSignal) {
  return apiRequest<AnswerResult>(roundPath(id) + '/answers', { method: 'POST', body, signal })
}

export function finishRound(id: number, signal?: AbortSignal) {
  return apiRequest<RoundResult>(roundPath(id) + '/finish', { method: 'POST', signal })
}

export function abandonRound(id: number, signal?: AbortSignal) {
  return apiRequest<void>(roundPath(id) + '/abandon', { method: 'POST', signal })
}
