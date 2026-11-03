import { apiRequest } from '@/shared/api'

import type { PlanCode, PlanUpdateRequest, PlanView } from '../types'

export function listPlans(signal?: AbortSignal): Promise<PlanView[]> {
  return apiRequest<PlanView[]>('/api/admin/plans', { signal })
}

export function updatePlan(code: PlanCode, body: PlanUpdateRequest): Promise<PlanView> {
  return apiRequest<PlanView>(`/api/admin/plans/${encodeURIComponent(code)}`, {
    body,
    method: 'PUT',
  })
}
