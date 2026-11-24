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

/** One skill or domain: `percent` is null while none of its nodes has enough evidence. */
export type MasteryGroup = {
  code: string
  nodes: number
  good: number
  missing: number
  percent: number | null
}
export type SkillMastery = { hskLevel: number; skills: MasteryGroup[]; domains: MasteryGroup[] }

export function getMySkillMastery(hskLevel: number, signal?: AbortSignal): Promise<SkillMastery> {
  return apiRequest<SkillMastery>(`/api/mastery/skills?hskLevel=${hskLevel}`, { signal })
}
