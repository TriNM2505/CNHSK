import { apiRequest, type BlobApiResponse } from '@/shared/api'

import type { LedgerFilter, LedgerPage, LedgerParams, Reconciliation } from '../types'

function ledgerQuery({ from, to, type }: LedgerFilter) {
  const query = new URLSearchParams({ from, to })
  if (type !== undefined) query.set('type', type)
  return query
}

export function listLedger(
  { page = 0, size = 20, ...filter }: LedgerParams,
  signal?: AbortSignal,
): Promise<LedgerPage> {
  const query = ledgerQuery(filter)
  query.set('page', String(page))
  query.set('size', String(size))
  return apiRequest<LedgerPage>(`/api/admin/credit-transactions?${query.toString()}`, { signal })
}

export function exportLedger(
  filter: LedgerFilter,
  signal?: AbortSignal,
): Promise<BlobApiResponse | undefined> {
  return apiRequest(`/api/admin/credit-transactions/export?${ledgerQuery(filter).toString()}`, {
    responseType: 'blob',
    signal,
  })
}

export function reconcileLedger(signal?: AbortSignal): Promise<Reconciliation> {
  return apiRequest<Reconciliation>('/api/admin/credit-transactions/reconciliation', { signal })
}
