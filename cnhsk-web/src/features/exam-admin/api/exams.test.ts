import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api'
import { examWrite, formFromExam } from '../examForm'
import { examFixture } from '../test-fixtures'
import { archiveExam, cloneExam, getExam, listExams, publishExam, saveExam } from './exams'
const fetchMock = vi.fn<typeof fetch>()
beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockReset().mockImplementation(
    async () =>
      new Response(JSON.stringify(examFixture), {
        headers: { 'Content-Type': 'application/json' },
      }),
  )
  document.cookie = 'XSRF-TOKEN=exam-test; Path=/'
})
afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/'
})
describe('exam administration HTTP contract', () => {
  it('sends exact paged filters, cookies and abort signal', async () => {
    const signal = new AbortController().signal
    await listExams({ status: 'PUBLISHED', hsk: 2, q: ' 水 & ', page: 3, size: 20 }, signal)
    const [url, init] = fetchMock.mock.calls[0]!
    const parsed = new URL(String(url), 'https://example.test')
    expect(parsed.pathname).toBe('/api/admin/exams')
    expect(Object.fromEntries(parsed.searchParams)).toEqual({
      status: 'PUBLISHED',
      hsk: '2',
      q: '水 &',
      page: '3',
      size: '20',
    })
    expect(init).toMatchObject({ signal, credentials: 'include', method: 'GET' })
    await getExam(2301, signal)
    expect(fetchMock.mock.lastCall?.[0]).toBe('/api/admin/exams/2301')
  })
  it('uses required verbs and etag bodies with shared CSRF, omitting clone title when blank', async () => {
    const write = examWrite(formFromExam(examFixture))
    await saveExam(null, write)
    await saveExam(2301, write, 'opaque-etag')
    await publishExam(2301, 'latest-etag')
    await archiveExam(2301)
    await cloneExam(2301, 'COPY', ' ')
    expect(fetchMock.mock.calls.map(([url, init]) => [url, init?.method])).toEqual([
      ['/api/admin/exams', 'POST'],
      ['/api/admin/exams/2301', 'PUT'],
      ['/api/admin/exams/2301/publish', 'POST'],
      ['/api/admin/exams/2301/archive', 'POST'],
      ['/api/admin/exams/2301/clone', 'POST'],
    ])
    expect(JSON.parse(String(fetchMock.mock.calls[1]![1]?.body))).toEqual({
      ...write,
      etag: 'opaque-etag',
    })
    expect(JSON.parse(String(fetchMock.mock.calls[2]![1]?.body))).toEqual({ etag: 'latest-etag' })
    expect(JSON.parse(String(fetchMock.mock.lastCall?.[1]?.body))).toEqual({ code: 'COPY' })
    for (const [, init] of fetchMock.mock.calls)
      expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe('exam-test')
  })
  it('preserves server 422 details and does not retry a lost mutation response', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          error_code: 'EXAM_PUBLISH_INVALID',
          message: 'Lỗi',
          request_id: 'fixture',
          details: [{ code: 'EMPTY_SECTION', message: 'Trống' }],
        }),
        { status: 422, headers: { 'Content-Type': 'application/json' } },
      ),
    )
    await expect(publishExam(1, 'e')).rejects.toMatchObject({
      errorCode: 'EXAM_PUBLISH_INVALID',
      details: [{ code: 'EMPTY_SECTION', message: 'Trống' }],
    })
    fetchMock.mockRejectedValueOnce(new TypeError('fixture'))
    await expect(archiveExam(1)).rejects.toBeInstanceOf(ApiError)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(() => getExam(0)).toThrow()
  })
})
