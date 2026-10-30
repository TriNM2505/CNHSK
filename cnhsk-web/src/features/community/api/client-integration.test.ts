import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  comment,
  moderationComment,
  moderationPost,
  ownPost,
  postDetail,
  postWrite,
  publishedPage,
  reportCase,
} from '../test-fixtures'

const origin = 'http://api.test'
function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
function errorResponse(status: number, code: string, extra: Record<string, unknown> = {}) {
  return json(
    { error_code: code, message: 'Thông báo an toàn', request_id: 'req-community', ...extra },
    status,
  )
}
function clearCsrf() {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
}
async function loadApis() {
  vi.resetModules()
  const [posts, comments, reports, moderation] = await Promise.all([
    import('./posts'),
    import('./comments'),
    import('./reports'),
    import('./moderation'),
  ])
  return { posts, comments, reports, moderation }
}

describe('community wrappers with the real shared client (§10, §13)', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', origin)
    clearCsrf()
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.unstubAllEnvs()
    clearCsrf()
  })

  it('FR001_public list, detail and comments work as guest and retain credentials include (AT-01 transport)', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(json(publishedPage))
      .mockResolvedValueOnce(json(postDetail))
      .mockResolvedValueOnce(json([comment]))
    vi.stubGlobal('fetch', fetchMock)
    const { posts, comments } = await loadApis()
    const signal = new AbortController().signal
    await expect(posts.listPublishedPosts({}, signal)).resolves.toEqual(publishedPage)
    await expect(posts.getPublishedPost(42, signal)).resolves.toEqual(postDetail)
    await expect(comments.listPostComments(42, signal)).resolves.toEqual([comment])
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      `${origin}/api/public/community/posts?page=0&size=20`,
      `${origin}/api/public/community/posts/42`,
      `${origin}/api/public/community/posts/42/comments`,
    ])
    for (const [, init] of fetchMock.mock.calls) {
      expect(init).toMatchObject({ method: 'GET', credentials: 'include', signal })
      const headers = new Headers(init?.headers)
      expect(headers.get('X-Client-Type')).toBe('WEB')
      expect(headers.has('Authorization')).toBe(false)
      expect(headers.has('X-XSRF-TOKEN')).toBe(false)
      expect(init?.body).toBeUndefined()
    }
  })

  it('FR001_all mutation wrappers use current CSRF and JSON through the real client', async () => {
    document.cookie = 'XSRF-TOKEN=community%20token; path=/'
    const fetchMock = vi.fn<typeof fetch>()
    vi.stubGlobal('fetch', fetchMock)
    const { posts, comments, reports, moderation } = await loadApis()
    const commentWrite = { content: '中文\nBình luận' }
    const reportWrite = { targetType: 'POST', targetId: 42, reason: 'SPAM' } as const
    const rejection = { reason: 'Trùng bài' }
    const decision = { decision: 'REJECT', note: 'Không vi phạm' } as const
    const action = { action: 'HIDE', reason: 'Vi phạm' } as const
    const mutations = [
      {
        invoke: () => posts.createPost(postWrite),
        path: '/api/community/posts',
        method: 'POST',
        body: postWrite,
        response: ownPost,
        status: 201,
      },
      {
        invoke: () => posts.updatePost(42, postWrite),
        path: '/api/community/posts/42',
        method: 'PUT',
        body: postWrite,
        response: ownPost,
      },
      {
        invoke: () => posts.likePost(42),
        path: '/api/community/posts/42/like',
        method: 'POST',
        response: { liked: true, likeCount: 1 },
      },
      {
        invoke: () => posts.unlikePost(42),
        path: '/api/community/posts/42/like',
        method: 'DELETE',
        response: { liked: false, likeCount: 0 },
      },
      {
        invoke: () => comments.createComment(42, commentWrite),
        path: '/api/community/posts/42/comments',
        method: 'POST',
        body: commentWrite,
        response: comment,
        status: 201,
      },
      {
        invoke: () => comments.replyToComment(11, commentWrite),
        path: '/api/community/comments/11/replies',
        method: 'POST',
        body: commentWrite,
        response: comment,
        status: 201,
      },
      {
        invoke: () => comments.updateComment(11, commentWrite),
        path: '/api/community/comments/11',
        method: 'PUT',
        body: commentWrite,
        response: comment,
      },
      {
        invoke: () => comments.deleteComment(11),
        path: '/api/community/comments/11',
        method: 'DELETE',
        response: undefined,
      },
      {
        invoke: () => reports.reportContent(reportWrite),
        path: '/api/community/reports',
        method: 'POST',
        body: reportWrite,
        response: { received: true },
        status: 201,
      },
      {
        invoke: () => moderation.approvePost(42),
        path: '/api/community/moderation/posts/42/approve',
        method: 'PATCH',
        response: moderationPost,
      },
      {
        invoke: () => moderation.rejectPost(42, rejection),
        path: '/api/community/moderation/posts/42/reject',
        method: 'PATCH',
        body: rejection,
        response: moderationPost,
      },
      {
        invoke: () => moderation.decideReportCase(9, decision),
        path: '/api/community/moderation/reports/9',
        method: 'PATCH',
        body: decision,
        response: reportCase,
      },
      {
        invoke: () => moderation.moderateComment(11, action),
        path: '/api/community/moderation/comments/11',
        method: 'PATCH',
        body: action,
        response: moderationComment,
      },
    ]
    for (const mutation of mutations) {
      fetchMock.mockResolvedValueOnce(
        mutation.response === undefined
          ? new Response(null, { status: 204 })
          : json(mutation.response, 'status' in mutation ? mutation.status : 200),
      )
      await expect(mutation.invoke()).resolves.toEqual(mutation.response)
      const [url, init] = fetchMock.mock.calls[fetchMock.mock.calls.length - 1] ?? []
      expect(url).toBe(`${origin}${mutation.path}`)
      expect(init).toMatchObject({ method: mutation.method, credentials: 'include' })
      const headers = new Headers(init?.headers)
      expect(headers.get('X-XSRF-TOKEN')).toBe('community token')
      expect(headers.get('X-Client-Type')).toBe('WEB')
      if ('body' in mutation) {
        expect(init?.body).toBe(JSON.stringify(mutation.body))
        expect(headers.get('Content-Type')).toBe('application/json')
      } else {
        expect(init?.body).toBeUndefined()
        expect(headers.has('Content-Type')).toBe(false)
      }
    }
    expect(fetchMock).toHaveBeenCalledTimes(mutations.length)
  })

  it.each(['TOKEN_EXPIRED', 'SESSION_REVOKED'])(
    'FR001_refreshes supported 401 %s once and retries the community request',
    async (code) => {
      const fetchMock = vi
        .fn<typeof fetch>()
        .mockResolvedValueOnce(errorResponse(401, code))
        .mockResolvedValueOnce(new Response(null, { status: 204 }))
        .mockResolvedValueOnce(json(ownPost))
      vi.stubGlobal('fetch', fetchMock)
      const { posts } = await loadApis()
      const signal = new AbortController().signal
      await expect(posts.getMyPost(42, signal)).resolves.toEqual(ownPost)
      expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
        `${origin}/api/community/posts/mine/42`,
        `${origin}/api/auth/refresh`,
        `${origin}/api/community/posts/mine/42`,
      ])
      expect(fetchMock.mock.calls[1]?.[1]).toMatchObject({ method: 'POST', credentials: 'include' })
      expect(fetchMock.mock.calls[2]?.[1]).toMatchObject({ signal, credentials: 'include' })
    },
  )

  it('FR001_fetches missing CSRF before creating a post and renews an invalid token once', async () => {
    let csrfRequests = 0
    let writes = 0
    const tokens: Array<string | null> = []
    const fetchMock = vi.fn<typeof fetch>(async (input, init) => {
      if (String(input).endsWith('/api/auth/csrf')) {
        csrfRequests += 1
        document.cookie = `XSRF-TOKEN=token-${csrfRequests}; path=/`
        return new Response(null, { status: 204 })
      }
      writes += 1
      tokens.push(new Headers(init?.headers).get('X-XSRF-TOKEN'))
      return writes === 1 ? errorResponse(403, 'CSRF_TOKEN_INVALID') : json(ownPost, 201)
    })
    vi.stubGlobal('fetch', fetchMock)
    const { posts } = await loadApis()
    await expect(posts.createPost(postWrite)).resolves.toEqual(ownPost)
    expect(tokens).toEqual(['token-1', 'token-2'])
    expect(csrfRequests).toBe(2)
    expect(writes).toBe(2)
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      `${origin}/api/auth/csrf`,
      `${origin}/api/community/posts`,
      `${origin}/api/auth/csrf`,
      `${origin}/api/community/posts`,
    ])
  })

  it('FR007_validation details and FR008 rate-limit retry seconds remain ApiError data', async () => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
    const details = [{ field: 'title', message: 'Tối đa 200 ký tự' }]
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(errorResponse(422, 'VALIDATION_FAILED', { details }))
      .mockResolvedValueOnce(errorResponse(429, 'TOO_MANY_REQUESTS', { retryAfterSeconds: 1800 }))
    vi.stubGlobal('fetch', fetchMock)
    const { posts } = await loadApis()
    const { ApiError } = await import('@/shared/api')
    const validation = posts.createPost(postWrite)
    await expect(validation).rejects.toBeInstanceOf(ApiError)
    await expect(validation).rejects.toMatchObject({
      status: 422,
      errorCode: 'VALIDATION_FAILED',
      requestId: 'req-community',
      details,
    })
    await expect(posts.createPost(postWrite)).rejects.toMatchObject({
      status: 429,
      errorCode: 'TOO_MANY_REQUESTS',
      extra: { retryAfterSeconds: 1800 },
    })
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
})
