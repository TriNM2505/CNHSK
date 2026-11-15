import { apiRequest } from '@/shared/api'
import type {
  AdminContest,
  AdminContestPage,
  AdminExamSummary,
  ContestAction,
  ContestListParams,
  ContestWrite,
  ExamListParams,
  Page,
  Participant,
  ParticipantDecision,
  Winner,
} from '../types'

function path(id: number): string {
  if (!Number.isSafeInteger(id) || id < 1) throw new Error('Mã cuộc thi không hợp lệ.')
  return `/api/admin/contests/${id}`
}
export function listContests(
  { status, page = 0, size = 20 }: ContestListParams = {},
  signal?: AbortSignal,
) {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  if (status) query.set('status', status)
  return apiRequest<AdminContestPage>(`/api/admin/contests?${query}`, { signal })
}
export function getContest(id: number, signal?: AbortSignal) {
  return apiRequest<AdminContest>(path(id), { signal })
}
export function saveContest(id: number | null, body: ContestWrite) {
  return apiRequest<AdminContest>(id === null ? '/api/admin/contests' : path(id), {
    method: id === null ? 'POST' : 'PUT',
    body,
  })
}
export function transitionContest(id: number, action: ContestAction) {
  const suffix = action === 'ranking' ? 'ranking/publish' : action
  return apiRequest<AdminContest>(`${path(id)}/${suffix}`, { method: 'POST' })
}
export function listParticipants(id: number, signal?: AbortSignal) {
  return apiRequest<Participant[]>(`${path(id)}/participants`, { signal })
}
export function decideParticipant(id: number, userId: number, body: ParticipantDecision) {
  if (!Number.isSafeInteger(userId) || userId < 1) throw new Error('Mã thí sinh không hợp lệ.')
  return apiRequest<Participant>(`${path(id)}/participants/${userId}`, { method: 'PATCH', body })
}
export function revealWinners(id: number, signal?: AbortSignal) {
  return apiRequest<Winner[]>(`${path(id)}/winners`, { signal, cache: 'no-store' })
}
export function listExams({ q, page = 0, size = 20 }: ExamListParams = {}, signal?: AbortSignal) {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  if (q) query.set('q', q)
  return apiRequest<Page<AdminExamSummary>>(`/api/admin/exams?${query}`, { signal })
}
// The approved selector uses only the paged list endpoint. A selected exam may be
// outside its current page, or archived; do not mistake either case for deletion.
export async function findAttachedExam(
  id: number,
  signal?: AbortSignal,
): Promise<AdminExamSummary | null> {
  let page = 0
  while (!signal?.aborted) {
    const result = await listExams({ page, size: 50 }, signal)
    const exam = result.items.find((item) => item.id === id)
    if (exam) return exam
    if (result.items.length === 0 || (result.page + 1) * result.size >= result.totalElements)
      return null
    page = result.page + 1
  }
  throw new DOMException('Request aborted', 'AbortError')
}
