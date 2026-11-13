import { apiRequest } from '@/shared/api'

import type {
  AdminListParams,
  AdminReference,
  AdminReferencePage,
  ReferenceAction,
  ReferenceInput,
} from '../types'

function path(id: number): string {
  if (!Number.isSafeInteger(id) || id <= 0) throw new Error('Mã tài nguyên không hợp lệ.')
  return `/api/admin/references/${id}`
}

export function listAdminReferences(
  { kind, status, reported, page = 0, size = 20 }: AdminListParams = {},
  signal?: AbortSignal,
): Promise<AdminReferencePage> {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  if (kind !== undefined) query.set('kind', kind)
  if (status !== undefined) query.set('status', status)
  if (reported !== undefined) query.set('reported', String(reported))
  return apiRequest<AdminReferencePage>(`/api/admin/references?${query}`, { signal })
}
export function getAdminReference(id: number, signal?: AbortSignal): Promise<AdminReference> {
  return apiRequest<AdminReference>(path(id), { signal })
}
export function createReference(body: ReferenceInput): Promise<AdminReference> {
  return apiRequest<AdminReference>('/api/admin/references', { method: 'POST', body })
}
export function updateReference(id: number, body: ReferenceInput): Promise<AdminReference> {
  return apiRequest<AdminReference>(path(id), { method: 'PUT', body })
}
export function transitionReference(id: number, action: ReferenceAction): Promise<AdminReference> {
  return apiRequest<AdminReference>(`${path(id)}/${action}`, { method: 'POST' })
}
