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

export function buildPlacementForms() {
  return apiRequest<PlacementBuildResult>('/api/admin/placement/forms', { method: 'POST' })
}
