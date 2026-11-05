import type {
  BatchPage,
  LedgerPage,
  PlanView,
  Reconciliation,
  CreatedBatch,
  PaidFeature,
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

export const creditsBatchPage: BatchPage = {
  page: 0,
  size: 20,
  total: 3,
  items: [
    {
      batchId: 'B20261103-DEMO03',
      cardType: 'CREDIT',
      value: 50,
      channel: 'DIRECT',
      total: 5,
      unused: 0,
      used: 2,
      voided: 3,
      campaign: 'thu-nghiem',
      createdAt: '2026-11-30T17:00:00Z',
      expiresAt: null,
    },
    {
      batchId: 'B20261102-DEMO02',
      cardType: 'SUBSCRIPTION',
      planDays: 30,
      channel: 'PARTNER_BATCH',
      partnerCode: 'PARTNER_DEMO',
      campaign: 'hoc-thu',
      total: 10,
      unused: 7,
      used: 3,
      voided: 0,
      createdAt: '2026-11-02T02:00:00Z',
      expiresAt: '2026-12-31T16:59:59Z',
    },
    {
      batchId: 'B20261101-DEMO01',
      cardType: 'CREDIT',
      value: 100,
      channel: 'ECOSYSTEM_GIFT',
      issuedTo: 'Nhóm học thử mẫu',
      total: 3,
      unused: 1,
      used: 2,
      voided: 0,
      createdAt: '2026-11-01T02:00:00Z',
    },
  ],
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
  balance: 221,
  plan: creditsWallet.plan,
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
    description: 'Cấu hình mẫu',
    durationDays: 30,
    price: '199000.00',
    benefits: { AI_GENERATE: 100, TRANSLATE: 50, GRADING: 10 },
    isActive: true,
    activeUsers: 7,
  },
  { code: 'PREMIUM_PLUS', exists: false, name: null, activeUsers: 0 },
]

export const creditsLedgerRows = creditsTransactions.map((row) => ({
  ...row,
  id: 7000 + row.seq,
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
    credited: creditsLedgerRows.reduce((sum, row) => sum + Math.max(0, row.amount), 0),
    debited: creditsLedgerRows.reduce((sum, row) => sum + Math.min(0, row.amount), 0),
  },
}
export const creditsLedgerLastPage: LedgerPage = {
  ...creditsLedgerPage,
  page: 1,
  items: creditsLedgerRows.slice(20),
}
export const creditsEmptyLedgerPage: LedgerPage = {
  items: [],
  page: 0,
  size: 20,
  total: 0,
  totals: { count: 0, credited: 0, debited: 0 },
}
export const creditsReconciliation: Reconciliation = {
  users: 1,
  rows: 25,
  totalBalance: 121,
  totalAmount: 121,
  issues: [],
}
export const creditsReconciliationWithIssues: Reconciliation = {
  ...creditsReconciliation,
  issues: [
    { userId: 7, maskedEmail: 'ma***@example.test', seq: 1, kind: 'FIRST_BALANCE_NOT_ZERO' },
    { userId: 7, maskedEmail: 'ma***@example.test', seq: 5, kind: 'SEQ_GAP' },
    { userId: 7, maskedEmail: 'ma***@example.test', seq: 6, kind: 'BALANCE_CHAIN_BROKEN' },
  ],
}
