import { apiRequest } from '@/shared/api'
import type {
  GradingPolicy,
  GradingRequest,
  GradingRequestPage,
  GradingRequestWrite,
  ListParams,
} from '../types'

export function getGradingPolicy(signal?: AbortSignal): Promise<GradingPolicy> {
  return apiRequest<GradingPolicy>('/api/grading/policy', { signal })
}

export function createGradingRequest(body: GradingRequestWrite): Promise<GradingRequest> {
  return apiRequest<GradingRequest>('/api/grading-requests', { method: 'POST', body })
}

export function listMyGradingRequests(
  { status, page = 0, size = 20 }: ListParams = {},
  signal?: AbortSignal,
): Promise<GradingRequestPage> {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  if (status) query.set('status', status)
  return apiRequest<GradingRequestPage>(`/api/grading-requests?${query}`, { signal })
}

export function getMyGradingRequest(id: string, signal?: AbortSignal): Promise<GradingRequest> {
  return apiRequest<GradingRequest>(`/api/grading-requests/${encodeURIComponent(id)}`, { signal })
}

export function cancelGradingRequest(id: string): Promise<GradingRequest> {
  return apiRequest<GradingRequest>(`/api/grading-requests/${encodeURIComponent(id)}/cancel`, {
    method: 'POST',
  })
}
