import type { SessionView } from '@/features/practice'
import { apiRequest } from '@/shared/api'

import type { MistakeSection } from '../mistakeSections'

import type {
  MistakeSummary,
  NoMistakePractice,
  NoQuestions,
  TargetedPracticeRequest,
} from '../types'

export function getMyMistakes(signal?: AbortSignal): Promise<MistakeSummary> {
  return apiRequest<MistakeSummary>('/api/mistake-practice', { signal })
}

/** Every section, or only the mistakes made in {@code section}. */
export function startMistakePractice(section?: MistakeSection): Promise<SessionView | NoMistakePractice> {
  const query = section ? '?section=' + encodeURIComponent(section) : ''
  return apiRequest<SessionView | NoMistakePractice>('/api/mistake-practice/sessions' + query, {
    method: 'POST',
  })
}

export function startTargetedPractice(
  request: TargetedPracticeRequest,
): Promise<SessionView | NoQuestions> {
  return apiRequest<SessionView | NoQuestions>('/api/targeted-practice/sessions', {
    body: request,
    method: 'POST',
  })
}

export async function getTargetedNodeName(kid: string, signal?: AbortSignal): Promise<string> {
  const node = await apiRequest<{ nameVi: string }>(
    '/api/knowledge-tree/nodes/' + encodeURIComponent(kid),
    { signal },
  )
  return node.nameVi
}
