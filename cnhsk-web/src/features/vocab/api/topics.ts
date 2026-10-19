import { apiRequest } from '@/shared/api'
import type { SessionView } from '@/features/practice'
import type { HskLevel } from '@/shared/ui'

import type {
  StartTopicPracticeRequest,
  StartTopicStudyResult,
  TopicDetail,
  TopicSummary,
} from '../types'

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

export function startTopicPracticeSession(topicId: number, request: StartTopicPracticeRequest) {
  return apiRequest<SessionView>(
    `/api/topics/${encodeURIComponent(String(topicId))}/practice-sessions`,
    { body: request, method: 'POST' },
  )
}

/** FR-031…FR-033: bắt đầu bài kiểm tra, hoặc nhận lại lượt đang làm. */
export function startTopicTest(topicId: number) {
  return apiRequest<SessionView>(`/api/topics/${encodeURIComponent(String(topicId))}/tests`, {
    method: 'POST',
  })
}
