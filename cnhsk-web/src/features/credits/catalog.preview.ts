import { createElement, useRef, useState } from 'react'

import { ApiError, toApiError } from '@/shared/api'

import type { PreviewEntry } from '@/app/preview/types'

import { PlanCreditsView } from './components/PlanCreditsView'
import { TransactionHistoryView } from './components/TransactionHistoryView'
import { CardBatchView } from './components/CardBatchView'

import type {
  BatchPage,
  CreatedBatch,
  CreateBatchRequest,
  LedgerPage,
  PaidFeature,
  PlanView,
  Reconciliation,
  RedeemRequest,
  RedeemResult,
  Transaction,
  TransactionPage,
  Wallet,
  VoidResult,
} from './types'

// Original, offline fixtures. U is outside the card-code alphabet, so these codes cannot be redeemed.
export const creditsCreatedBatch: CreatedBatch = {
  batchId: 'B20261103-DEMO04',
  codes: ['DEMO-ONLY-0001-USE0', 'DEMO-ONLY-0002-USE0', 'DEMO-ONLY-0003-USE0'],
}

const events: readonly [Transaction['type'], number, PaidFeature | null, string][] = [
  ['TOPUP', 100, null, 'Nạp điểm mẫu'],
  ['SUBSCRIPTION', 0, null, 'PREMIUM +30 ngày — dữ liệu mẫu'],
  ['DEDUCT', -1, 'AI_GENERATE', 'Lượt tạo câu mẫu'],
  ['REFUND', 1, 'AI_GENERATE', 'Hoàn lượt tạo câu mẫu'],
  ['DEDUCT', -1, 'TRANSLATE', 'Lượt dịch mẫu'],
  ['TEACHER_PAYOUT', 5, null, 'Điểm chấm bài mẫu'],
  ['DEDUCT', -1, 'GRADING', 'Lượt chấm bài mẫu'],
  ['DEDUCT', -1, 'AI_GENERATE', 'Lượt tạo câu mẫu'],
  ['TOPUP', 20, null, 'Nạp điểm mẫu'],
  ['ADJUSTMENT', -2, null, 'Dòng điều chỉnh lịch sử mẫu'],
  ['DEDUCT', -1, 'TRANSLATE', 'Lượt dịch mẫu'],
  ['DEDUCT', -1, 'AI_GENERATE', 'Lượt tạo câu mẫu'],
  ['REFUND', 1, 'AI_GENERATE', 'Hoàn lượt tạo câu mẫu'],
  ['DEDUCT', -1, 'GRADING', 'Lượt chấm bài mẫu'],
  ['DEDUCT', -1, 'TRANSLATE', 'Lượt dịch mẫu'],
  ['SUBSCRIPTION', 0, null, 'PREMIUM +30 ngày — dữ liệu mẫu'],
  ['DEDUCT', -1, 'AI_GENERATE', 'Lượt tạo câu mẫu'],
  ['DEDUCT', -1, 'TRANSLATE', 'Lượt dịch mẫu'],
  ['TOPUP', 10, null, 'Nạp điểm mẫu'],
  ['DEDUCT', -1, 'GRADING', 'Lượt chấm bài mẫu'],
  ['REFUND', 1, 'GRADING', 'Hoàn lượt chấm bài mẫu'],
  ['DEDUCT', -1, 'TRANSLATE', 'Lượt dịch mẫu'],
  ['DEDUCT', -1, 'AI_GENERATE', 'Lượt tạo câu mẫu'],
  ['DEDUCT', -1, 'TRANSLATE', 'Lượt dịch mẫu'],
  ['DEDUCT', -1, 'GRADING', 'Lượt chấm bài mẫu'],
]

function createSampleTransactions(): Transaction[] {
  let balance = 0
  return events
    .map(([type, amount, featureCode, reason], index) => {
      const balanceBefore = balance
      balance += amount
      return {
        seq: index + 1,
        type,
        amount,
        balanceBefore,
        balanceAfter: balance,
        featureCode,
        reason,
        createdAt: `2026-11-01T02:${String(index).padStart(2, '0')}:00Z`,
      }
    })
    .reverse()
}

export const creditsTransactions = createSampleTransactions()
export const creditsTransactionPage: TransactionPage = {
  items: creditsTransactions.slice(0, 20),
  page: 0,
  size: 20,
  total: creditsTransactions.length,
}
export const creditsTransactionLastPage: TransactionPage = {
  items: creditsTransactions.slice(20),
  page: 1,
  size: 20,
  total: creditsTransactions.length,
}
export const creditsEmptyTransactionPage: TransactionPage = {
  items: [],
  page: 0,
  size: 20,
  total: 0,
}

export const creditsWallet: Wallet = {
  balance: 121,
  plan: { code: 'PREMIUM', name: 'PREMIUM mẫu', expiresAt: '2026-12-31T02:01:00Z', active: true },
  quota: [
    { feature: 'AI_GENERATE', limit: 100, used: 100, remaining: 0 },
    { feature: 'TRANSLATE', limit: 50, used: 50, remaining: 0 },
    { feature: 'ASSISTANT', limit: 0, used: 0, remaining: 0 },
    { feature: 'GRADING', limit: 10, used: 10, remaining: 0 },
  ],
  resetAt: '2026-11-30T17:00:00Z',
}

export const creditsFreeWallet: Wallet = {
  balance: 0,
  plan: { code: 'FREE', name: 'Miễn phí', expiresAt: null, active: true },
  quota: [
    { feature: 'AI_GENERATE', limit: 10, used: 3, remaining: 7 },
    { feature: 'TRANSLATE', limit: 10, used: 0, remaining: 10 },
    { feature: 'ASSISTANT', limit: 10, used: 0, remaining: 10 },
    { feature: 'GRADING', limit: 10, used: 0, remaining: 10 },
  ],
  resetAt: '2026-11-30T17:00:00Z',
}

export const creditsRedeemResult: RedeemResult = {
  cardType: 'CREDIT',
  amount: 100,
  balance: 121,
  plan: creditsWallet.plan,
}

export const creditsBatchPage: BatchPage = {
  page: 0,
  size: 20,
  total: 3,
  items: [
    {
      batchId: 'B20261103-DEMO03',
      cardType: 'CREDIT',
      value: 50,
      planDays: null,
      channel: 'DIRECT',
      partnerCode: null,
      campaign: 'thu-nghiem',
      issuedTo: null,
      expiresAt: null,
      total: 5,
      unused: 0,
      used: 2,
      voided: 3,
      createdAt: '2026-11-03T02:00:00Z',
      createdByName: 'Quản trị mẫu',
    },
    {
      batchId: 'B20261102-DEMO02',
      cardType: 'SUBSCRIPTION',
      value: null,
      planDays: 30,
      channel: 'PARTNER_BATCH',
      partnerCode: 'PARTNER_DEMO',
      campaign: 'hoc-thu',
      issuedTo: null,
      expiresAt: '2026-12-31T16:59:59Z',
      total: 10,
      unused: 7,
      used: 3,
      voided: 0,
      createdAt: '2026-11-02T02:00:00Z',
      createdByName: 'Quản trị mẫu',
    },
    {
      batchId: 'B20261101-DEMO01',
      cardType: 'CREDIT',
      value: 100,
      planDays: null,
      channel: 'ECOSYSTEM_GIFT',
      partnerCode: null,
      campaign: 'tang-trai-nghiem',
      issuedTo: 'Nhóm học thử mẫu',
      expiresAt: null,
      total: 3,
      unused: 1,
      used: 2,
      voided: 0,
      createdAt: '2026-11-01T02:00:00Z',
      createdByName: 'Quản trị mẫu',
    },
  ],
}

export const creditsPlans: PlanView[] = [
  {
    code: 'FREE',
    exists: true,
    name: 'Miễn phí',
    description: 'Gói miễn phí mẫu',
    durationDays: null,
    price: '0.00',
    benefits: {},
    isActive: true,
    activeUsers: 42,
  },
  {
    code: 'PREMIUM',
    exists: true,
    name: 'PREMIUM mẫu',
    description: 'Giá và hạn mức minh họa do quản trị nhập',
    durationDays: 30,
    price: '199000.00',
    benefits: { AI_GENERATE: 100, TRANSLATE: 50, GRADING: 10 },
    isActive: true,
    activeUsers: 7,
  },
  {
    code: 'PREMIUM_PLUS',
    exists: false,
    name: null,
    description: null,
    durationDays: null,
    price: null,
    isActive: null,
    activeUsers: 0,
  },
]

export const creditsLedgerRows = creditsTransactions.map((transaction) => ({
  ...transaction,
  id: 7000 + transaction.seq,
  userId: 7,
  maskedEmail: 'ma***@example.test',
}))

export const creditsLedgerPage: LedgerPage = {
  items: creditsLedgerRows.slice(0, 20),
  page: 0,
  size: 20,
  total: creditsLedgerRows.length,
  totals: {
    count: creditsLedgerRows.length,
    credited: creditsLedgerRows.reduce((total, row) => total + Math.max(0, row.amount), 0),
    debited: creditsLedgerRows.reduce((total, row) => total + Math.min(0, row.amount), 0),
  },
}

export const creditsReconciliation: Reconciliation = {
  users: 1,
  rows: 25,
  totalBalance: 121,
  totalAmount: 121,
  issues: [],
}

export const creditsReconciliationWithIssue: Reconciliation = {
  ...creditsReconciliation,
  issues: [{ userId: 7, maskedEmail: 'ma***@example.test', seq: 4, kind: 'BALANCE_CHAIN_BROKEN' }],
}

function samplePreview(title: string, rows: readonly { id: string | number; text: string }[]) {
  return createElement(
    'section',
    { className: 'mx-auto max-w-reading space-y-4' },
    createElement('h1', { className: 'text-h1-sm text-ink' }, title),
    createElement(
      'ul',
      { className: 'space-y-3' },
      rows.map(({ id, text }) =>
        createElement(
          'li',
          { className: 'rounded-md border border-line bg-surface p-4 text-body text-ink', key: id },
          text,
        ),
      ),
    ),
  )
}

function PlanCreditsPreview() {
  const [wallet, setWallet] = useState(() => structuredClone(creditsWallet))
  const [pending, setPending] = useState(false)
  const [redeemError, setRedeemError] = useState<ApiError>()
  const [result, setResult] = useState<RedeemResult>()
  const busy = useRef(false)
  const redeem = async ({ code }: RedeemRequest) => {
    if (busy.current) return
    busy.current = true
    setPending(true)
    setRedeemError(undefined)
    try {
      const normalize = (value: string) => value.replace(/[\s-]/g, '').toUpperCase()
      if (normalize(code) !== normalize(creditsCreatedBatch.codes[0]!)) {
        throw new ApiError({
          status: 400,
          errorCode: 'INVALID_CARD_CODE',
          message: 'Mã thẻ không hợp lệ hoặc không thể sử dụng.',
        })
      }
      const redeemed: RedeemResult = {
        ...creditsRedeemResult,
        balance: wallet.balance + creditsRedeemResult.amount,
        plan: { ...wallet.plan },
      }
      setWallet((current) => ({
        ...current,
        balance: redeemed.balance,
        plan: { ...redeemed.plan },
      }))
      setResult(redeemed)
    } catch (error) {
      setRedeemError(toApiError(error))
    } finally {
      busy.current = false
      setPending(false)
    }
  }
  return createElement(PlanCreditsView, {
    state: 'ready',
    wallet,
    pending,
    redeemError,
    result,
    onRetry: () => undefined,
    onRedeem: redeem,
  })
}

function TransactionHistoryPreview() {
  const [page, setPage] = useState(0)
  return createElement(TransactionHistoryView, {
    state: 'ready',
    data: page === 0 ? creditsTransactionPage : creditsTransactionLastPage,
    page,
    onPageChange: setPage,
    onRetry: () => undefined,
  })
}

function CardBatchPreview() {
  const [items, setItems] = useState(() => structuredClone(creditsBatchPage.items))
  const [page, setPage] = useState(0)
  const [created, setCreated] = useState<CreatedBatch>()
  const [voided, setVoided] = useState<VoidResult>()
  const sequence = useRef(4)

  const create = async (body: CreateBatchRequest) => {
    const batchId = `B20261106-DEMO${String(sequence.current++).padStart(2, '0')}`
    const result = {
      batchId,
      codes: Array.from(
        { length: body.quantity },
        (_, index) => `DEMO-ONLY-${String(index + 1).padStart(4, '0')}-USE0`,
      ),
    }
    setCreated(result)
    setItems((current) => [
      {
        ...body,
        batchId,
        total: body.quantity,
        unused: body.quantity,
        used: 0,
        voided: 0,
        createdAt: new Date().toISOString(),
        createdByName: 'Quản trị mẫu',
      },
      ...current,
    ])
    setPage(0)
  }

  const voidBatch = async (batchId: string) => {
    const batch = items.find((item) => item.batchId === batchId)
    if (!batch) return
    setVoided({ batchId, voided: batch.unused })
    setItems((current) =>
      current.map((item) =>
        item.batchId === batchId ? { ...item, unused: 0, voided: item.voided + item.unused } : item,
      ),
    )
  }

  return createElement(CardBatchView, {
    state: items.length ? 'ready' : 'empty',
    data: { items: items.slice(page * 20, (page + 1) * 20), page, size: 20, total: items.length },
    page,
    created,
    voided,
    onCreate: create,
    onVoid: voidBatch,
    onPageChange: setPage,
    onRetry: () => undefined,
  })
}

export const previews = [
  {
    group: 'screen',
    id: 'credits-wallet-samples',
    scr: 'SCR-030',
    title: 'Ví điểm — Dữ liệu mẫu',
    render: () => createElement(PlanCreditsPreview),
  },
  {
    group: 'screen',
    id: 'credits-history-samples',
    scr: 'SCR-031',
    title: 'Lịch sử — Dữ liệu mẫu',
    render: () => createElement(TransactionHistoryPreview),
  },
  {
    group: 'screen',
    id: 'credits-batch-samples',
    scr: 'SCR-073',
    title: 'Lô mã thẻ — Dữ liệu mẫu',
    render: () => createElement(CardBatchPreview),
  },
  {
    group: 'screen',
    id: 'credits-plan-samples',
    scr: 'SCR-074',
    title: 'Gói dịch vụ — Dữ liệu mẫu',
    render: () =>
      samplePreview(
        'Gói dịch vụ mẫu',
        creditsPlans.map((plan) => ({
          id: plan.code,
          text: `${plan.code} · ${plan.name ?? 'Chưa hỗ trợ đợt này'} · ${plan.activeUsers} người đang dùng`,
        })),
      ),
  },
  {
    group: 'screen',
    id: 'credits-ledger-samples',
    scr: 'SCR-075',
    title: 'Sổ cái — Dữ liệu mẫu',
    render: () =>
      samplePreview(
        'Sổ cái mẫu',
        creditsLedgerRows.map((row) => ({
          id: row.id,
          text: `${row.maskedEmail} · #${row.seq} · ${row.amount} · ${row.balanceBefore} → ${row.balanceAfter}`,
        })),
      ),
  },
] satisfies readonly PreviewEntry[]
