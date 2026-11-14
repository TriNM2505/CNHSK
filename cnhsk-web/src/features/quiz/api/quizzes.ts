import { apiRequest } from '@/shared/api'

import type { QuizListingPage, QuizListParams, QuizSession } from '../types'

export function listQuizzes(
  { hsk, page = 0, size = 20 }: QuizListParams = {},
  signal?: AbortSignal,
): Promise<QuizListingPage> {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  if (hsk !== undefined) query.set('hsk', String(hsk))
  return apiRequest<QuizListingPage>(`/api/quizzes?${query}`, { signal })
}

export function startQuizAttempt(topicId: number): Promise<QuizSession> {
  if (!Number.isSafeInteger(topicId) || topicId <= 0) throw new Error('Mã chủ đề không hợp lệ.')
  return apiRequest<QuizSession>(`/api/quizzes/${topicId}/attempts`, { method: 'POST' })
}
