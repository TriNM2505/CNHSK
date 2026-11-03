export type CardType = 'CREDIT' | 'SUBSCRIPTION'
export type Channel = 'ECOSYSTEM_GIFT' | 'PARTNER_BATCH' | 'DIRECT'
export type PlanCode = 'FREE' | 'PREMIUM' | 'PREMIUM_PLUS'
export type PaidFeature = 'AI_GENERATE' | 'TRANSLATE' | 'ASSISTANT' | 'GRADING'
export type TransactionType =
  'TOPUP' | 'DEDUCT' | 'REFUND' | 'ADJUSTMENT' | 'TEACHER_PAYOUT' | 'SUBSCRIPTION'

export type PageParams = {
  page?: number
  size?: number
}

export type RedeemRequest = {
  code: string
}

export type PlanStatus = {
  code: PlanCode
  name: string
  expiresAt: string | null
  active: boolean
}

export type RedeemResult = {
  cardType: CardType
  amount: number
  balance: number
  plan: PlanStatus
}

export type QuotaLine = {
  feature: PaidFeature
  limit: number
  used: number
  remaining: number
}

export type Wallet = {
  balance: number
  plan: PlanStatus
  quota: QuotaLine[]
  resetAt: string
}

export type Transaction = {
  seq: number
  type: TransactionType
  amount: number
  balanceBefore: number
  balanceAfter: number
  featureCode?: PaidFeature | null
  reason?: string | null
  createdAt: string
}

export type TransactionPage = {
  items: Transaction[]
  page: number
  size: number
  total: number
}

export type CreateBatchRequest = {
  cardType: CardType
  value?: number | null
  planDays?: number | null
  quantity: number
  channel: Channel
  partnerCode?: string | null
  campaign?: string | null
  issuedTo?: string | null
  expiresAt?: string | null
}

export type CreatedBatch = {
  batchId: string
  codes: string[]
}

export type BatchSummary = {
  batchId: string
  cardType: CardType
  value?: number | null
  planDays?: number | null
  channel: Channel
  partnerCode?: string | null
  campaign?: string | null
  issuedTo?: string | null
  expiresAt?: string | null
  total: number
  unused: number
  used: number
  voided: number
  createdAt: string
  createdByName?: string | null
}

export type BatchPage = {
  items: BatchSummary[]
  page: number
  size: number
  total: number
}

export type VoidResult = {
  batchId: string
  voided: number
}

export type Benefits = Partial<Record<PaidFeature, number>>

export type PlanView = {
  code: PlanCode
  exists: boolean
  name?: string | null
  description?: string | null
  durationDays?: number | null
  price?: string | null
  benefits?: Benefits
  isActive?: boolean | null
  activeUsers: number
}

export type PlanUpdateRequest = {
  name: string
  description?: string | null
  durationDays?: number | null
  price?: string | null
  benefits?: Benefits
  isActive?: boolean | null
}

export type LedgerFilter = {
  from: string
  to: string
  type?: TransactionType
}

export type LedgerParams = LedgerFilter & PageParams

export type LedgerRow = {
  id: number
  userId: number
  maskedEmail: string
  seq: number
  type: TransactionType
  amount: number
  balanceBefore: number
  balanceAfter: number
  featureCode?: string | null
  reason?: string | null
  createdAt: string
}

export type LedgerPage = {
  items: LedgerRow[]
  page: number
  size: number
  total: number
  totals: {
    count: number
    credited: number
    debited: number
  }
}

export type ReconciliationIssue = {
  userId: number
  maskedEmail: string
  seq: number
  kind: 'SEQ_GAP' | 'BALANCE_CHAIN_BROKEN' | 'FIRST_BALANCE_NOT_ZERO'
}

export type Reconciliation = {
  users: number
  rows: number
  totalBalance: number
  totalAmount: number
  issues: ReconciliationIssue[]
}
