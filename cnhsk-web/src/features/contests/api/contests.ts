import { answerItem, getSession } from '@/features/practice/api/sessions'
import type { AnswerRequest } from '@/features/practice/types'
import { apiRequest } from '@/shared/api'

import type {
  ContestDetail,
  ContestDraft,
  ContestPage,
  ContestResults,
  ContestSession,
  ScheduleState,
  SubmitResult,
} from '../types'

function contestPath(id: number): string {
  return '/api/community/contests/' + encodeURIComponent(String(id))
}

export function listContests(
  filters: { state?: ScheduleState; page?: number; size?: number } = {},
  signal?: AbortSignal,
): Promise<ContestPage> {
  const query = new URLSearchParams()
  if (filters.state) query.set('state', filters.state)
  if (filters.page !== undefined) query.set('page', String(filters.page))
  if (filters.size !== undefined) query.set('size', String(filters.size))
  return apiRequest<ContestPage>(
    '/api/public/community/contests' + (query.size ? '?' + query.toString() : ''),
    { signal },
  )
}

export function getContest(id: number, signal?: AbortSignal): Promise<ContestDetail> {
  return apiRequest<ContestDetail>(
    '/api/public/community/contests/' + encodeURIComponent(String(id)),
    { signal },
  )
}

export function enterContest(id: number): Promise<ContestSession> {
  return apiRequest<ContestSession>(contestPath(id) + '/attempts', { method: 'POST' })
}

export function submitContest(id: number): Promise<SubmitResult> {
  return apiRequest<SubmitResult>(contestPath(id) + '/attempts/submit', { method: 'POST' })
}

export function reportContestTabHidden(id: number): Promise<void> {
  return apiRequest<void>(contestPath(id) + '/attempts/events', {
    method: 'POST',
    body: { type: 'TAB_HIDDEN' },
  })
}

export function getContestResults(id: number, signal?: AbortSignal): Promise<ContestResults> {
  return apiRequest<ContestResults>(contestPath(id) + '/results', { signal })
}

export async function getContestDraft(sessionId: number): Promise<ContestDraft> {
  const { status, answered } = await getSession(sessionId)
  return { sessionId, status, answered }
}

export function saveContestAnswer(sessionId: number, answer: AnswerRequest) {
  return answerItem(sessionId, answer)
}
