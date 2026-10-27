import { ApiError, invalidResponseError, networkError, parseApiError } from './errors'

const CLIENT_TYPE = 'WEB'
const CSRF_COOKIE_NAME = 'XSRF-TOKEN'
const CSRF_HEADER_NAME = 'X-XSRF-TOKEN'
const AUTH_EXCLUDED_PATHS = new Set(['/api/auth/login', '/api/auth/refresh', '/api/auth/logout'])
const CSRF_EXEMPT_PATHS = new Set([
  '/api/auth/login',
  '/api/auth/register',
  '/api/auth/forgot-password',
  '/api/auth/reset-password',
  '/api/auth/verify-email',
  '/api/auth/refresh',
])
const STATE_CHANGING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])

export const AUTH_EXPIRED_EVENT = 'auth:expired'

export type AuthExpiredDetail = {
  reason: 'ACCOUNT_BANNED'
  banReason?: unknown
}

export type ApiResponseType = 'json' | 'blob' | 'text'

export type BlobApiResponse = {
  blob: Blob
  fileName?: string
}

export type ApiRequestInit = Omit<RequestInit, 'body'> & {
  body?: BodyInit | object | null
  responseType?: ApiResponseType
}

type RetryState = {
  auth: boolean
  csrf: boolean
}

let refreshPromise: Promise<void> | null = null
let csrfPromise: Promise<void> | null = null
let authGeneration = 0
let csrfGeneration = 0

function buildUrl(path: string): string {
  const baseUrl = import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') ?? ''
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${baseUrl}${normalizedPath}`
}

function getPathname(path: string): string {
  try {
    return new URL(buildUrl(path), window.location.origin).pathname
  } catch {
    return path.split('?')[0] ?? path
  }
}

function readCookie(name: string): string | undefined {
  if (typeof document === 'undefined') return undefined

  const prefix = `${encodeURIComponent(name)}=`
  const cookie = document.cookie
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(prefix))

  if (!cookie) return undefined

  try {
    return decodeURIComponent(cookie.slice(prefix.length))
  } catch {
    return undefined
  }
}

function dispatchAuthExpired(detail?: AuthExpiredDetail): void {
  if (typeof window === 'undefined') return
  window.dispatchEvent(
    new CustomEvent<AuthExpiredDetail | undefined>(AUTH_EXPIRED_EVENT, { detail }),
  )
}

function isJsonBody(body: ApiRequestInit['body']): body is object {
  if (body === null || body === undefined || typeof body !== 'object') return false
  if (typeof FormData !== 'undefined' && body instanceof FormData) return false
  if (typeof Blob !== 'undefined' && body instanceof Blob) return false
  if (typeof URLSearchParams !== 'undefined' && body instanceof URLSearchParams) return false
  if (typeof ReadableStream !== 'undefined' && body instanceof ReadableStream) return false
  if (body instanceof ArrayBuffer || ArrayBuffer.isView(body)) return false
  return true
}

function prepareBody(body: ApiRequestInit['body'], headers: Headers): BodyInit | null | undefined {
  if (!isJsonBody(body)) return body as BodyInit | null | undefined
  if (!headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  return JSON.stringify(body)
}

function parseFileName(contentDisposition: string | null): string | undefined {
  if (!contentDisposition) return undefined

  const extendedMatch = /(?:^|;)\s*filename\*\s*=\s*(?:"([^"]*)"|([^;]*))/i.exec(contentDisposition)
  const extendedValue = (extendedMatch?.[1] ?? extendedMatch?.[2])?.trim()
  const encodedName = /^UTF-8''(.+)$/i.exec(extendedValue ?? '')?.[1]

  if (encodedName) {
    try {
      return decodeURIComponent(encodedName)
    } catch {
      // Thử tên dự phòng `filename` nếu giá trị mở rộng không hợp lệ.
    }
  }

  const fallbackMatch = /(?:^|;)\s*filename\s*=\s*(?:"((?:\\.|[^"])*)"|([^;]*))/i.exec(
    contentDisposition,
  )
  const fallbackName = (fallbackMatch?.[1] ?? fallbackMatch?.[2])?.trim()
  return fallbackName?.replace(/\\"/g, '"') || undefined
}

function parseJson(status: number, text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    throw invalidResponseError(status)
  }
}

function throwApiError(response: Response, text: string): never {
  const error =
    text === ''
      ? invalidResponseError(response.status)
      : parseApiError(response.status, parseJson(response.status, text))
  if (error.status === 403 && error.errorCode === 'ACCOUNT_BANNED') {
    dispatchAuthExpired({ reason: 'ACCOUNT_BANNED', banReason: error.extra.banReason })
  }
  throw error
}

async function parseResponse<T>(response: Response, responseType: ApiResponseType): Promise<T> {
  if (!response.ok) {
    const text = await response.text()
    return throwApiError(response, text)
  }

  if (response.status === 204) return undefined as T

  if (responseType === 'blob') {
    const blob = await response.blob()
    if (blob.size === 0) return undefined as T

    return {
      blob,
      fileName: parseFileName(response.headers.get('Content-Disposition')),
    } as T
  }

  const text = await response.text()
  if (text === '') return undefined as T
  if (responseType === 'text') return text as T

  return parseJson(response.status, text) as T
}

async function fetchResponse<T>(
  url: string,
  init: RequestInit,
  responseType: ApiResponseType = 'json',
): Promise<T> {
  let response: Response
  try {
    response = await fetch(url, init)
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw networkError()
  }
  return parseResponse<T>(response, responseType)
}

async function fetchCsrfToken(force: boolean): Promise<void> {
  if (!force && readCookie(CSRF_COOKIE_NAME)) return
  if (csrfPromise) return csrfPromise

  csrfPromise = (async () => {
    await fetchResponse<void>(buildUrl('/api/auth/csrf'), {
      method: 'GET',
      credentials: 'include',
      headers: { 'X-Client-Type': CLIENT_TYPE },
    })
    csrfGeneration += 1

    if (!readCookie(CSRF_COOKIE_NAME)) throw invalidResponseError()
  })().finally(() => {
    csrfPromise = null
  })

  return csrfPromise
}

async function refreshSession(): Promise<void> {
  if (refreshPromise) return refreshPromise

  refreshPromise = requestInternal<void>(
    '/api/auth/refresh',
    { method: 'POST' },
    { auth: true, csrf: false },
  )
    .then(() => {
      authGeneration += 1
    })
    .catch((error: unknown) => {
      if (!(error instanceof ApiError && error.errorCode === 'ACCOUNT_BANNED')) {
        dispatchAuthExpired()
      }
      throw error
    })
    .finally(() => {
      refreshPromise = null
    })

  return refreshPromise
}

function isExpiredSession(error: ApiError): boolean {
  return (
    error.status === 401 &&
    (error.errorCode === 'TOKEN_EXPIRED' || error.errorCode === 'SESSION_REVOKED')
  )
}

async function requestInternal<T>(
  path: string,
  init: ApiRequestInit,
  retry: RetryState,
): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const changesState = STATE_CHANGING_METHODS.has(method)
  const requiresCsrf = changesState && !CSRF_EXEMPT_PATHS.has(getPathname(path))
  const { responseType = 'json', ...fetchInit } = init

  if (requiresCsrf && !readCookie(CSRF_COOKIE_NAME)) await fetchCsrfToken(false)

  const headers = new Headers(init.headers)
  headers.set('X-Client-Type', CLIENT_TYPE)

  const csrfToken = requiresCsrf ? readCookie(CSRF_COOKIE_NAME) : undefined
  if (csrfToken) headers.set(CSRF_HEADER_NAME, csrfToken)
  else headers.delete(CSRF_HEADER_NAME)

  const body = prepareBody(init.body, headers)
  const authGenerationAtSend = authGeneration
  const csrfGenerationAtSend = csrfGeneration

  try {
    return await fetchResponse<T>(
      buildUrl(path),
      {
        ...fetchInit,
        method,
        body,
        headers,
        credentials: 'include',
      },
      responseType,
    )
  } catch (error) {
    if (!(error instanceof ApiError)) throw error

    if (
      requiresCsrf &&
      error.status === 403 &&
      error.errorCode === 'CSRF_TOKEN_INVALID' &&
      !retry.csrf
    ) {
      if (csrfGeneration === csrfGenerationAtSend) await fetchCsrfToken(true)
      return requestInternal<T>(path, init, { ...retry, csrf: true })
    }

    if (isExpiredSession(error) && !AUTH_EXCLUDED_PATHS.has(getPathname(path))) {
      if (retry.auth) {
        dispatchAuthExpired()
        throw error
      }

      if (authGeneration === authGenerationAtSend) await refreshSession()
      return requestInternal<T>(path, init, { ...retry, auth: true })
    }

    throw error
  }
}

export function apiRequest(
  path: string,
  init: ApiRequestInit & { responseType: 'blob' },
): Promise<BlobApiResponse | undefined>
export function apiRequest(
  path: string,
  init: ApiRequestInit & { responseType: 'text' },
): Promise<string | undefined>
export function apiRequest<T>(
  path: string,
  init?: ApiRequestInit & { responseType?: 'json' },
): Promise<T>
export function apiRequest<T>(path: string, init: ApiRequestInit = {}): Promise<T> {
  return requestInternal<T>(path, init, { auth: false, csrf: false })
}
