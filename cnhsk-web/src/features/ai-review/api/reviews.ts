import { apiRequest } from '@/shared/api'

import type { AIQuestionPage, BulkResult, ReviewQueueParams, ReviewResult } from '../types'

const path = '/api/admin/ai-questions'

export function listReviews(
  { kid, hsk, qtype, from, to, page = 0, size = 20 }: ReviewQueueParams = {},
  signal?: AbortSignal,
): Promise<AIQuestionPage> {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries({ kid, hsk, qtype, from, to })) {
    if (value !== undefined && value !== '') query.set(key, String(value))
  }
  query.set('page', String(page))
  query.set('size', String(Math.min(50, Math.max(1, size))))
  return apiRequest(`${path}?${query}`, { signal })
}

export function approveReview(id: number, signal?: AbortSignal): Promise<ReviewResult> {
  return apiRequest(`${path}/${encodeURIComponent(String(id))}/approve`, { method: 'POST', signal })
}

export function rejectReview(
  id: number,
  reason: string,
  signal?: AbortSignal,
): Promise<ReviewResult> {
  return apiRequest(`${path}/${encodeURIComponent(String(id))}/reject`, {
    method: 'POST',
    body: { reason },
    signal,
  })
}

export function bulkApproveReviews(ids: number[], signal?: AbortSignal): Promise<BulkResult> {
  return apiRequest(`${path}/bulk-approve`, { method: 'POST', body: { ids }, signal })
}
