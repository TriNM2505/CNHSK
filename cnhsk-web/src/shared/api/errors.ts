export const NETWORK_ERROR_MESSAGE =
  'Không thể kết nối tới máy chủ. Vui lòng kiểm tra kết nối và thử lại.'
export const INVALID_RESPONSE_MESSAGE = 'Phản hồi từ máy chủ không hợp lệ. Vui lòng thử lại.'

export type ApiErrorBody = {
  error_code: string
  message: string
  request_id: string
  details?: unknown
  [key: string]: unknown
}

type ApiErrorOptions = {
  status: number
  errorCode: string
  message: string
  requestId?: string
  details?: unknown
  extra?: Record<string, unknown>
}

export class ApiError extends Error {
  readonly status: number
  readonly errorCode: string
  readonly requestId?: string
  readonly details?: unknown
  readonly extra: Record<string, unknown>

  constructor({ status, errorCode, message, requestId, details, extra = {} }: ApiErrorOptions) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.errorCode = errorCode
    this.requestId = requestId
    this.details = details
    this.extra = extra
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

export function parseApiError(status: number, body: unknown): ApiError {
  if (
    !isRecord(body) ||
    typeof body.error_code !== 'string' ||
    typeof body.message !== 'string' ||
    typeof body.request_id !== 'string'
  ) {
    return invalidResponseError(status)
  }

  const { error_code: errorCode, message, request_id: requestId, details, ...extra } = body

  return new ApiError({ status, errorCode, message, requestId, details, extra })
}

export function invalidResponseError(status = 0): ApiError {
  return new ApiError({
    status,
    errorCode: 'INVALID_RESPONSE',
    message: INVALID_RESPONSE_MESSAGE,
  })
}

export function networkError(): ApiError {
  return new ApiError({
    status: 0,
    errorCode: 'NETWORK_ERROR',
    message: NETWORK_ERROR_MESSAGE,
  })
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error
  return networkError()
}
