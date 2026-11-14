import { apiRequest } from '@/shared/api'
import type { TopicLeaderboard } from '../types'

export function getQuizTopTen(topicId: number, signal?: AbortSignal): Promise<TopicLeaderboard> {
  if (!Number.isSafeInteger(topicId) || topicId <= 0) throw new Error('Mã chủ đề không hợp lệ.')
  return apiRequest<TopicLeaderboard>(`/api/public/leaderboards/topics/${topicId}?period=all`, {
    signal,
  })
}
