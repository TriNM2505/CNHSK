import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'

import type { CreatedBatch, PlanView, RedeemResult, Wallet } from '../types'
import * as batches from './cardBatches'
import * as ledger from './ledger'
import * as plans from './plans'
import * as wallet from './wallet'

const origin = 'http://api.test'
const batchId = 'B20261103-DEMO01'
const fakeCode = 'DEMO-ONLY-0001-USE0'
const plan = { code: 'FREE', name: 'Miễn phí', expiresAt: null, active: true } as const
const walletResponse: Wallet = {
  balance: 7,
  plan,
  quota: [
    { feature: 'AI_GENERATE', limit: 10, used: 3, remaining: 7 },
    { feature: 'TRANSLATE', limit: 10, used: 0, remaining: 10 },
    { feature: 'ASSISTANT', limit: 10, used: 0, remaining: 10 },
    { feature: 'GRADING', limit: 10, used: 0, remaining: 10 },
  ],
  resetAt: '2026-11-30T17:00:00Z',
}
const redeemed: RedeemResult = { cardType: 'CREDIT', amount: 7, balance: 7, plan }
const created: CreatedBatch = { batchId, codes: [fakeCode] }
const updatedPlan: PlanView = {
  code: 'PREMIUM',
  exists: true,
  name: 'Gói mẫu',
  price: '1234567890.12',
  benefits: { TRANSLATE: 20 },
  activeUsers: 0,
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function clearCsrf() {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
}

beforeEach(() => {
  vi.stubEnv('VITE_API_BASE_URL', origin)
  clearCsrf()
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
  clearCsrf()
})

describe('credits API with the real shared client (FR-001 transport, §13)', () => {
  it('FR001_GETs use the cookie session, contract paths, query defaults and AbortSignal', async () => {
    const signal = new AbortController().signal
    const transactionPage = { items: [], page: 0, size: 20, total: 0 }
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(json(walletResponse))
      .mockResolvedValueOnce(json(transactionPage))
      .mockResolvedValueOnce(json(transactionPage))
      .mockResolvedValueOnce(
        json({
          batchId,
          cardType: 'CREDIT',
          channel: 'DIRECT',
          total: 1,
          unused: 1,
          used: 0,
          voided: 0,
          createdAt: '2026-11-03T00:00:00Z',
        }),
      )
      .mockResolvedValueOnce(json([updatedPlan]))
      .mockResolvedValueOnce(
        json({ ...transactionPage, totals: { count: 0, credited: 0, debited: 0 } }),
      )
      .mockResolvedValueOnce(
        json({ users: 0, rows: 0, totalBalance: 0, totalAmount: 0, issues: [] }),
      )
    vi.stubGlobal('fetch', fetchMock)

    await expect(wallet.getMyWallet(signal)).resolves.toEqual(walletResponse)
    await wallet.listMyTransactions(undefined, signal)
    await batches.listCardBatches(undefined, signal)
    await batches.getCardBatch(batchId, signal)
    await expect(plans.listPlans(signal)).resolves.toEqual([updatedPlan])
    await ledger.listLedger({ from: '2026-11-01', to: '2026-11-03' }, signal)
    await ledger.reconcileLedger(signal)

    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      `${origin}/api/me/credits`,
      `${origin}/api/me/credit-transactions?page=0&size=20`,
      `${origin}/api/admin/card-batches?page=0&size=20`,
      `${origin}/api/admin/card-batches/${batchId}`,
      `${origin}/api/admin/plans`,
      `${origin}/api/admin/credit-transactions?from=2026-11-01&to=2026-11-03&page=0&size=20`,
      `${origin}/api/admin/credit-transactions/reconciliation`,
    ])
    for (const [, init] of fetchMock.mock.calls) {
      expect(init).toMatchObject({ method: 'GET', credentials: 'include', signal })
      const headers = new Headers(init?.headers)
      expect(headers.get('X-Client-Type')).toBe('WEB')
      expect(headers.has('X-XSRF-TOKEN')).toBe(false)
      expect(init?.body).toBeUndefined()
    }
  })

  it('preserves explicit pagination, ledger filters and encodes path parameters', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockImplementation(async () => json({ items: [], total: 0 }))
    vi.stubGlobal('fetch', fetchMock)
    await wallet.listMyTransactions({ page: 1, size: 100 })
    await batches.listCardBatches({ page: 2, size: 10 })
    await batches.getCardBatch('B/DEMO?x=1')
    await ledger.listLedger({
      from: '2026-10-01',
      to: '2026-11-03',
      type: 'REFUND',
      page: 3,
      size: 50,
    })
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      `${origin}/api/me/credit-transactions?page=1&size=100`,
      `${origin}/api/admin/card-batches?page=2&size=10`,
      `${origin}/api/admin/card-batches/B%2FDEMO%3Fx%3D1`,
      `${origin}/api/admin/credit-transactions?from=2026-10-01&to=2026-11-03&type=REFUND&page=3&size=50`,
    ])
  })

  const mutations = [
    {
      path: '/api/credits/redeem',
      method: 'POST',
      body: { code: fakeCode },
      invoke: () => wallet.redeemCard({ code: fakeCode }),
      response: redeemed,
      status: 200,
    },
    {
      path: '/api/admin/card-batches',
      method: 'POST',
      body: { cardType: 'CREDIT', value: 7, quantity: 1, channel: 'DIRECT' },
      invoke: () =>
        batches.createCardBatch({ cardType: 'CREDIT', value: 7, quantity: 1, channel: 'DIRECT' }),
      response: created,
      status: 201,
    },
    {
      path: '/api/admin/card-batches',
      method: 'POST',
      body: {
        cardType: 'SUBSCRIPTION',
        planDays: 30,
        quantity: 1,
        channel: 'PARTNER_BATCH',
        partnerCode: 'PARTNER_DEMO',
      },
      invoke: () =>
        batches.createCardBatch({
          cardType: 'SUBSCRIPTION',
          planDays: 30,
          quantity: 1,
          channel: 'PARTNER_BATCH',
          partnerCode: 'PARTNER_DEMO',
        }),
      response: created,
      status: 201,
    },
    {
      path: `/api/admin/card-batches/${batchId}/void`,
      method: 'PATCH',
      invoke: () => batches.voidCardBatch(batchId),
      response: { batchId, voided: 1 },
      status: 200,
    },
    {
      path: '/api/admin/plans/PREMIUM',
      method: 'PUT',
      body: {
        name: 'Gói mẫu',
        price: '1234567890.12',
        durationDays: 30,
        benefits: { TRANSLATE: 20 },
        isActive: true,
      },
      invoke: () =>
        plans.updatePlan('PREMIUM', {
          name: 'Gói mẫu',
          price: '1234567890.12',
          durationDays: 30,
          benefits: { TRANSLATE: 20 },
          isActive: true,
        }),
      response: updatedPlan,
      status: 200,
    },
    {
      path: '/api/admin/plans/FREE',
      method: 'PUT',
      body: { name: 'Miễn phí', description: null },
      invoke: () => plans.updatePlan('FREE', { name: 'Miễn phí', description: null }),
      response: { code: 'FREE', exists: true, activeUsers: 0 },
      status: 200,
    },
  ]

  it.each(mutations)(
    'FR001_$method $path sends JSON and shared CSRF without storing or logging codes',
    async ({ path, method, body, invoke, response, status }) => {
      document.cookie = 'XSRF-TOKEN=credits%20token; path=/'
      const storageSpy = vi.spyOn(Storage.prototype, 'setItem')
      const consoleSpies = [
        vi.spyOn(console, 'log'),
        vi.spyOn(console, 'warn'),
        vi.spyOn(console, 'error'),
      ]
      const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(json(response, status))
      vi.stubGlobal('fetch', fetchMock)
      await expect(invoke()).resolves.toEqual(response)
      expect(fetchMock).toHaveBeenCalledOnce()
      const [url, init] = fetchMock.mock.calls[0]!
      expect(url).toBe(`${origin}${path}`)
      expect(String(url)).not.toContain(fakeCode)
      expect(init).toMatchObject({ method, credentials: 'include' })
      expect(init?.body).toBe(body === undefined ? undefined : JSON.stringify(body))
      const headers = new Headers(init?.headers)
      expect(headers.get('X-XSRF-TOKEN')).toBe('credits token')
      if (body) expect(headers.get('Content-Type')).toBe('application/json')
      expect(storageSpy).not.toHaveBeenCalled()
      for (const spy of consoleSpies) expect(spy).not.toHaveBeenCalled()
    },
  )

  it('FR001_fetches CSRF before redeeming when no CSRF cookie is present', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockImplementationOnce(async () => {
        document.cookie = 'XSRF-TOKEN=fresh-credits-token; path=/'
        return new Response(null, { status: 204 })
      })
      .mockResolvedValueOnce(json(redeemed))
    vi.stubGlobal('fetch', fetchMock)
    await wallet.redeemCard({ code: fakeCode })
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      `${origin}/api/auth/csrf`,
      `${origin}/api/credits/redeem`,
    ])
    expect(new Headers(fetchMock.mock.calls[1]![1]?.headers).get('X-XSRF-TOKEN')).toBe(
      'fresh-credits-token',
    )
  })

  it('exports the CSV bytes as a blob while retaining filters, cancellation and the server filename', async () => {
    const csv = 'seq,maskedEmail,reason\n1,ma***@example.test,Nạp điểm mẫu\n'
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(csv, {
        headers: {
          'Content-Type': 'text/csv; charset=utf-8',
          'Content-Disposition': 'attachment; filename="ledger.csv"',
        },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const signal = new AbortController().signal
    const result = await ledger.exportLedger(
      { from: '2026-11-01', to: '2026-11-03', type: 'TOPUP' },
      signal,
    )
    expect(result?.fileName).toBe('ledger.csv')
    expect(await result?.blob.text()).toBe(csv)
    expect(fetchMock.mock.calls[0]![0]).toBe(
      `${origin}/api/admin/credit-transactions/export?from=2026-11-01&to=2026-11-03&type=TOPUP`,
    )
    expect(fetchMock.mock.calls[0]![1]).toMatchObject({
      method: 'GET',
      credentials: 'include',
      signal,
    })
  })

  it.each([
    {
      status: 400,
      errorCode: 'INVALID_CARD_CODE',
      invoke: () => wallet.redeemCard({ code: fakeCode }),
    },
    { status: 401, errorCode: 'UNAUTHENTICATED', invoke: () => wallet.getMyWallet() },
    { status: 403, errorCode: 'FORBIDDEN', invoke: () => batches.listCardBatches() },
    { status: 404, errorCode: 'CARD_BATCH_NOT_FOUND', invoke: () => batches.getCardBatch(batchId) },
    {
      status: 409,
      errorCode: 'PLAN_IN_USE',
      invoke: () => plans.updatePlan('PREMIUM', { name: 'Gói mẫu', benefits: { TRANSLATE: 1 } }),
    },
    {
      status: 422,
      errorCode: 'PLAN_NOT_AVAILABLE',
      invoke: () => plans.updatePlan('PREMIUM_PLUS', { name: 'Gói mẫu' }),
    },
    {
      status: 422,
      errorCode: 'VALIDATION_FAILED',
      invoke: () => ledger.listLedger({ from: '', to: '' }),
    },
    {
      status: 422,
      errorCode: 'EXPORT_TOO_LARGE',
      invoke: () => ledger.exportLedger({ from: '2026-11-01', to: '2026-11-03' }),
    },
    { status: 429, errorCode: 'RATE_LIMITED', invoke: () => wallet.redeemCard({ code: fakeCode }) },
  ])(
    'preserves $status $errorCode and error metadata for the future screen',
    async ({ status, errorCode, invoke }) => {
      document.cookie = 'XSRF-TOKEN=credits-token; path=/'
      const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
        json(
          {
            error_code: errorCode,
            message: 'Thông báo an toàn',
            request_id: 'req-credits',
            retryAfterSeconds: 60,
            details: [{ field: 'name', message: 'Tên chưa hợp lệ' }],
          },
          status,
        ),
      )
      vi.stubGlobal('fetch', fetchMock)
      const result = invoke()
      await expect(result).rejects.toBeInstanceOf(ApiError)
      await expect(result).rejects.toMatchObject({
        status,
        errorCode,
        requestId: 'req-credits',
        extra: { retryAfterSeconds: 60 },
        details: [{ field: 'name', message: 'Tên chưa hợp lệ' }],
      })
      expect(fetchMock).toHaveBeenCalledOnce()
    },
  )

  it('propagates request cancellation without converting it into a billing error', async () => {
    const aborted = new DOMException('Aborted', 'AbortError')
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockRejectedValue(aborted))
    const controller = new AbortController()
    controller.abort()
    await expect(wallet.getMyWallet(controller.signal)).rejects.toBe(aborted)
  })
})
