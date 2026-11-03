import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { SessionView } from '@/features/practice/types'
import { ApiError } from '@/shared/api'

import type { MasteryNodeDetail, MasteryTree, Recommendation, ReviewStartRequest } from '../types'
import { getMyMastery, getMyMasteryNode, getMyRecommendations } from './mastery'
import { getReviewSummary, startReviewSession } from './reviews'

const origin = 'http://api.test'
const tree: MasteryTree = {
  nodes: [
    {
      kid: 'NP1-901',
      value: null,
      confidence: 0,
      evidenceCount: 0,
      state: 'notyet',
      frontier: true,
    },
  ],
}
const detail: MasteryNodeDetail = {
  ...tree.nodes[0]!,
  dueAt: null,
  prereqs: [],
  evidence: [],
  approvedQuestionCount: 0,
}
const recommendations: Recommendation[] = [
  { kid: 'NP1-901', nameVi: 'Câu tự soạn', kind: 'NEW', reason: 'Có thể bắt đầu học.' },
]
const session: SessionView = {
  sessionId: 11,
  source: 'DUE_REVIEW',
  feedback: 'IMMEDIATE',
  status: 'IN_PROGRESS',
  servedAt: '2026-11-04T10:00:00Z',
  expiresAt: '2026-11-04T12:00:00Z',
  items: [
    { no: 1, type: 'FLASHCARD_RATING', skill: 'GRAMMAR', prompt: { text: '我看书。' }, rules: {} },
  ],
  answered: [],
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
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
  clearCsrf()
})

describe('mastery API contract (§13; FR-001 and AT-01 transport only)', () => {
  it('uses the four GET paths with the current session and preserves optional/nullable fields', async () => {
    const summary = { dueCount: 0, nextDueAt: null }
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(json(tree))
      .mockResolvedValueOnce(json(detail))
      .mockResolvedValueOnce(json(recommendations))
      .mockResolvedValueOnce(json(summary))
    vi.stubGlobal('fetch', fetchMock)
    const signal = new AbortController().signal

    await expect(getMyMastery(signal)).resolves.toEqual(tree)
    await expect(getMyMasteryNode('NP1-901', signal)).resolves.toEqual(detail)
    await expect(getMyRecommendations(signal)).resolves.toEqual(recommendations)
    await expect(getReviewSummary(signal)).resolves.toEqual(summary)
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      `${origin}/api/mastery`,
      `${origin}/api/mastery/nodes/NP1-901`,
      `${origin}/api/mastery/recommendations`,
      `${origin}/api/reviews/summary`,
    ])
    for (const [, init] of fetchMock.mock.calls) {
      expect(init).toMatchObject({ method: 'GET', credentials: 'include', signal })
      expect(init?.body).toBeUndefined()
      const headers = new Headers(init?.headers)
      expect(headers.get('X-Client-Type')).toBe('WEB')
      expect(headers.has('Authorization')).toBe(false)
      expect(headers.has('X-XSRF-TOKEN')).toBe(false)
    }
  })

  it('encodes a KID as one path segment and preserves empty responses', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(json(detail))
      .mockResolvedValueOnce(json({ nodes: [] }))
      .mockResolvedValueOnce(json([]))
    vi.stubGlobal('fetch', fetchMock)
    await getMyMasteryNode('NP1/901?x=1#nút')
    expect(fetchMock.mock.calls[0]![0]).toBe(
      `${origin}/api/mastery/nodes/NP1%2F901%3Fx%3D1%23n%C3%BAt`,
    )
    await expect(getMyMastery()).resolves.toEqual({ nodes: [] })
    await expect(getMyRecommendations()).resolves.toEqual([])
  })

  it.each([
    { label: 'new session without body', request: undefined, response: session, status: 201 },
    { label: 'resumed session', request: { ahead: false }, response: session, status: 200 },
    { label: 'review ahead', request: { ahead: true }, response: session, status: 201 },
    {
      label: 'nothing due',
      request: {},
      response: { session: null, reason: 'NOTHING_DUE' },
      status: 200,
    },
  ] satisfies {
    label: string
    request: ReviewStartRequest | undefined
    response: unknown
    status: number
  }[])('POST handles $label with shared CSRF', async ({ request, response, status }) => {
    document.cookie = 'XSRF-TOKEN=mastery%20token; path=/'
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(json(response, status))
    vi.stubGlobal('fetch', fetchMock)
    const signal = new AbortController().signal
    await expect(startReviewSession(request, signal)).resolves.toEqual(response)
    expect(fetchMock).toHaveBeenCalledOnce()
    const [url, init] = fetchMock.mock.calls[0]!
    expect(url).toBe(`${origin}/api/reviews/sessions`)
    expect(init).toMatchObject({ method: 'POST', credentials: 'include', signal })
    expect(init?.body).toBe(request === undefined ? undefined : JSON.stringify(request))
    const headers = new Headers(init?.headers)
    expect(headers.get('X-XSRF-TOKEN')).toBe('mastery token')
    if (request !== undefined) {
      expect(headers.get('Content-Type')).toBe('application/json')
      expect(JSON.parse(String(init?.body))).not.toHaveProperty('userId')
    }
  })

  it('obtains a missing CSRF cookie before posting', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockImplementation(async (url) => {
      if (String(url).endsWith('/api/auth/csrf')) {
        document.cookie = 'XSRF-TOKEN=mastery-fetched; path=/'
        return new Response(null, { status: 204 })
      }
      return json(session, 201)
    })
    vi.stubGlobal('fetch', fetchMock)
    await startReviewSession()
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      `${origin}/api/auth/csrf`,
      `${origin}/api/reviews/sessions`,
    ])
    expect(new Headers(fetchMock.mock.calls[1]![1]?.headers).get('X-XSRF-TOKEN')).toBe(
      'mastery-fetched',
    )
  })

  it.each([
    { status: 401, errorCode: 'UNAUTHENTICATED', invoke: () => getMyMastery() },
    { status: 401, errorCode: 'UNAUTHENTICATED', invoke: () => startReviewSession() },
    { status: 403, errorCode: 'FORBIDDEN', invoke: () => getMyMastery() },
    { status: 403, errorCode: 'FORBIDDEN', invoke: () => startReviewSession() },
    {
      status: 404,
      errorCode: 'KNOWLEDGE_NODE_NOT_FOUND',
      invoke: () => getMyMasteryNode('NP1-999'),
    },
    { status: 422, errorCode: 'VALIDATION_FAILED', invoke: () => startReviewSession() },
    { status: 429, errorCode: 'RATE_LIMITED', invoke: () => startReviewSession() },
  ])('preserves $status $errorCode', async ({ status, errorCode, invoke }) => {
    document.cookie = 'XSRF-TOKEN=mastery-token; path=/'
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      json(
        {
          error_code: errorCode,
          message: 'Thông báo kiểm thử.',
          request_id: 'req-mastery',
          ...(status === 429 ? { retryAfterSeconds: 60 } : {}),
        },
        status,
      ),
    )
    vi.stubGlobal('fetch', fetchMock)
    const result = invoke()
    await expect(result).rejects.toBeInstanceOf(ApiError)
    await expect(result).rejects.toMatchObject({ status, errorCode, requestId: 'req-mastery' })
    if (status === 429)
      await expect(result).rejects.toMatchObject({ extra: { retryAfterSeconds: 60 } })
    expect(fetchMock).toHaveBeenCalledOnce()
  })

  it('surfaces CSRF_TOKEN_INVALID after the shared client retries once', async () => {
    document.cookie = 'XSRF-TOKEN=mastery-stale; path=/'
    const fetchMock = vi.fn<typeof fetch>().mockImplementation(async (url) => {
      if (String(url).endsWith('/api/auth/csrf')) {
        document.cookie = 'XSRF-TOKEN=mastery-refreshed; path=/'
        return new Response(null, { status: 204 })
      }
      return json(
        {
          error_code: 'CSRF_TOKEN_INVALID',
          message: 'Phiên không hợp lệ.',
          request_id: 'req-csrf',
        },
        403,
      )
    })
    vi.stubGlobal('fetch', fetchMock)
    await expect(startReviewSession({ ahead: true })).rejects.toMatchObject({
      status: 403,
      errorCode: 'CSRF_TOKEN_INVALID',
      requestId: 'req-csrf',
    })
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      `${origin}/api/reviews/sessions`,
      `${origin}/api/auth/csrf`,
      `${origin}/api/reviews/sessions`,
    ])
    expect(fetchMock.mock.calls[2]![1]?.body).toBe(JSON.stringify({ ahead: true }))
    expect(new Headers(fetchMock.mock.calls[2]![1]?.headers).get('X-XSRF-TOKEN')).toBe(
      'mastery-refreshed',
    )
  })

  it('propagates cancellation', async () => {
    const aborted = new DOMException('Aborted', 'AbortError')
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockRejectedValue(aborted))
    const controller = new AbortController()
    controller.abort()
    await expect(getMyMastery(controller.signal)).rejects.toBe(aborted)
  })
})
