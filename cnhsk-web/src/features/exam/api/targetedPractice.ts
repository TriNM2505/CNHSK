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

export async function getTargetedNodeName(kid: string, signal?: AbortSignal): Promise<string> {
  const node = await apiRequest<{ nameVi: string }>(
    '/api/knowledge-tree/nodes/' + encodeURIComponent(kid),
    { signal },
  )
  return node.nameVi
}
