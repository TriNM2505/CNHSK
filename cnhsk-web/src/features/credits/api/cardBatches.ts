import { apiRequest } from '@/shared/api'

import type {
  BatchPage,
  BatchSummary,
  CreateBatchRequest,
  CreatedBatch,
  PageParams,
  VoidResult,
} from '../types'

function batchPath(batchId: string, suffix = '') {
  return `/api/admin/card-batches/${encodeURIComponent(batchId)}${suffix}`
}

export function createCardBatch(body: CreateBatchRequest): Promise<CreatedBatch> {
  return apiRequest<CreatedBatch>('/api/admin/card-batches', { body, method: 'POST' })
}

export function listCardBatches(
  { page = 0, size = 20 }: PageParams = {},
  signal?: AbortSignal,
): Promise<BatchPage> {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  return apiRequest<BatchPage>(`/api/admin/card-batches?${query.toString()}`, { signal })
}

export function getCardBatch(batchId: string, signal?: AbortSignal): Promise<BatchSummary> {
  return apiRequest<BatchSummary>(batchPath(batchId), { signal })
}

export function voidCardBatch(batchId: string): Promise<VoidResult> {
  return apiRequest<VoidResult>(batchPath(batchId, '/void'), { method: 'PATCH' })
}
