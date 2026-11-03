import { apiRequest } from '@/shared/api'

import type {
  PlacementResult,
  PlacementStartRequest,
  PlacementStep,
  PlacementSummary,
} from '../types'

function runPath(runId: number, suffix = ''): string {
  return `/api/placement/runs/${encodeURIComponent(String(runId))}${suffix}`
}

export function getPlacementSummary(signal?: AbortSignal): Promise<PlacementSummary> {
  return apiRequest<PlacementSummary>('/api/placement/summary', { signal })
}

export function startPlacementRun(request?: PlacementStartRequest): Promise<PlacementStep> {
  return apiRequest<PlacementStep>('/api/placement/runs', { body: request, method: 'POST' })
}

export function nextPlacementBlock(runId: number): Promise<PlacementStep> {
  return apiRequest<PlacementStep>(runPath(runId, '/next'), { method: 'POST' })
}

export function getPlacementResult(runId: number, signal?: AbortSignal): Promise<PlacementResult> {
  return apiRequest<PlacementResult>(runPath(runId, '/result'), { signal })
}
