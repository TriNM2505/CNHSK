import { apiRequest } from '@/shared/api'

import type {
  PublicReference,
  PublicReferencePage,
  ReferenceListParams,
  ReportRequest,
} from '../types'

function identifier(id: number): string {
  if (!Number.isSafeInteger(id) || id <= 0) throw new Error('Mã tài liệu không hợp lệ.')
  return String(id)
}

export function listReferences(
  { kind, hsk, tag, page = 0, size = 20 }: ReferenceListParams,
  signal?: AbortSignal,
): Promise<PublicReferencePage> {
  const query = new URLSearchParams({ kind, page: String(page), size: String(size) })
  if (hsk !== undefined) query.set('hsk', String(hsk))
  if (tag !== undefined) query.set('tag', tag)
  return apiRequest<PublicReferencePage>(`/api/public/references?${query}`, { signal })
}

export function getReference(id: number, signal?: AbortSignal): Promise<PublicReference> {
  return apiRequest<PublicReference>(`/api/public/references/${identifier(id)}`, { signal })
}

export function reportReference(id: number, body: ReportRequest): Promise<void> {
  return apiRequest<void>(`/api/references/${identifier(id)}/report`, { method: 'POST', body })
}
