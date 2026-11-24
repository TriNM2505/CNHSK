import type { SessionView } from '@/features/practice'
import { apiRequest } from '@/shared/api'

export type ReadingQuestionType = 'MULTIPLE_CHOICE' | 'TRUE_FALSE'

export type ReadingPracticeRequest = {
  hskLevel: number
  qtype?: ReadingQuestionType
}

type NoQuestions = { session: null; reason: 'NO_QUESTIONS' }

/** Starts a reading session; null when the level has no approved reading question yet. */
export async function startReadingPractice(
  request: ReadingPracticeRequest,
): Promise<SessionView | null> {
  const created = await apiRequest<SessionView | NoQuestions>('/api/reading-practice/sessions', {
    body: request,
    method: 'POST',
  })
  return 'reason' in created ? null : created
}
