import { apiRequest } from '@/shared/api'

export type PlacementLevelCount = {
  level: number
  approved: number
  pending: number
  needed: number
}
export type PlacementFormState = { id: number; code: string; status: string; questions: number }
export type PlacementPool = { levels: PlacementLevelCount[]; forms: PlacementFormState[] }
export type PlacementBuildResult = { built: string[]; kept: string[] }

export function getPlacementPool(signal?: AbortSignal) {
  return apiRequest<PlacementPool>('/api/admin/placement/pool', { signal })
}

/** Starts AI drafting of placement questions for one HSK level; they go to the two-step review. */
export function draftPlacementQuestions(level: number, count: number) {
  return apiRequest<{ started: boolean }>('/api/admin/placement/drafts', {
    method: 'POST',
    body: { level, count },
  })
}

export function buildPlacementForms() {
  return apiRequest<PlacementBuildResult>('/api/admin/placement/forms', { method: 'POST' })
}
