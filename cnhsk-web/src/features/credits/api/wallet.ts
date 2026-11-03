import { apiRequest } from '@/shared/api'

import type { PageParams, RedeemRequest, RedeemResult, TransactionPage, Wallet } from '../types'

export function getMyWallet(signal?: AbortSignal): Promise<Wallet> {
  return apiRequest<Wallet>('/api/me/credits', { signal })
}

export function listMyTransactions(
  { page = 0, size = 20 }: PageParams = {},
  signal?: AbortSignal,
): Promise<TransactionPage> {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  return apiRequest<TransactionPage>(`/api/me/credit-transactions?${query.toString()}`, { signal })
}

export function redeemCard(body: RedeemRequest): Promise<RedeemResult> {
  return apiRequest<RedeemResult>('/api/credits/redeem', { body, method: 'POST' })
}
