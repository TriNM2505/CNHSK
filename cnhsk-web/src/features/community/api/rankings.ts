import { apiRequest } from '@/shared/api'

export type RankingPeriod = 'week' | 'month' | 'year'

export type RankingEntry = {
  rank: number
  userId: number
  displayName: string
  avatarUrl: string | null
  score: number
  /** Completion time of the ranked round; null for point totals. */
  durationMs?: number | null
}

export type CommunityRanking = {
  period: RankingPeriod
  entries: RankingEntry[]
  myEntry: RankingEntry | null
}

/** Game points summed over every game and HSK level for the current week, month or year. */
export function getCommunityRanking(period: RankingPeriod, signal?: AbortSignal): Promise<CommunityRanking> {
  return apiRequest<CommunityRanking>(
    `/api/public/community/rankings?period=${encodeURIComponent(period)}`,
    { signal },
  )
}
