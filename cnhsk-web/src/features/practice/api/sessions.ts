import type {
  AnswerRequest,
  AnswerResult,
  FinishRequest,
  FinishResult,
  PracticeSource,
  SessionView,
} from '@/features/practice/types'
import { apiRequest } from '@/shared/api'

function sessionPath(sessionId: number, suffix = ''): string {
  return `/api/practice/sessions/${encodeURIComponent(String(sessionId))}${suffix}`
}

export function getSession(sessionId: number, signal?: AbortSignal): Promise<SessionView> {
  return apiRequest<SessionView>(sessionPath(sessionId), { signal })
}

export async function findActiveSession(
  source: PracticeSource,
  refId?: number | null,
  signal?: AbortSignal,
): Promise<SessionView | null> {
  const query = new URLSearchParams({ source })
  if (refId !== null && refId !== undefined) query.set('refId', String(refId))

  const session = await apiRequest<SessionView | undefined>(
    `/api/practice/sessions/active?${query.toString()}`,
    { signal },
  )
  return session ?? null
}

export function answerItem(sessionId: number, request: AnswerRequest): Promise<AnswerResult> {
  return apiRequest<AnswerResult>(sessionPath(sessionId, '/answers'), {
    body: request,
    method: 'POST',
  })
}

export function finishSession(sessionId: number, request?: FinishRequest): Promise<FinishResult> {
  return apiRequest<FinishResult>(sessionPath(sessionId, '/finish'), {
    body: request,
    method: 'POST',
  })
}

export function abandonSession(sessionId: number): Promise<void> {
  return apiRequest<void>(sessionPath(sessionId, '/abandon'), { method: 'POST' })
}
