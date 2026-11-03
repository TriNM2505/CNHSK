import type { SessionView } from '@/features/practice'
import { apiRequest } from '@/shared/api'

import type { NoQuestions, TargetedPracticeRequest } from '../types'

export function startTargetedPractice(
  request: TargetedPracticeRequest,
): Promise<SessionView | NoQuestions> {
  return apiRequest<SessionView | NoQuestions>('/api/targeted-practice/sessions', {
    body: request,
    method: 'POST',
  })
}
