import { describe, expect, it } from 'vitest'

import { ApiError } from '@/shared/api'

import {
  formatCountdown,
  getResendFailure,
  getVerifyLinkFailure,
  secondsUntil,
} from './verifyEmailFlow'

describe('verifyEmailFlow', () => {
  it('phân biệt token hỏng với lỗi hệ thống', () => {
    expect(
      getVerifyLinkFailure(
        new ApiError({ errorCode: 'INVALID_VERIFY_TOKEN', message: 'x', status: 400 }),
      ),
    ).toEqual({ status: 'invalid' })
    expect(
      getVerifyLinkFailure(
        new ApiError({ errorCode: 'INTERNAL_ERROR', message: 'x', requestId: 'r1', status: 500 }),
      ),
    ).toEqual({ requestId: 'r1', status: 'error' })
  })

  it('429 dùng retryAfterSeconds, thiếu thì mặc định 60 giây', () => {
    const now = 1_000
    expect(
      getResendFailure(
        new ApiError({
          errorCode: 'TOO_MANY_REQUESTS',
          extra: { retryAfterSeconds: 30 },
          message: 'x',
          status: 429,
        }),
        now,
      ),
    ).toEqual({ kind: 'cooldown', retryUntil: now + 30_000 })
    expect(
      getResendFailure(
        new ApiError({ errorCode: 'TOO_MANY_REQUESTS', message: 'x', status: 429 }),
        now,
      ),
    ).toEqual({ kind: 'cooldown', retryUntil: now + 60_000 })
  })

  it('409 đã xác thực và 401 hết phiên có xử lý riêng', () => {
    expect(
      getResendFailure(
        new ApiError({ errorCode: 'EMAIL_ALREADY_VERIFIED', message: 'x', status: 409 }),
        0,
      ),
    ).toEqual({ kind: 'already-verified' })
    expect(
      getResendFailure(
        new ApiError({ errorCode: 'UNAUTHENTICATED', message: 'x', status: 401 }),
        0,
      ),
    ).toMatchObject({ kind: 'error', message: expect.stringContaining('đăng nhập lại') })
  })

  it('đếm ngược làm tròn lên và định dạng m:ss', () => {
    expect(secondsUntil(10_500, 10_000)).toBe(1)
    expect(secondsUntil(9_000, 10_000)).toBe(0)
    expect(formatCountdown(90)).toBe('1:30')
    expect(formatCountdown(5)).toBe('0:05')
  })
})
