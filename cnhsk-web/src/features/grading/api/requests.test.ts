import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api'
import { pendingRequest, policy, requestPage } from '../test-fixtures'
import * as api from './requests'

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
beforeEach(() => {
  vi.stubEnv('VITE_API_BASE_URL', 'http://api.test')
  document.cookie = 'XSRF-TOKEN=grading-csrf-fixture; path=/'
})
afterEach(() => {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
  vi.restoreAllMocks()
})

describe('grading API through the real shared cookie/CSRF client', () => {
  it('uses the three exact GET paths, zero-based pagination, status and AbortSignal', async () => {
    const fetch = vi
      .fn<typeof globalThis.fetch>()
      .mockResolvedValueOnce(json(policy))
      .mockResolvedValueOnce(json(requestPage))
      .mockResolvedValueOnce(json(requestPage))
      .mockResolvedValueOnce(json(pendingRequest))
    vi.stubGlobal('fetch', fetch)
    const signal = new AbortController().signal
    expect(await api.getGradingPolicy(signal)).toEqual(policy)
    expect(await api.listMyGradingRequests({}, signal)).toEqual(requestPage)
    await api.listMyGradingRequests({ status: 'COMPLETED', page: 2, size: 10 }, signal)
    await api.getMyGradingRequest('9007199254740993', signal)
    expect(fetch.mock.calls.map(([url]) => String(url))).toEqual([
      'http://api.test/api/grading/policy',
      'http://api.test/api/grading-requests?page=0&size=20',
      'http://api.test/api/grading-requests?page=2&size=10&status=COMPLETED',
      'http://api.test/api/grading-requests/9007199254740993',
    ])
    for (const [, init] of fetch.mock.calls) {
      expect(init?.credentials).toBe('include')
      expect(init?.signal).toBe(signal)
    }
  })
  it('creates and cancels via POST with CSRF and the contract request body', async () => {
    const fetch = vi
      .fn<typeof globalThis.fetch>()
      .mockResolvedValueOnce(json(pendingRequest, 201))
      .mockResolvedValueOnce(json({ ...pendingRequest, status: 'FAILED', refunded: true }))
    vi.stubGlobal('fetch', fetch)
    const body = { essay: '字'.repeat(50) + '\n词', prompt: null, hskLevel: 3 }
    expect(await api.createGradingRequest(body)).toEqual(pendingRequest)
    expect(await api.cancelGradingRequest('42')).toMatchObject({ status: 'FAILED', refunded: true })
    expect(fetch.mock.calls[0]?.[0]).toBe('http://api.test/api/grading-requests')
    expect(fetch.mock.calls[1]?.[0]).toBe('http://api.test/api/grading-requests/42/cancel')
    for (const [, init] of fetch.mock.calls) {
      expect(init?.method).toBe('POST')
      expect(init?.credentials).toBe('include')
      expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe('grading-csrf-fixture')
    }
    expect(JSON.parse(String(fetch.mock.calls[0]?.[1]?.body))).toEqual(body)
    expect(fetch.mock.calls[1]?.[1]?.body).toBeUndefined()
  })
  it.each([
    [402, 'INSUFFICIENT_CREDITS', undefined],
    [409, 'GRADING_DUPLICATE_ACTIVE', undefined],
    [422, 'GRADING_INVALID_ESSAY', { hanziCount: 49 }],
    [422, 'VALIDATION_FAILED', [{ field: 'prompt', message: 'Quá dài' }]],
  ])(
    'preserves %i %s and both details shapes without automatically resubmitting',
    async (status, code, details) => {
      const fetch = vi
        .fn<typeof globalThis.fetch>()
        .mockResolvedValue(
          json(
            { error_code: code, message: 'Thông báo mẫu', request_id: 'fixture-request', details },
            status as number,
          ),
        )
      vi.stubGlobal('fetch', fetch)
      await expect(api.createGradingRequest({ essay: '字'.repeat(50) })).rejects.toMatchObject({
        status,
        errorCode: code,
        details,
      })
      expect(fetch).toHaveBeenCalledTimes(1)
    },
  )
  it('preserves ownership 403 and cancellation conflict 409', async () => {
    const fetch = vi
      .fn<typeof globalThis.fetch>()
      .mockResolvedValueOnce(
        json(
          {
            error_code: 'GRADING_REQUEST_NOT_OWNED',
            message: 'Không có quyền truy cập.',
            request_id: 'fixture',
          },
          403,
        ),
      )
      .mockResolvedValueOnce(
        json(
          {
            error_code: 'GRADING_NOT_CANCELLABLE',
            message: 'Bài đã có người nhận.',
            request_id: 'fixture',
          },
          409,
        ),
      )
    vi.stubGlobal('fetch', fetch)
    await expect(api.getMyGradingRequest('invalid')).rejects.toBeInstanceOf(ApiError)
    await expect(api.cancelGradingRequest('42')).rejects.toMatchObject({
      status: 409,
      errorCode: 'GRADING_NOT_CANCELLABLE',
    })
    expect(fetch).toHaveBeenCalledTimes(2)
  })
})
