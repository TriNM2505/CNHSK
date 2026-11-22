import { apiRequest } from '@/shared/api'

import type { GameOption, Leaderboard, LeaderboardPeriod, TopicOption } from '../types'

export function listLeaderboardTopics(hsk?: number, signal?: AbortSignal): Promise<TopicOption[]> {
  const query = hsk === undefined ? '' : `?hsk=${encodeURIComponent(hsk)}`
  return apiRequest<TopicOption[]>(`/api/public/leaderboards/topics${query}`, { signal })
}

export function getTopicLeaderboard(
  topicId: number,
  period: LeaderboardPeriod,
  signal?: AbortSignal,
): Promise<Leaderboard> {
  if (!Number.isSafeInteger(topicId) || topicId <= 0) throw new Error('Mã chủ đề không hợp lệ.')
  return apiRequest<Leaderboard>(
    `/api/public/leaderboards/topics/${topicId}?period=${encodeURIComponent(period)}`,
    { signal },
  )
}

export function listLeaderboardGames(signal?: AbortSignal): Promise<GameOption[]> {
  return apiRequest<GameOption[]>('/api/public/leaderboards/games', { signal })
}

export function getGameLeaderboard(
  gameCode: string,
  period: LeaderboardPeriod,
  hskLevel: number,
  signal?: AbortSignal,
): Promise<Leaderboard> {
  if (!/^[A-Z_]{2,30}$/.test(gameCode)) throw new Error('Mã game không hợp lệ.')
  return apiRequest<Leaderboard>(
    `/api/public/leaderboards/games/${encodeURIComponent(gameCode)}?period=${encodeURIComponent(period)}&hskLevel=${hskLevel}`,
    { signal },
  )
}
