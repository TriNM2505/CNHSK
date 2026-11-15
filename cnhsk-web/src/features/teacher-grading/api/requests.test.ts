import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { assigned, completed, mine, queue, result } from '../test-fixtures'
import * as api from './requests'

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
beforeEach(() => {
  vi.stubEnv('VITE_API_BASE_URL', 'http://api.test')
  document.cookie = 'XSRF-TOKEN=teacher-fixture; path=/'
})
afterEach(() => {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
  vi.restoreAllMocks()
})
describe('teacher grading API', () => {
  it('uses three GET operations with zero-based pagination, signal and unchanged route IDs', async () => {
    const fetch = vi
      .fn<typeof globalThis.fetch>()
      .mockResolvedValueOnce(json(queue))
      .mockResolvedValueOnce(json(queue))
      .mockResolvedValueOnce(json(mine))
      .mockResolvedValueOnce(json(assigned))
    vi.stubGlobal('fetch', fetch)
    const signal = new AbortController().signal
    expect(await api.getGradingQueue(0, 20, signal)).toEqual(queue)
    await api.getGradingQueue(2, 10, signal)
    expect(await api.getMyAssignedGrading(signal)).toEqual(mine)
    expect(await api.getAssignedGrading('9007199254740993', signal)).toEqual(assigned)
    expect(fetch.mock.calls.map(([url]) => String(url))).toEqual([
      'http://api.test/api/teacher/grading-queue?page=0&size=20',
      'http://api.test/api/teacher/grading-queue?page=2&size=10',
      'http://api.test/api/teacher/grading-requests/mine',
      'http://api.test/api/teacher/grading-requests/9007199254740993',
    ])
    for (const [, init] of fetch.mock.calls) {
      expect(init?.credentials).toBe('include')
      expect(init?.signal).toBe(signal)
    }
  })
  it('claims, completes and releases through CSRF, with 204 handled without JSON parsing', async () => {
    const fetch = vi
      .fn<typeof globalThis.fetch>()
      .mockResolvedValueOnce(json(assigned))
      .mockResolvedValueOnce(json(completed))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetch)
    expect(await api.claimGrading('42')).toEqual(assigned)
    expect(await api.completeGrading('42', result)).toEqual(completed)
    expect(await api.releaseGrading('42')).toBeUndefined()
    expect(fetch.mock.calls.map(([url]) => String(url))).toEqual([
      'http://api.test/api/teacher/grading-requests/42/claim',
      'http://api.test/api/teacher/grading-requests/42/complete',
      'http://api.test/api/teacher/grading-requests/42/release',
    ])
    for (const [, init] of fetch.mock.calls) {
      expect(init?.method).toBe('POST')
      expect(init?.credentials).toBe('include')
      expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe('teacher-fixture')
    }
    expect(JSON.parse(String(fetch.mock.calls[1]?.[1]?.body))).toEqual(result)
    expect(fetch.mock.calls[0]?.[1]?.body).toBeUndefined()
    expect(fetch.mock.calls[2]?.[1]?.body).toBeUndefined()
  })
  it.each([
    [409, 'GRADING_ALREADY_CLAIMED'],
    [422, 'GRADING_CLAIM_LIMIT'],
    [403, 'GRADING_NOT_ASSIGNEE'],
    [422, 'GRADING_INVALID_RESULT'],
    [422, 'GRADING_DEADLINE_MISSED'],
  ] as const)('preserves %i %s without automatically retrying POST', async (status, errorCode) => {
    const fetch = vi
      .fn<typeof globalThis.fetch>()
      .mockResolvedValue(
        json({ error_code: errorCode, message: 'Thông báo mẫu', request_id: 'fixture' }, status),
      )
    vi.stubGlobal('fetch', fetch)
    await expect(api.completeGrading('42', result)).rejects.toMatchObject({
      status,
      errorCode,
      message: 'Thông báo mẫu',
    })
    expect(fetch).toHaveBeenCalledTimes(1)
  })
})
