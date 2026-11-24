import { apiRequest } from '@/shared/api'
import type {
  AssignedItem,
  ErrorCodeOption,
  GradingResultWrite,
  QueuePage,
  TeacherGradingDetail,
} from '../types'

const base = '/api/teacher/grading-requests'
export function getErrorCodes(signal?: AbortSignal): Promise<ErrorCodeOption[]> {
  return apiRequest<ErrorCodeOption[]>('/api/teacher/error-codes', { signal })
}
export function getGradingQueue(page = 0, size = 20, signal?: AbortSignal): Promise<QueuePage> {
  return apiRequest<QueuePage>(
    `/api/teacher/grading-queue?${new URLSearchParams({ page: String(page), size: String(size) })}`,
    { signal },
  )
}
export function getMyAssignedGrading(signal?: AbortSignal): Promise<AssignedItem[]> {
  return apiRequest<AssignedItem[]>(`${base}/mine`, { signal })
}
export function getAssignedGrading(
  id: string,
  signal?: AbortSignal,
): Promise<TeacherGradingDetail> {
  return apiRequest<TeacherGradingDetail>(`${base}/${encodeURIComponent(id)}`, { signal })
}
export function claimGrading(id: string): Promise<TeacherGradingDetail> {
  return apiRequest<TeacherGradingDetail>(`${base}/${encodeURIComponent(id)}/claim`, {
    method: 'POST',
  })
}
export function releaseGrading(id: string): Promise<void> {
  return apiRequest<void>(`${base}/${encodeURIComponent(id)}/release`, { method: 'POST' })
}
export function completeGrading(
  id: string,
  body: GradingResultWrite,
): Promise<TeacherGradingDetail> {
  return apiRequest<TeacherGradingDetail>(`${base}/${encodeURIComponent(id)}/complete`, {
    method: 'POST',
    body,
  })
}
