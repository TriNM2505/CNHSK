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
const MUTATIONS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])

export const GAME_SESSION_EXPIRED_EVENT = 'game:session-expired'

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly errorCode: string | undefined,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

type ApiRequestInit = Pick<RequestInit, 'method' | 'headers' | 'signal'> & { body?: unknown }

let csrfRequest: Promise<void> | undefined
let authGeneration = 0
let refreshAttempt: { generation: number; result: Promise<void> } | undefined

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
  try {
    const error = JSON.parse(content)
    if (typeof error?.error_code === 'string') errorCode = error.error_code
    if (typeof error?.message === 'string') message = error.message
  } catch {
    // Keep the HTTP status when an intermediary returns a non-JSON response.
  }
  throw new ApiError(response.status, errorCode, message)
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

function notifyExpired() {
  window.dispatchEvent(new Event(GAME_SESSION_EXPIRED_EVENT))
}

function refreshSession(generation: number): Promise<void> {
  if (refreshAttempt && refreshAttempt.generation >= generation) return refreshAttempt.result

  // Requests sent before this attempt share its result even if their 401 arrives later.
  const result = send<void>(AUTH_REFRESH, { method: 'POST', credentials: 'include' })
    .catch((error: unknown) => {
      if (error instanceof ApiError && error.status === 401) notifyExpired()
      throw error
    })
    .finally(() => {
      authGeneration += 1
    })
  refreshAttempt = { generation, result }
  return result
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
    if (!expired(error) || AUTH_RETRY_EXCLUDED.has(pathname)) throw error
    init.signal?.throwIfAborted()
    try {
      await refreshSession(generation)
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
