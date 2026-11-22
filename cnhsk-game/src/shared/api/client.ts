const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const AUTH_CSRF = '/api/auth/csrf'
const AUTH_REFRESH = '/api/auth/refresh'
const GAME_CATALOG = '/api/community/games'
const AUTH_RETRY_EXCLUDED = new Set(['/api/auth/login', AUTH_REFRESH, '/api/auth/logout'])
const CSRF_EXEMPT = new Set([
  '/api/auth/login',
  '/api/auth/register',
  '/api/auth/forgot-password',
  '/api/auth/reset-password',
  '/api/auth/verify-email',
  AUTH_REFRESH,
  '/api/public/reminder/unsubscribe',
])
const REFRESH_LOCK = 'cnhsk:auth-refresh'
const ROTATION_SETTLE_MS = 400
const MUTATIONS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])

export const GAME_SESSION_EXPIRED_EVENT = 'game:session-expired'

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly errorCode: string | undefined,
    message: string,
    /** The server saw this refresh token being exchanged a moment ago by another tab or the web app. */
    readonly alreadyRotated = false,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

type ApiRequestInit = Pick<RequestInit, 'method' | 'headers' | 'signal'> & { body?: unknown }

let csrfRequest: Promise<void> | undefined
let authGeneration = 0
let refreshAttempt: { generation: number; notify: boolean; result: Promise<void> } | undefined

function readCsrfCookie(): string | undefined {
  const prefix = CSRF_COOKIE + '='
  const entry = document.cookie
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(prefix))
  if (!entry) return undefined
  try {
    return decodeURIComponent(entry.slice(prefix.length)) || undefined
  } catch {
    return undefined
  }
}

async function send<T>(path: string, init: RequestInit): Promise<T> {
  const base = import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') ?? ''
  const headers = new Headers(init.headers)
  headers.set('X-Client-Type', 'WEB')
  headers.delete('Authorization')
  const response = await fetch(base + path, { ...init, headers })
  if (response.status === 204) return undefined as T

  const content = await response.text()
  if (response.ok) return (content ? JSON.parse(content) : undefined) as T

  let errorCode: string | undefined
  let message = 'Không thể hoàn tất yêu cầu.'
  let alreadyRotated = false
  try {
    const error = JSON.parse(content)
    if (typeof error?.error_code === 'string') errorCode = error.error_code
    if (typeof error?.message === 'string') message = error.message
    alreadyRotated = error?.alreadyRotated === true
  } catch {
    // Keep the HTTP status when an intermediary returns a non-JSON response.
  }
  throw new ApiError(response.status, errorCode, message, alreadyRotated)
}

function ensureCsrfCookie(): Promise<void> | undefined {
  if (readCsrfCookie()) return undefined
  csrfRequest ??= send<void>(AUTH_CSRF, { method: 'GET', credentials: 'include' })
    .then(() => {
      if (!readCsrfCookie()) throw new Error('Không nhận được cookie xác thực yêu cầu.')
    })
    .finally(() => {
      csrfRequest = undefined
    })
  return csrfRequest
}

function expired(error: unknown): error is ApiError {
  return (
    error instanceof ApiError &&
    error.status === 401 &&
    (error.errorCode === 'TOKEN_EXPIRED' || error.errorCode === 'SESSION_REVOKED')
  )
}

// The access cookie's Max-Age equals the token lifetime, so once the token expires the browser drops
// the cookie and the server sees no token at all. The refresh cookie may still be valid.
function missing(error: unknown): error is ApiError {
  return error instanceof ApiError && error.status === 401 && error.errorCode === 'UNAUTHENTICATED'
}

function notifyExpired() {
  window.dispatchEvent(new Event(GAME_SESSION_EXPIRED_EVENT))
}

// Tabs of the game share one refresh cookie, so they take turns. The web app runs on another origin
// and cannot take part: when it rotates first, the server says so and its cookies are already ours.
async function rotateTokens(): Promise<void> {
  const rotate = () => send<void>(AUTH_REFRESH, { method: 'POST', credentials: 'include' })
  const locks = (navigator as { locks?: LockManager }).locks
  try {
    await (locks ? locks.request(REFRESH_LOCK, rotate) : rotate())
  } catch (error) {
    if (!(error instanceof ApiError) || !error.alreadyRotated) throw error
    await new Promise((resolve) => window.setTimeout(resolve, ROTATION_SETTLE_MS))
  }
}

// A failed refresh announces an expired session only when a caller knew it had one; a silent probe
// for a missing access cookie fails quietly, so guests never see a session-expired notice.
function refreshSession(generation: number, notify: boolean): Promise<void> {
  if (refreshAttempt && refreshAttempt.generation >= generation) {
    if (notify) refreshAttempt.notify = true
    return refreshAttempt.result
  }

  // Requests sent before this attempt share its result even if their 401 arrives later.
  const attempt = { generation, notify, result: Promise.resolve() }
  attempt.result = rotateTokens()
    .catch((error: unknown) => {
      if (attempt.notify && error instanceof ApiError && error.status === 401) notifyExpired()
      throw error
    })
    .finally(() => {
      authGeneration += 1
    })
  refreshAttempt = attempt
  return attempt.result
}

export async function apiRequest<T>(path: string, init: ApiRequestInit = {}): Promise<T> {
  if (!path.startsWith('/api/')) throw new Error('Đường dẫn API không hợp lệ.')
  const pathname = new URL(path, window.location.origin).pathname
  const method = (init.method ?? 'GET').toUpperCase()
  const csrfRequired = MUTATIONS.has(method) && !CSRF_EXEMPT.has(pathname)
  const headers = new Headers(init.headers)
  headers.delete(CSRF_HEADER)
  const body = init.body === undefined ? undefined : JSON.stringify(init.body)
  if (body !== undefined) headers.set('Content-Type', 'application/json')

  async function request(credentials: RequestCredentials = 'include'): Promise<T> {
    init.signal?.throwIfAborted()
    if (csrfRequired) {
      await ensureCsrfCookie()
      init.signal?.throwIfAborted()
      const token = readCsrfCookie()
      if (!token) throw new Error('Không nhận được cookie xác thực yêu cầu.')
      headers.set(CSRF_HEADER, token)
    }
    return send<T>(path, { method, headers, body, credentials, signal: init.signal })
  }

  const generation = authGeneration
  try {
    return await request()
  } catch (error) {
    if (AUTH_RETRY_EXCLUDED.has(pathname)) throw error
    if (missing(error)) {
      init.signal?.throwIfAborted()
      try {
        await refreshSession(generation, false)
      } catch {
        // No usable refresh cookie either: the caller gets the original "not signed in" error.
        throw error
      }
      return request()
    }
    if (!expired(error)) throw error
    init.signal?.throwIfAborted()
    try {
      await refreshSession(generation, true)
    } catch (refreshError) {
      if (
        method === 'GET' &&
        path === GAME_CATALOG &&
        refreshError instanceof ApiError &&
        refreshError.status === 401
      ) {
        return request('omit')
      }
      throw refreshError
    }
    try {
      return await request()
    } catch (retryError) {
      if (expired(retryError)) notifyExpired()
      throw retryError
    }
  }
}
