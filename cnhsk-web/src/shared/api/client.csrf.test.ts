import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const API_ORIGIN = 'http://api.test'
const CSRF_EXEMPT_PATHS = [
  '/api/auth/login',
  '/api/auth/register',
  '/api/auth/forgot-password',
  '/api/auth/reset-password',
  '/api/auth/verify-email',
  '/api/auth/refresh',
]

function clearCsrfCookie(): void {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
}

async function loadClient() {
  vi.resetModules()
  return import('./client')
}

describe('API client CSRF exemptions', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', API_ORIGIN)
    clearCsrfCookie()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.unstubAllEnvs()
    clearCsrfCookie()
  })

  it.each(CSRF_EXEMPT_PATHS)('không lấy hoặc gửi CSRF cho endpoint được miễn %s', async (path) => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)
    const { apiRequest } = await loadClient()

    await apiRequest(path, { method: 'POST' })

    expect(fetchMock).toHaveBeenCalledOnce()
    const [, init] = fetchMock.mock.calls[0] ?? []
    expect(new Headers(init?.headers).has('X-XSRF-TOKEN')).toBe(false)
  })

  it('vẫn lấy và gửi CSRF cho endpoint ghi cần bảo vệ', async () => {
    const fetchMock = vi.fn<typeof fetch>(async (input) => {
      if (String(input).endsWith('/api/auth/csrf')) {
        document.cookie = 'XSRF-TOKEN=fresh-token; path=/'
      }
      return new Response(null, { status: 204 })
    })
    vi.stubGlobal('fetch', fetchMock)
    const { apiRequest } = await loadClient()

    await apiRequest('/api/auth/logout-all', { method: 'POST' })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(String(fetchMock.mock.calls[0]?.[0])).toBe(`${API_ORIGIN}/api/auth/csrf`)
    expect(new Headers(fetchMock.mock.calls[1]?.[1]?.headers).get('X-XSRF-TOKEN')).toBe(
      'fresh-token',
    )
  })
})
