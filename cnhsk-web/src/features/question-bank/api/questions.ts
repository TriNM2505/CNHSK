import { apiRequest } from '@/shared/api'

import type { QuestionDetail, QuestionListParams, QuestionPage, QuestionUpdate } from '../types'

const questionsPath = '/api/admin/questions'

export function listQuestions(
  { hskLevel, qtype, status, kid, q, page = 0, size = 10 }: QuestionListParams = {},
  signal?: AbortSignal,
): Promise<QuestionPage> {
  const query = new URLSearchParams()
  if (hskLevel !== undefined) query.set('hskLevel', String(hskLevel))
  if (qtype !== undefined) query.set('qtype', qtype)
  if (status !== undefined) query.set('status', status)
  if (kid !== undefined) query.set('kid', kid)
  if (q !== undefined) query.set('q', q)
  query.set('page', String(page))
  query.set('size', String(size))
  return apiRequest<QuestionPage>(`${questionsPath}?${query.toString()}`, { signal })
}

export function getQuestion(id: number, signal?: AbortSignal): Promise<QuestionDetail> {
  return apiRequest<QuestionDetail>(`${questionsPath}/${encodeURIComponent(String(id))}`, {
    signal,
  })
}

export function updateQuestion(
  id: number,
  body: QuestionUpdate,
  signal?: AbortSignal,
): Promise<QuestionDetail> {
  return apiRequest<QuestionDetail>(`${questionsPath}/${encodeURIComponent(String(id))}`, {
    method: 'PUT',
    body,
    signal,
  })
}

export function archiveQuestion(id: number, signal?: AbortSignal): Promise<QuestionDetail> {
  return apiRequest<QuestionDetail>(`${questionsPath}/${encodeURIComponent(String(id))}/archive`, {
    method: 'PATCH',
    signal,
  })
}
