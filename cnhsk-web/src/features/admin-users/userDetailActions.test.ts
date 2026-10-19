import { describe, expect, it } from 'vitest'

import { ApiError } from '@/shared/api'

import { mapUserDetailActionError, validateBanReason } from './userDetailActions'

describe('user detail actions', () => {
  it('trim và kiểm tra lý do cấm trong khoảng 5–500 ký tự', () => {
    expect(validateBanReason('  abc  ')).toMatchObject({ error: expect.any(String), value: 'abc' })
    expect(validateBanReason('  vi phạm  ')).toEqual({ value: 'vi phạm' })
    expect(validateBanReason('x'.repeat(501))).toMatchObject({ error: expect.any(String) })
  })

  it.each([
    ['SELF_ROLE_GRANT', 'Bạn không thể tự cấp vai trò cho chính mình.'],
    ['SELF_ROLE_REVOKE', 'Bạn không thể tự thu hồi vai trò ADMIN của chính mình.'],
    ['SELF_BAN', 'Bạn không thể cấm tài khoản của chính mình.'],
    ['REVOKE_LAST_ADMIN', 'Đây là quản trị viên cuối cùng còn hoạt động.'],
    ['BAN_LAST_ADMIN', 'Đây là quản trị viên cuối cùng còn hoạt động.'],
  ])('map mã lỗi %s sang tiếng Việt', (errorCode, message) => {
    expect(
      mapUserDetailActionError(
        new ApiError({ errorCode, message: 'server message', requestId: 'req-21', status: 403 }),
      ),
    ).toMatchObject({ message, requestId: 'req-21' })
  })

  it('gắn lỗi 422 của reason vào trường lý do', () => {
    expect(
      mapUserDetailActionError(
        new ApiError({
          details: { reason: 'Lý do phải có ít nhất 5 ký tự.' },
          errorCode: 'VALIDATION_ERROR',
          message: 'Invalid',
          status: 422,
        }),
      ),
    ).toMatchObject({ fieldError: 'Lý do phải có ít nhất 5 ký tự.' })
  })
})
