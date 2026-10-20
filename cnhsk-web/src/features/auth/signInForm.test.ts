import { describe, expect, it } from 'vitest'

import { ApiError } from '@/shared/api'

import { getSignInFailure, normalizeLoginRequest, validateLogin } from './signInForm'

describe('validateLogin', () => {
  it('yêu cầu email và mật khẩu', () => {
    expect(validateLogin({ email: '  ', password: '' })).toEqual({
      email: 'Vui lòng nhập email.',
      password: 'Vui lòng nhập mật khẩu.',
    })
  })

  it('kiểm tra định dạng và giới hạn độ dài nhưng không áp luật mật khẩu mới', () => {
    expect(validateLogin({ email: 'khong-hop-le', password: 'abc' })).toEqual({
      email: 'Vui lòng nhập email đúng định dạng.',
    })
    expect(
      validateLogin({ email: `${'a'.repeat(246)}@example.com`, password: 'x'.repeat(73) }),
    ).toEqual({
      email: 'Email không được vượt quá 255 ký tự.',
      password: 'Mật khẩu không được vượt quá 72 ký tự.',
    })
  })

  it('chuẩn hoá email trước khi gửi và giữ nguyên mật khẩu', () => {
    expect(normalizeLoginRequest({ email: '  Learner@Example.COM ', password: ' pass ' })).toEqual({
      email: 'learner@example.com',
      password: ' pass ',
    })
  })
})

describe('getSignInFailure', () => {
  it('không phân biệt nguyên nhân của mọi lỗi 401', () => {
    const failure = getSignInFailure(
      new ApiError({ errorCode: 'ANY_401', message: 'Chi tiết không được lộ', status: 401 }),
    )

    expect(failure).toEqual({ fieldErrors: {}, notice: { kind: 'invalid-credentials' } })
  })

  it('đọc thời điểm mở chặn và lý do cấm từ trường mở rộng', () => {
    const blocked = getSignInFailure(
      new ApiError({
        errorCode: 'LOGIN_SOURCE_BLOCKED',
        extra: { blockedUntil: '2026-10-20T14:30:00+07:00' },
        message: 'blocked',
        status: 423,
      }),
    )
    const banned = getSignInFailure(
      new ApiError({
        errorCode: 'ACCOUNT_BANNED',
        extra: { banReason: 'Vi phạm quy định' },
        message: 'banned',
        status: 403,
      }),
    )

    expect(blocked.notice).toEqual({
      blockedUntil: '2026-10-20T14:30:00+07:00',
      kind: 'blocked',
    })
    expect(banned.notice).toEqual({ banReason: 'Vi phạm quy định', kind: 'banned' })
  })

  it('chỉ gắn lỗi 422 vào hai trường thuộc form', () => {
    const failure = getSignInFailure(
      new ApiError({
        details: [
          { field: 'email', message: 'Email chưa hợp lệ.' },
          { field: 'password', message: 'Mật khẩu chưa hợp lệ.' },
          { field: 'internal', message: 'Không được hiển thị.' },
        ],
        errorCode: 'VALIDATION_FAILED',
        message: 'validation',
        status: 422,
      }),
    )

    expect(failure).toEqual({
      fieldErrors: {
        email: 'Email chưa hợp lệ.',
        password: 'Mật khẩu chưa hợp lệ.',
      },
      notice: { kind: 'validation' },
    })
  })

  it('không đưa thông báo kỹ thuật 5xx ra giao diện', () => {
    const failure = getSignInFailure(
      new ApiError({
        errorCode: 'INTERNAL_ERROR',
        message: 'java.lang.IllegalStateException',
        requestId: 'req-123',
        status: 500,
      }),
    )

    expect(failure.notice).toEqual({
      kind: 'generic',
      message: 'Có lỗi xảy ra khi đăng nhập. Vui lòng thử lại.',
      requestId: 'req-123',
    })
  })
})
