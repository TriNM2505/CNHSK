import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

function clearCookie() {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
}
function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
const fetchMock = vi.fn<typeof fetch>()
beforeEach(() => {
  vi.resetModules()
  vi.stubEnv('VITE_API_BASE_URL', 'https://api.test')
  clearCookie()
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(() => {
  clearCookie()
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

describe('review adapters through the real shared HTTP client', () => {
  it('GET queue and existing detail use cookies and WEB header without CSRF write', async () => {
    fetchMock.mockImplementation(async () =>
      json({ items: [], page: 0, size: 20, totalElements: 0 }),
    )
    const { listReviews } = await import('./reviews')
    const { getQuestion } = await import('@/features/question-bank/api/questions')
    const signal = new AbortController().signal
    await listReviews({}, signal)
    await getQuestion(901, signal)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    for (const [, init] of fetchMock.mock.calls) {
      expect(init?.credentials).toBe('include')
      expect(new Headers(init?.headers).get('X-Client-Type')).toBe('WEB')
      expect(new Headers(init?.headers).has('X-XSRF-TOKEN')).toBe(false)
      expect(init?.signal).toBe(signal)
    }
  })
  it.each(['approve', 'reject', 'bulk'] as const)(
    'obtains missing CSRF then sends the exact %s write',
    async (kind) => {
      fetchMock.mockImplementation(async (url) => {
        if (String(url).endsWith('/api/auth/csrf')) {
          document.cookie = 'XSRF-TOKEN=review-token; path=/'
          return json({})
        }
        return json({ id: 901, status: 'APPROVED', reviewedAt: '2026-11-17T00:00:00Z' })
      })
      const api = await import('./reviews')
      if (kind === 'approve') await api.approveReview(901)
      if (kind === 'reject') await api.rejectReview(901, 'Cần sửa.')
      if (kind === 'bulk') await api.bulkApproveReviews([901])
      expect(fetchMock).toHaveBeenCalledTimes(2)
      expect(String(fetchMock.mock.calls[0]![0])).toBe('https://api.test/api/auth/csrf')
      const [url, init] = fetchMock.mock.calls[1]!
      expect(String(url)).toBe(
        `https://api.test/api/admin/ai-questions/${kind === 'bulk' ? 'bulk-approve' : `901/${kind}`}`,
      )
      expect(init?.method).toBe('POST')
      expect(init?.credentials).toBe('include')
      expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe('review-token')
      expect(init?.body).toBe(
        kind === 'approve'
          ? undefined
          : JSON.stringify(kind === 'reject' ? { reason: 'Cần sửa.' } : { ids: [901] }),
      )
    },
  )
  it('retries an invalid CSRF response once using the shared policy', async () => {
    document.cookie = 'XSRF-TOKEN=old; path=/'
    fetchMock
      .mockResolvedValueOnce(
        json({ error_code: 'CSRF_TOKEN_INVALID', message: 'CSRF', request_id: 'req-csrf' }, 403),
      )
      .mockImplementationOnce(async () => {
        document.cookie = 'XSRF-TOKEN=new; path=/'
        return json({})
      })
      .mockResolvedValueOnce(json({ id: 901, status: 'APPROVED' }))
    const { approveReview } = await import('./reviews')
    await approveReview(901)
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(new Headers(fetchMock.mock.calls[2]![1]?.headers).get('X-XSRF-TOKEN')).toBe('new')
  })
  it.each([409, 422, 500])(
    'propagates %s details/request id without replaying the write',
    async (status) => {
      document.cookie = 'XSRF-TOKEN=token; path=/'
      fetchMock.mockResolvedValue(
        json(
          {
            error_code:
              status === 409
                ? 'QUESTION_NOT_PENDING'
                : status === 422
                  ? 'MALFORMED_QUESTION'
                  : 'INTERNAL_ERROR',
            message: 'Thông báo an toàn.',
            request_id: 'req-review',
            details: { stem: 'Cần sửa.' },
          },
          status,
        ),
      )
      const { approveReview } = await import('./reviews')
      await expect(approveReview(901)).rejects.toMatchObject({
        status,
        requestId: 'req-review',
        details: { stem: 'Cần sửa.' },
      })
      expect(fetchMock).toHaveBeenCalledTimes(1)
    },
  )
  it('does not replay an uncertain network write', async () => {
    document.cookie = 'XSRF-TOKEN=token; path=/'
    fetchMock.mockRejectedValue(new TypeError('network'))
    const { bulkApproveReviews } = await import('./reviews')
    await expect(bulkApproveReviews([901])).rejects.toMatchObject({
      status: 0,
      errorCode: 'NETWORK_ERROR',
    })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
