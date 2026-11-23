import { apiRequest } from '@/shared/api'

import type { MediaItem } from '../types'

/** WAITING before the start, OPEN while rounds are served, TALLYING while the last rounds finish, then FINISHED. */
export type GameContestPhase = 'WAITING' | 'OPEN' | 'TALLYING' | 'FINISHED'

export type GameContestRankingRow = {
  rank: number
  userId: number
  displayName: string | null
  points: number
  durationMs: number | null
  achievedAt: string
}

export type GameContestLive = {
  id: number
  title: string
  description: string | null
  gameCode: string
  startsAt: string
  endsAt: string
  /** When the ranking becomes final, after the last rounds have had time to be submitted. */
  resultsAt: string
  prizeCount: number
  serverTime: string
  phase: GameContestPhase
  participants: number
  ranking: GameContestRankingRow[]
}

export type GameContestWinner = GameContestRankingRow & { email: string }

export type GameContestExcluded = {
  userId: number
  displayName: string | null
  note: string
  decidedAt: string
}

export type GameContestResults = {
  contest: GameContestLive
  winners: GameContestWinner[]
  excluded: GameContestExcluded[]
}

export type GameContestWrite = {
  title: string
  content: string
  gameCode: string
  startsAt: string
  endsAt: string
  prizeCount: number
  media: MediaItem[]
}

export function createGameContest(body: GameContestWrite): Promise<{ contestId: number; postId: number }> {
  return apiRequest('/api/community/moderation/contests', { body, method: 'POST' })
}

export function getGameContestLive(id: number, signal?: AbortSignal): Promise<GameContestLive> {
  return apiRequest<GameContestLive>(`/api/public/community/contests/${id}/live`, { signal })
}

export function getGameContestResults(id: number, signal?: AbortSignal): Promise<GameContestResults> {
  return apiRequest<GameContestResults>(`/api/community/moderation/contests/${id}/results`, { signal })
}

export function exportGameContestResults(id: number) {
  return apiRequest(`/api/community/moderation/contests/${id}/results.csv`, { responseType: 'blob' })
}

export function decideGameContestExclusion(id: number, userId: number, excluded: boolean, note: string): Promise<void> {
  return apiRequest<void>(`/api/community/moderation/contests/${id}/exclusions/${userId}`, {
    body: { excluded, note },
    method: 'PUT',
  })
}

/** Where the game app serves a contest round for this game. */
export function contestPlayUrl(gameOrigin: string, gameCode: string, contestId: number): string {
  const slug = gameCode.toLowerCase().replace(/_/g, '-')
  return `${gameOrigin.replace(/\/$/, '')}/play/${slug}?contest=${contestId}`
}
