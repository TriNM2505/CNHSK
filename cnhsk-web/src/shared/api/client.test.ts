import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { type ApiError, INVALID_RESPONSE_MESSAGE } from './errors'

const API_ORIGIN = 'http://api.test'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function apiErrorResponse(status: number, errorCode: string, extra: Record<string, unknown> = {}) {
  return jsonResponse(
    {
      error_code: errorCode,
      message: 'Thông báo an toàn.',
      request_id: `req-${errorCode}`,
      ...extra,
    },
    status,
  )
}

function clearCsrfCookie(): void {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
}

async function loadClient() {
  vi.resetModules()
  return import('./client')
}

describe('apiRequest', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', API_ORIGIN)
    clearCsrfCookie()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.unstubAllEnvs()
    clearCsrfCookie()
  })

  it('gửi cookie phiên, loại client và đọc JSON', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse({ name: 'CNHSK' }))
    vi.stubGlobal('fetch', fetchMock)
    const { apiRequest } = await loadClient()

    await expect(apiRequest<{ name: string }>('/api/profile')).resolves.toEqual({ name: 'CNHSK' })
    const [, init] = fetchMock.mock.calls[0] ?? []

    expect(fetchMock).toHaveBeenCalledWith(`${API_ORIGIN}/api/profile`, expect.any(Object))
    expect(init?.credentials).toBe('include')
    expect(new Headers(init?.headers).get('X-Client-Type')).toBe('WEB')
  })

  it('dùng một refresh cho ba request 401 song song rồi retry từng request một lần', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-token; path=/'
    const attempts = new Map<string, number>()
    const fetchMock = vi.fn<typeof fetch>(async (input) => {
      const path = new URL(String(input)).pathname
      if (path === '/api/auth/refresh') return new Response(null, { status: 204 })

      const attempt = (attempts.get(path) ?? 0) + 1
      attempts.set(path, attempt)
      return attempt === 1
        ? apiErrorResponse(401, 'TOKEN_EXPIRED')
        : jsonResponse({ path, attempt })
    })
    vi.stubGlobal('fetch', fetchMock)
    const { apiRequest } = await loadClient()

    const result = await Promise.all([
      apiRequest('/api/one'),
      apiRequest('/api/two'),
      apiRequest('/api/three'),
    ])

    expect(result).toHaveLength(3)
    expect(
      fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/api/auth/refresh')),
    ).toHaveLength(1)
    expect(attempts).toEqual(
      new Map([
        ['/api/one', 2],
        ['/api/two', 2],
        ['/api/three', 2],
      ]),
    )
  })

  it('phát auth:expired khi refresh thất bại', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-token; path=/'
    const fetchMock = vi.fn<typeof fetch>(async (input) =>
      String(input).endsWith('/api/auth/refresh')
        ? apiErrorResponse(401, 'SESSION_REVOKED')
        : apiErrorResponse(401, 'TOKEN_EXPIRED'),
    )
    vi.stubGlobal('fetch', fetchMock)
    const { apiRequest, AUTH_EXPIRED_EVENT } = await loadClient()
    const listener = vi.fn()
    window.addEventListener(AUTH_EXPIRED_EVENT, listener)

    await expect(apiRequest('/api/profile')).rejects.toMatchObject({ errorCode: 'SESSION_REVOKED' })

    expect(listener).toHaveBeenCalledTimes(1)
    window.removeEventListener(AUTH_EXPIRED_EVENT, listener)
  })

  it('không refresh lần hai và phát auth:expired nếu request retry vẫn 401', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-token; path=/'
    const fetchMock = vi.fn<typeof fetch>(async (input) =>
      String(input).endsWith('/api/auth/refresh')
        ? new Response(null, { status: 204 })
        : apiErrorResponse(401, 'TOKEN_EXPIRED'),
    )
    vi.stubGlobal('fetch', fetchMock)
    const { apiRequest, AUTH_EXPIRED_EVENT } = await loadClient()
    const listener = vi.fn()
    window.addEventListener(AUTH_EXPIRED_EVENT, listener)

    await expect(apiRequest('/api/profile')).rejects.toMatchObject({ errorCode: 'TOKEN_EXPIRED' })

    expect(
      fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/api/auth/refresh')),
    ).toHaveLength(1)
    expect(
      fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/api/profile')),
    ).toHaveLength(2)
    expect(listener).toHaveBeenCalledTimes(1)
    window.removeEventListener(AUTH_EXPIRED_EVENT, listener)
  })

  it.each(['/api/auth/login', '/api/auth/refresh', '/api/auth/logout'])(
    'không tự refresh endpoint %s',
    async (path) => {
      document.cookie = 'XSRF-TOKEN=csrf-token; path=/'
      const fetchMock = vi
        .fn<typeof fetch>()
        .mockResolvedValue(apiErrorResponse(401, 'TOKEN_EXPIRED'))
      vi.stubGlobal('fetch', fetchMock)
      const { apiRequest } = await loadClient()

      await expect(apiRequest(path, { method: 'POST' })).rejects.toMatchObject({
        errorCode: 'TOKEN_EXPIRED',
      })
      expect(fetchMock).toHaveBeenCalledTimes(1)
    },
  )

  it('đọc XSRF từ cookie mới nhất ngay trước request đổi trạng thái', async () => {
    document.cookie = 'XSRF-TOKEN=token%20moi; path=/'
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse({ saved: true }))
    vi.stubGlobal('fetch', fetchMock)
    const { apiRequest } = await loadClient()

    await apiRequest('/api/profile', { method: 'PATCH', body: { name: 'Học viên' } })
    const [, init] = fetchMock.mock.calls[0] ?? []
    const headers = new Headers(init?.headers)

    expect(headers.get('X-XSRF-TOKEN')).toBe('token moi')
    expect(headers.get('Content-Type')).toBe('application/json')
    expect(init?.body).toBe(JSON.stringify({ name: 'Học viên' }))
  })

  it('tự lấy CSRF một lần khi thiếu cookie rồi mới gửi request', async () => {
    const fetchMock = vi.fn<typeof fetch>(async (input) => {
      if (String(input).endsWith('/api/auth/csrf')) {
        document.cookie = 'XSRF-TOKEN=fetched-token; path=/'
        return new Response(null, { status: 204 })
      }
      return jsonResponse({ saved: true })
    })
    vi.stubGlobal('fetch', fetchMock)
    const { apiRequest } = await loadClient()

    await apiRequest('/api/profile', { method: 'PUT', body: { name: 'CNHSK' } })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(String(fetchMock.mock.calls[0]?.[0])).toBe(`${API_ORIGIN}/api/auth/csrf`)
    expect(new Headers(fetchMock.mock.calls[1]?.[1]?.headers).get('X-XSRF-TOKEN')).toBe(
      'fetched-token',
    )
  })

  it('lấy CSRF mới và retry đúng một lần khi token bị từ chối', async () => {
    document.cookie = 'XSRF-TOKEN=old-token; path=/'
    let updateAttempts = 0
    const sentTokens: Array<string | null> = []
    const fetchMock = vi.fn<typeof fetch>(async (input, init) => {
      if (String(input).endsWith('/api/auth/csrf')) {
        document.cookie = 'XSRF-TOKEN=new-token; path=/'
        return new Response(null, { status: 204 })
      }

      updateAttempts += 1
      sentTokens.push(new Headers(init?.headers).get('X-XSRF-TOKEN'))
      return updateAttempts === 1
        ? apiErrorResponse(403, 'CSRF_TOKEN_INVALID')
        : jsonResponse({ saved: true })
    })
    vi.stubGlobal('fetch', fetchMock)
    const { apiRequest } = await loadClient()

    await expect(apiRequest('/api/profile', { method: 'PATCH', body: {} })).resolves.toEqual({
      saved: true,
    })

    expect(sentTokens).toEqual(['old-token', 'new-token'])
    expect(
      fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/api/auth/csrf')),
    ).toHaveLength(1)
    expect(updateAttempts).toBe(2)
  })

  it('dùng chung một lần lấy CSRF mới cho các request bị từ chối đồng thời', async () => {
    document.cookie = 'XSRF-TOKEN=old-token; path=/'
    const attempts = new Map<string, number>()
    const fetchMock = vi.fn<typeof fetch>(async (input) => {
      const path = new URL(String(input)).pathname
      if (path === '/api/auth/csrf') {
        document.cookie = 'XSRF-TOKEN=new-token; path=/'
        return new Response(null, { status: 204 })
      }

      const attempt = (attempts.get(path) ?? 0) + 1
      attempts.set(path, attempt)
      return attempt === 1
        ? apiErrorResponse(403, 'CSRF_TOKEN_INVALID')
        : jsonResponse({ saved: true })
    })
    vi.stubGlobal('fetch', fetchMock)
    const { apiRequest } = await loadClient()

    await Promise.all([
      apiRequest('/api/one', { method: 'PATCH', body: {} }),
      apiRequest('/api/two', { method: 'PATCH', body: {} }),
      apiRequest('/api/three', { method: 'PATCH', body: {} }),
    ])

    expect(
      fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/api/auth/csrf')),
    ).toHaveLength(1)
  })

  it('phát lý do cấm tài khoản và không retry', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValue(apiErrorResponse(403, 'ACCOUNT_BANNED', { banReason: 'Vi phạm quy định' }))
    vi.stubGlobal('fetch', fetchMock)
    const { apiRequest, AUTH_EXPIRED_EVENT } = await loadClient()
    const listener = vi.fn<(event: Event) => void>()
    window.addEventListener(AUTH_EXPIRED_EVENT, listener)

    await expect(apiRequest('/api/profile')).rejects.toMatchObject({ errorCode: 'ACCOUNT_BANNED' })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect((listener.mock.calls[0]?.[0] as CustomEvent).detail).toEqual({
      reason: 'ACCOUNT_BANNED',
      banReason: 'Vi phạm quy định',
    })
    window.removeEventListener(AUTH_EXPIRED_EVENT, listener)
  })

  it.each([200, 202, 204])(
    'trả undefined cho phản hồi thành công %s có body rỗng',
    async (status) => {
      const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status }))
      vi.stubGlobal('fetch', fetchMock)
      const { apiRequest } = await loadClient()

      await expect(apiRequest('/api/ping')).resolves.toBeUndefined()
    },
  )

  it('trả undefined khi phản hồi blob thành công có body rỗng', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const { apiRequest } = await loadClient()

    await expect(apiRequest('/api/empty.csv', { responseType: 'blob' })).resolves.toBeUndefined()
  })

  it('trả nguyên nội dung khi request yêu cầu text', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValue(new Response('Nội dung thuần', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const { apiRequest } = await loadClient()

    await expect(apiRequest('/api/plain-text', { responseType: 'text' })).resolves.toBe(
      'Nội dung thuần',
    )
  })

  it('giữ HTTP status và thông báo an toàn khi phản hồi lỗi có body rỗng', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 400 }))
    vi.stubGlobal('fetch', fetchMock)
    const { apiRequest } = await loadClient()

    await expect(apiRequest('/api/profile')).rejects.toMatchObject({
      status: 400,
      errorCode: 'INVALID_RESPONSE',
      message: INVALID_RESPONSE_MESSAGE,
    })
  })

  it('trả blob cùng tên file tiếng Việt từ Content-Disposition', async () => {
    const csv = '\uFEFFline,column,message\n1,name,Lỗi dữ liệu'
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(csv, {
        status: 200,
        headers: {
          'Content-Type': 'text/csv; charset=UTF-8',
          'Content-Disposition':
            "attachment; filename=errors.csv; filename*=UTF-8''danh-s%C3%A1ch-l%E1%BB%97i.csv",
        },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const { apiRequest } = await loadClient()

    const result = await apiRequest('/api/admin/imports/123/errors.csv', {
      responseType: 'blob',
    })

    if (!result) throw new Error('Thiếu nội dung file tải xuống trong test')
    expect(result.fileName).toBe('danh-sách-lỗi.csv')
    expect(result.blob).toBeInstanceOf(Blob)
    expect(result.blob.size).toBeGreaterThan(0)
    expect(fetchMock.mock.calls[0]?.[1]).not.toHaveProperty('responseType')
  })

  it('đọc lỗi JSON thành ApiError dù request yêu cầu blob', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValue(apiErrorResponse(404, 'IMPORT_RUN_NOT_FOUND'))
    vi.stubGlobal('fetch', fetchMock)
    const { apiRequest } = await loadClient()

    await expect(
      apiRequest('/api/admin/imports/missing/errors.csv', { responseType: 'blob' }),
    ).rejects.toMatchObject({
      status: 404,
      errorCode: 'IMPORT_RUN_NOT_FOUND',
      requestId: 'req-IMPORT_RUN_NOT_FOUND',
    })
  })

  it('chuyển lỗi mạng thành thông báo tiếng Việt an toàn', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockRejectedValue(new TypeError('socket failed'))
    vi.stubGlobal('fetch', fetchMock)
    const { apiRequest } = await loadClient()

    let error: ApiError | undefined
    try {
      await apiRequest('/api/profile')
    } catch (reason) {
      error = reason as ApiError
    }

    expect(error?.errorCode).toBe('NETWORK_ERROR')
    expect(error?.message).not.toContain('socket')
  })

  it('báo phản hồi không hợp lệ và không tự đặt Content-Type cho FormData', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-token; path=/'
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValue(new Response('not-json', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const { apiRequest } = await loadClient()
    const formData = new FormData()
    formData.set('avatar', 'data')

    await expect(
      apiRequest('/api/avatar', { method: 'POST', body: formData }),
    ).rejects.toMatchObject({
      errorCode: 'INVALID_RESPONSE',
    })
    const [, init] = fetchMock.mock.calls[0] ?? []
    expect(new Headers(init?.headers).has('Content-Type')).toBe(false)
  })
})
