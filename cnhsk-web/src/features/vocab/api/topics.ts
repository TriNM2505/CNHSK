import { apiRequest } from '@/shared/api'
import type { HskLevel } from '@/shared/ui'

import type { TopicSummary } from '../types'

export function getTopics(hskLevel?: HskLevel, signal?: AbortSignal) {
  const query = hskLevel ? `?hskLevel=${hskLevel}` : ''
  return apiRequest<TopicSummary[]>(`/api/topics${query}`, { signal })
}
