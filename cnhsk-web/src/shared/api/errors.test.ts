import { describe, expect, it } from 'vitest'

import { ApiError, INVALID_RESPONSE_MESSAGE, parseApiError, toApiError } from './errors'

describe('ApiError', () => {
  it('đọc hợp đồng lỗi và giữ các trường mở rộng', () => {
    const error = parseApiError(429, {
      error_code: 'TOO_MANY_REQUESTS',
      message: 'Vui lòng thử lại sau.',
      request_id: 'req-123',
      details: [{ field: 'email' }],
      retryAfterSeconds: 30,
      blockedUntil: '2026-10-19T10:00:00Z',
    })

    expect(error).toMatchObject({
      status: 429,
      errorCode: 'TOO_MANY_REQUESTS',
      message: 'Vui lòng thử lại sau.',
      requestId: 'req-123',
      details: [{ field: 'email' }],
      extra: {
        retryAfterSeconds: 30,
        blockedUntil: '2026-10-19T10:00:00Z',
      },
    })
  })

  it('không đưa phản hồi sai cấu trúc ra giao diện', () => {
    const error = parseApiError(500, { message: 'java.lang.IllegalStateException' })

    expect(error).toMatchObject({
      status: 500,
      errorCode: 'INVALID_RESPONSE',
      message: INVALID_RESPONSE_MESSAGE,
    })
  })

  it('chuẩn hoá lỗi không xác định thành lỗi mạng an toàn', () => {
    const error = toApiError(new Error('socket details'))

    expect(error).toBeInstanceOf(ApiError)
    expect(error.errorCode).toBe('NETWORK_ERROR')
    expect(error.message).not.toContain('socket')
  })
})
