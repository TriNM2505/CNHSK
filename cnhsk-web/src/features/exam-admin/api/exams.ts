import { apiRequest } from '@/shared/api'
import type { ExamDetail, ExamFilters, ExamPage, ExamWrite } from '../types'
function path(id: number) {
  if (!Number.isSafeInteger(id) || id <= 0) throw new Error('Mã đề không hợp lệ.')
  return `/api/admin/exams/${id}`
}
export function listExams(
  { status, hsk, q, page = 0, size = 20 }: ExamFilters = {},
  signal?: AbortSignal,
) {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  if (status) query.set('status', status)
  if (hsk !== undefined) query.set('hsk', String(hsk))
  if (q?.trim()) query.set('q', q.trim())
  return apiRequest<ExamPage>(`/api/admin/exams?${query}`, { signal })
}
export function getExam(id: number, signal?: AbortSignal) {
  return apiRequest<ExamDetail>(path(id), { signal })
}
export function saveExam(id: number | null, body: ExamWrite, etag?: string) {
  return apiRequest<ExamDetail>(id === null ? '/api/admin/exams' : path(id), {
    method: id === null ? 'POST' : 'PUT',
    body: id === null ? body : { ...body, etag },
  })
}
export function publishExam(id: number, etag: string) {
  return apiRequest<ExamDetail>(`${path(id)}/publish`, { method: 'POST', body: { etag } })
}
export function archiveExam(id: number) {
  return apiRequest<ExamDetail>(`${path(id)}/archive`, { method: 'POST' })
}
export function cloneExam(id: number, code: string, title?: string) {
  return apiRequest<ExamDetail>(`${path(id)}/clone`, {
    method: 'POST',
    body: { code, ...(title?.trim() ? { title: title.trim() } : {}) },
  })
}
