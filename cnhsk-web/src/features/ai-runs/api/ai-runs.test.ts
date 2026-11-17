import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { listAiRuns, triggerAiRun } from './ai-runs'

const fetchMock = vi.fn()
beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockReset()
  document.cookie = 'XSRF-TOKEN=run-csrf; path=/'
})
afterEach(() => vi.unstubAllGlobals())
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
describe('AI runs adapter through shared client', () => {
  it.each([undefined, 'RUNNING', 'COMPLETED', 'FAILED'] as const)(
    'requests only list with status %s and forwards cancellation',
    async (status) => {
      fetchMock.mockResolvedValue(json({ items: [] }))
      const controller = new AbortController()
      await listAiRuns({ status }, controller.signal)
      const [url, init] = fetchMock.mock.calls[0]!
      const parsed = new URL(url as string, 'http://localhost')
      expect(parsed.pathname).toBe('/api/admin/ai-generation-runs')
      expect(parsed.searchParams.get('page')).toBe('0')
      expect(parsed.searchParams.get('size')).toBe('20')
      expect(parsed.searchParams.get('status')).toBe(status ?? null)
      expect(init).toMatchObject({
        method: 'GET',
        credentials: 'include',
        signal: controller.signal,
      })
    },
  )
  it.each([
    [-1, 20],
    [0, 0],
    [0, 51],
    [0, 1.5],
  ])('rejects invalid page/size before network', (page, size) => {
    expect(() => listAiRuns({ page, size })).toThrow(RangeError)
    expect(fetchMock).not.toHaveBeenCalled()
  })
  it('uses POST, cookies and existing CSRF for accepted work', async () => {
    fetchMock.mockResolvedValue(json({ started: true }, 202))
    expect(await triggerAiRun()).toEqual({ started: true })
    expect(fetchMock).toHaveBeenCalledOnce()
    const [url, init] = fetchMock.mock.calls[0]!
    expect(url).toBe('/api/admin/ai-generation-runs/trigger')
    expect(init).toMatchObject({ method: 'POST', credentials: 'include' })
    expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('run-csrf')
  })
  it.each([409, 500])(
    'preserves safe errors and never automatically retries %s',
    async (status) => {
      fetchMock.mockResolvedValue(
        json(
          {
            error_code: status === 409 ? 'AI_GENERATION_BUSY' : 'INTERNAL_ERROR',
            message: 'Safe',
            request_id: 'req-run',
          },
          status,
        ),
      )
      await expect(triggerAiRun()).rejects.toMatchObject({ status, requestId: 'req-run' })
      expect(fetchMock).toHaveBeenCalledOnce()
    },
  )
  it('rejects malformed acceptance without inventing a run', async () => {
    fetchMock.mockResolvedValue(json({ started: false }, 202))
    await expect(triggerAiRun()).rejects.toMatchObject({ errorCode: 'INVALID_RESPONSE' })
  })
})
