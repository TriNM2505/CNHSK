import type { SessionView } from '@/features/practice/types'
import { apiRequest } from '@/shared/api'

import type { NothingDue, ReviewStartRequest, ReviewSummary } from '../types'

export function getReviewSummary(signal?: AbortSignal): Promise<ReviewSummary> {
  return apiRequest<ReviewSummary>('/api/reviews/summary', { signal })
}

export function startReviewSession(
  request?: ReviewStartRequest,
  signal?: AbortSignal,
): Promise<SessionView | NothingDue> {
  return apiRequest<SessionView | NothingDue>('/api/reviews/sessions', {
    body: request,
    method: 'POST',
    signal,
  })
}
