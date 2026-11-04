import { apiRequest } from '@/shared/api'

import type { MasteryNodeDetail, MasteryTree, Recommendation } from '../types'

export function getMyMastery(signal?: AbortSignal): Promise<MasteryTree> {
  return apiRequest<MasteryTree>('/api/mastery', { signal })
}

export function getMyMasteryNode(kid: string, signal?: AbortSignal): Promise<MasteryNodeDetail> {
  return apiRequest<MasteryNodeDetail>(`/api/mastery/nodes/${encodeURIComponent(kid)}`, { signal })
}

export function getMyRecommendations(signal?: AbortSignal): Promise<Recommendation[]> {
  return apiRequest<Recommendation[]>('/api/mastery/recommendations', { signal })
}
