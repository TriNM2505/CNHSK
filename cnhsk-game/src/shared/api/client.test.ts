import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const csrf = '/api/auth/csrf'
const refresh = '/api/auth/refresh'
const catalog = '/api/community/games'
const answers = catalog + '/rounds/42/answers'

function failure(status: number, error_code: string) {
  return Response.json({ error_code, message: 'Fixture' }, { status })
}

const expired = () => failure(401, 'TOKEN_EXPIRED')
const rejected = () => failure(401, 'INVALID_TOKEN')
const ok = () => Response.json({ ok: true })
const noContent = () => new Response(null, { status: 204 })

let request: typeof import('./client').apiRequest
const fetchMock = vi.fn<(path: string, init: RequestInit) => Promise<Response>>()

beforeEach(async () => {
  vi.resetModules()
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  vi.stubEnv('VITE_API_BASE_URL', '')
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/'
  request = (await import('./client')).apiRequest
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

function calls(path: string) {
  return fetchMock.mock.calls.filter(([url]) => url === path).map(([, init]) => init)
}

describe('AT-19 cookie web', () => {
  it('sends cookies and the WEB header on GET without XSRF or Bearer', async () => {
    document.cookie = 'XSRF-TOKEN=token; Path=/'
    fetchMock.mockResolvedValueOnce(ok())
    await request(catalog, {
      headers: { Authorization: 'Bearer unused', 'X-XSRF-TOKEN': 'unused' },
    })

    const [init] = calls(catalog)
    expect(init?.credentials).toBe('include')
    expect(new Headers(init?.headers).get('X-Client-Type')).toBe('WEB')
    expect(new Headers(init?.headers).has('Authorization')).toBe(false)
    expect(new Headers(init?.headers).has('X-XSRF-TOKEN')).toBe(false)
  })

  it('uses the decoded XSRF cookie and a JSON body for a mutation', async () => {
    document.cookie = 'XSRF-TOKEN=one%2Btwo; Path=/'
    fetchMock.mockResolvedValueOnce(ok())
    await request(answers, { method: 'POST', body: { no: 1, answer: 'A' } })

    expect(calls(csrf)).toHaveLength(0)
    const [init] = calls(answers)
    expect(init?.credentials).toBe('include')
    expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe('one+two')
    expect(new Headers(init?.headers).get('Content-Type')).toBe('application/json')
    expect(init?.body).toBe('{"no":1,"answer":"A"}')
  })

  it('bootstraps a missing cookie once for parallel mutations', async () => {
    fetchMock.mockImplementation(async (path) => {
      if (path !== csrf) return ok()
      document.cookie = 'XSRF-TOKEN=fresh; Path=/'
      return noContent()
    })
    await Promise.all([request(answers, { method: 'POST' }), request(answers, { method: 'POST' })])

    expect(calls(csrf)).toHaveLength(1)
    expect(calls(csrf)[0]?.credentials).toBe('include')
    expect(calls(answers).map(({ headers }) => new Headers(headers).get('X-XSRF-TOKEN'))).toEqual([
      'fresh',
      'fresh',
    ])
  })

  it.each(['TOKEN_EXPIRED', 'SESSION_REVOKED'])('refreshes once for %s', async (code) => {
    fetchMock
      .mockResolvedValueOnce(failure(401, code))
      .mockResolvedValueOnce(noContent())
      .mockResolvedValueOnce(ok())
    await expect(request('/api/auth/me')).resolves.toEqual({ ok: true })

    expect(
      fetchMock.mock.calls.map(([path, init]) => [path, init.method, init.credentials]),
    ).toEqual([
      ['/api/auth/me', 'GET', 'include'],
      [refresh, 'POST', 'include'],
      ['/api/auth/me', 'GET', 'include'],
    ])
    expect(new Headers(calls(refresh)[0]?.headers).has('X-XSRF-TOKEN')).toBe(false)
  })

  it('shares a refresh result with a parallel request whose 401 arrives late', async () => {
    let reply!: (response: Response) => void
    const late = new Promise<Response>((resolve) => {
      reply = resolve
    })
    fetchMock
      .mockResolvedValueOnce(expired())
      .mockReturnValueOnce(late)
      .mockResolvedValueOnce(noContent())
      .mockResolvedValueOnce(ok())
      .mockResolvedValueOnce(ok())
    const first = request('/api/auth/me')
    const second = request(catalog)
    await first
    reply(expired())
    await second
    expect(calls(refresh)).toHaveLength(1)
    expect(calls(catalog)).toHaveLength(2)
  })

  it('stops after a single replay when the access token still fails', async () => {
    fetchMock
      .mockResolvedValueOnce(expired())
      .mockResolvedValueOnce(noContent())
      .mockResolvedValueOnce(expired())
    await expect(request(catalog)).rejects.toMatchObject({ errorCode: 'TOKEN_EXPIRED' })
    expect(calls(refresh)).toHaveLength(1)
    expect(calls(catalog).map((init) => init.credentials)).toEqual(['include', 'include'])
  })

  it('refreshes silently once when the access cookie has expired and been dropped', async () => {
    const { GAME_SESSION_EXPIRED_EVENT } = await import('./client')
    const onExpired = vi.fn()
    window.addEventListener(GAME_SESSION_EXPIRED_EVENT, onExpired)
    fetchMock
      .mockResolvedValueOnce(failure(401, 'UNAUTHENTICATED'))
      .mockResolvedValueOnce(noContent())
      .mockResolvedValueOnce(ok())
    await expect(request('/api/auth/me')).resolves.toEqual({ ok: true })

    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual([
      '/api/auth/me',
      refresh,
      '/api/auth/me',
    ])
    expect(onExpired).not.toHaveBeenCalled()
    window.removeEventListener(GAME_SESSION_EXPIRED_EVENT, onExpired)
  })

  it('keeps a guest a guest when the silent refresh is rejected', async () => {
    const { GAME_SESSION_EXPIRED_EVENT } = await import('./client')
    const onExpired = vi.fn()
    window.addEventListener(GAME_SESSION_EXPIRED_EVENT, onExpired)
    fetchMock
      .mockResolvedValueOnce(failure(401, 'UNAUTHENTICATED'))
      .mockResolvedValueOnce(failure(401, 'INVALID_REFRESH_TOKEN'))
    await expect(request('/api/auth/me')).rejects.toMatchObject({ errorCode: 'UNAUTHENTICATED' })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(onExpired).not.toHaveBeenCalled()
    window.removeEventListener(GAME_SESSION_EXPIRED_EVENT, onExpired)
  })

  it('does not refresh when the refresh endpoint itself is unauthenticated', async () => {
    fetchMock.mockResolvedValueOnce(failure(401, 'UNAUTHENTICATED'))
    await expect(request(refresh, { method: 'POST' })).rejects.toMatchObject({
      errorCode: 'UNAUTHENTICATED',
    })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})

describe('Q-T09-02 catalog guest fallback', () => {
  it('omits credentials once, only after the refresh is rejected, and marks the session guest', async () => {
    const { GAME_SESSION_EXPIRED_EVENT } = await import('./client')
    const onExpired = vi.fn()
    window.addEventListener(GAME_SESSION_EXPIRED_EVENT, onExpired, { once: true })
    const guestCatalog = [{ code: 'CHAR_RAIN', myStats: null }]
    fetchMock
      .mockResolvedValueOnce(expired())
      .mockResolvedValueOnce(rejected())
      .mockResolvedValueOnce(Response.json(guestCatalog))

    await expect(request(catalog)).resolves.toEqual(guestCatalog)
    expect(calls(catalog).map((init) => init.credentials)).toEqual(['include', 'omit'])
    expect(calls(refresh)).toHaveLength(1)
    expect(onExpired).toHaveBeenCalledTimes(1)
  })

  it.each([
    ['/api/auth/me', 'GET'],
    [catalog + '/rounds/42', 'GET'],
    [catalog, 'POST'],
  ])('never omits credentials for %s with %s', async (path, method) => {
    document.cookie = 'XSRF-TOKEN=fixture; Path=/'
    fetchMock.mockResolvedValueOnce(expired()).mockResolvedValueOnce(rejected())
    await expect(request(path, { method })).rejects.toMatchObject({ status: 401 })
    expect(calls(path).map((init) => init.credentials)).toEqual(['include'])
    expect(calls(refresh)).toHaveLength(1)
  })

  it('does not use guest fallback on a refresh network failure', async () => {
    fetchMock.mockResolvedValueOnce(expired()).mockRejectedValueOnce(new TypeError('Offline'))
    await expect(request(catalog)).rejects.toThrow('Offline')
    expect(fetchMock.mock.calls.map(([, init]) => init.credentials)).toEqual(['include', 'include'])
  })

  it('does not loop if the guest request also returns 401', async () => {
    fetchMock
      .mockResolvedValueOnce(expired())
      .mockResolvedValueOnce(rejected())
      .mockResolvedValueOnce(expired())
    await expect(request(catalog)).rejects.toMatchObject({ errorCode: 'TOKEN_EXPIRED' })
    expect(calls(catalog).map((init) => init.credentials)).toEqual(['include', 'omit'])
    expect(calls(refresh)).toHaveLength(1)
  })
})
