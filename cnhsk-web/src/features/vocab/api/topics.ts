import { apiRequest } from '@/shared/api'
import type { HskLevel } from '@/shared/ui'

import type { StartTopicStudyResult, TopicDetail, TopicSummary } from '../types'

export function getTopics(hskLevel?: HskLevel, signal?: AbortSignal) {
  const query = hskLevel ? `?hskLevel=${hskLevel}` : ''
  return apiRequest<TopicSummary[]>(`/api/topics${query}`, { signal })
}

export function getTopic(topicId: number, signal?: AbortSignal) {
  return apiRequest<TopicDetail>(`/api/topics/${encodeURIComponent(String(topicId))}`, { signal })
}

export function startTopicStudySession(topicId: number) {
  return apiRequest<StartTopicStudyResult>(
    `/api/topics/${encodeURIComponent(String(topicId))}/study-sessions`,
    { method: 'POST' },
  )
}
