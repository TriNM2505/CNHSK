import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { contestFixture, examFixture, participantFixtures, winnerFixtures } from '../test-fixtures'
import { draftOf, writeOf } from '../contestForm'
import {
  decideParticipant,
  findAttachedExam,
  getContest,
  listContests,
  listExams,
  listParticipants,
  revealWinners,
  saveContest,
  transitionContest,
} from './contests'

const fetchMock = vi.fn<typeof fetch>()
function json(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}
beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockReset().mockImplementation(async () => json(contestFixture))
  document.cookie = 'XSRF-TOKEN=contest-test-token; Path=/'
})
afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/'
})

describe('contest admin HTTP contracts', () => {
  it('reads exact contest filters and pages with abort support and cookies', async () => {
    const signal = new AbortController().signal
    await listContests({ status: 'CANCELLED', page: 2, size: 20 }, signal)
    const [url, init] = fetchMock.mock.calls[0]!
    expect(url).toBe('/api/admin/contests?page=2&size=20&status=CANCELLED')
    expect(init).toMatchObject({ method: 'GET', signal, credentials: 'include' })
    await getContest(2109, signal)
    expect(fetchMock.mock.lastCall?.[0]).toBe('/api/admin/contests/2109')
  })
  it('uses POST, PUT, PATCH and cookie CSRF for all writes, without sending client scores', async () => {
    const input = writeOf(draftOf(contestFixture))
    await saveContest(null, input)
    await saveContest(2109, input)
    await transitionContest(2109, 'publish')
    await transitionContest(2109, 'cancel')
    await transitionContest(2109, 'ranking')
    await decideParticipant(2109, 32, { excluded: false, note: 'Đã kiểm tra lượt thi.' })
    expect(fetchMock.mock.calls.map(([url, init]) => [url, init?.method])).toEqual([
      ['/api/admin/contests', 'POST'],
      ['/api/admin/contests/2109', 'PUT'],
      ['/api/admin/contests/2109/publish', 'POST'],
      ['/api/admin/contests/2109/cancel', 'POST'],
      ['/api/admin/contests/2109/ranking/publish', 'POST'],
      ['/api/admin/contests/2109/participants/32', 'PATCH'],
    ])
    for (const [, init] of fetchMock.mock.calls) {
      expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe('contest-test-token')
      expect(init?.credentials).toBe('include')
    }
    expect(JSON.parse(String(fetchMock.mock.calls[0]![1]?.body))).toEqual(input)
    expect(JSON.parse(String(fetchMock.mock.lastCall?.[1]?.body))).toEqual({
      excluded: false,
      note: 'Đã kiểm tra lượt thi.',
    })
    expect(input).not.toHaveProperty('score')
  })
  it('keeps participants private and requests winners with no-store every time', async () => {
    fetchMock
      .mockResolvedValueOnce(json(participantFixtures))
      .mockResolvedValueOnce(json(winnerFixtures))
      .mockResolvedValueOnce(json(winnerFixtures))
    expect(await listParticipants(2109)).toEqual(participantFixtures)
    const signal = new AbortController().signal
    expect(await revealWinners(2109, signal)).toEqual(winnerFixtures)
    await revealWinners(2109, signal)
    expect(fetchMock).toHaveBeenCalledTimes(3)
    for (const [url, init] of fetchMock.mock.calls.slice(1)) {
      expect(url).toBe('/api/admin/contests/2109/winners')
      expect(init).toMatchObject({ cache: 'no-store', signal, credentials: 'include' })
    }
  })
  it('looks up an attached archived exam beyond page one using only the approved list API', async () => {
    const archived = { ...examFixture, id: 81, status: 'ARCHIVED' }
    fetchMock
      .mockResolvedValueOnce(
        json({
          items: Array.from({ length: 50 }, (_, i) => ({ ...examFixture, id: i + 1 })),
          page: 0,
          size: 50,
          totalElements: 51,
        }),
      )
      .mockResolvedValueOnce(json({ items: [archived], page: 1, size: 50, totalElements: 51 }))
    expect(await findAttachedExam(81)).toEqual(archived)
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      '/api/admin/exams?page=0&size=50',
      '/api/admin/exams?page=1&size=50',
    ])
  })
  it('encodes search, returns missing only at the end, and stops an aborted lookup', async () => {
    await listExams({ q: 'đề & sách', page: 1 })
    expect(
      new URL(String(fetchMock.mock.lastCall?.[0]), 'https://example.test').searchParams.get('q'),
    ).toBe('đề & sách')
    fetchMock.mockResolvedValueOnce(json({ items: [], page: 0, size: 50, totalElements: 0 }))
    expect(await findAttachedExam(99)).toBeNull()
    fetchMock.mockClear()
    const controller = new AbortController()
    controller.abort()
    await expect(findAttachedExam(99, controller.signal)).rejects.toMatchObject({
      name: 'AbortError',
    })
    expect(fetchMock).not.toHaveBeenCalled()
  })
  it('rejects invalid identifiers before making any request', () => {
    for (const id of [0, -1, 1.2, Number.MAX_SAFE_INTEGER + 1])
      expect(() => getContest(id)).toThrow()
    expect(() => decideParticipant(2109, 0, { excluded: true, note: 'Lý do' })).toThrow()
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
