import { createElement } from 'react'

import type { PreviewEntry } from '@/app/preview/types'

import type {
  BatchPage,
  CreatedBatch,
  LedgerPage,
  PaidFeature,
  PlanView,
  Reconciliation,
  RedeemResult,
  Transaction,
  TransactionPage,
  Wallet,
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

export const previews = [
  {
    group: 'screen',
    id: 'credits-wallet-samples',
    scr: 'SCR-030',
    title: 'Ví điểm — Dữ liệu mẫu',
    render: () =>
      samplePreview('Ví điểm mẫu', [
        { id: 'plan', text: `${creditsWallet.plan.name} · ${creditsWallet.balance} điểm` },
        ...creditsWallet.quota.map((quota) => ({
          id: quota.feature,
          text: `${quota.feature}: ${quota.remaining}/${quota.limit} lượt còn lại`,
        })),
      ]),
  },
  {
    group: 'screen',
    id: 'credits-history-samples',
    scr: 'SCR-031',
    title: 'Lịch sử — Dữ liệu mẫu',
    render: () =>
      samplePreview(
        'Giao dịch mẫu',
        creditsTransactions.map((row) => ({
          id: row.seq,
          text: `#${row.seq} · ${row.type} · ${row.amount} · ${row.balanceBefore} → ${row.balanceAfter}`,
        })),
      ),
  },
  {
    group: 'screen',
    id: 'credits-batch-samples',
    scr: 'SCR-073',
    title: 'Lô mã thẻ — Dữ liệu mẫu',
    render: () =>
      samplePreview(
        'Lô mã mẫu',
        creditsBatchPage.items.map((batch) => ({
          id: batch.batchId,
          text: `${batch.batchId} · ${batch.channel} · ${batch.used}/${batch.total} đã dùng`,
        })),
      ),
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
