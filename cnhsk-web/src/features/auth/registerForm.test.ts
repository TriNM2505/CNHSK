import { describe, expect, it } from 'vitest'

import { ApiError } from '@/shared/api'

import { getRegisterFailure, normalizeRegisterRequest, validateRegister } from './registerForm'
import type { RegisterRequest } from './types'

const validValues: RegisterRequest = {
  acceptTerms: true,
  confirmPassword: 'HocTieng1',
  displayName: 'Nguyễn An',
  email: 'learner@example.com',
  password: 'HocTieng1',
}

describe('validateRegister', () => {
  it('kiểm tra đủ năm trường bắt buộc', () => {
    expect(
      validateRegister({
        acceptTerms: false,
        confirmPassword: '',
        displayName: ' ',
        email: ' ',
        password: '',
      }),
    ).toEqual({
      acceptTerms: 'Bạn cần đồng ý với điều khoản để đăng ký.',
      displayName: 'Tên hiển thị cần từ 2–100 ký tự.',
      email: 'Vui lòng nhập email.',
      password: 'Mật khẩu cần 8–72 ký tự, gồm chữ và số.',
    })
  })

  it('áp dụng đúng biên tên hiển thị và email sau trim', () => {
    expect(validateRegister({ ...validValues, displayName: ' A ' })).toEqual({
      displayName: 'Tên hiển thị cần từ 2–100 ký tự.',
    })
    expect(validateRegister({ ...validValues, displayName: ` ${'A'.repeat(100)} ` })).toEqual({})
    expect(validateRegister({ ...validValues, displayName: 'A'.repeat(101) })).toEqual({
      displayName: 'Tên hiển thị cần từ 2–100 ký tự.',
    })
    expect(validateRegister({ ...validValues, email: 'khong-hop-le' })).toEqual({
      email: 'Vui lòng nhập email đúng định dạng.',
    })
    expect(validateRegister({ ...validValues, email: `${'a'.repeat(244)}@example.com` })).toEqual({
      email: 'Email không được vượt quá 255 ký tự.',
    })
  })

  it('yêu cầu nhập lại mật khẩu khớp nguyên văn', () => {
    expect(validateRegister({ ...validValues, confirmPassword: 'HocTieng2' })).toEqual({
      confirmPassword: 'Mật khẩu nhập lại chưa khớp.',
    })
  })

  it('chuẩn hoá email và tên nhưng giữ nguyên hai trường mật khẩu', () => {
    expect(
      normalizeRegisterRequest({
        ...validValues,
        confirmPassword: ' HocTieng1 ',
        displayName: '  Nguyễn An  ',
        email: '  Learner@Example.COM ',
        password: ' HocTieng1 ',
      }),
    ).toEqual({
      acceptTerms: true,
      confirmPassword: ' HocTieng1 ',
      displayName: 'Nguyễn An',
      email: 'learner@example.com',
      password: ' HocTieng1 ',
    })
  })
})

describe('getRegisterFailure', () => {
  it('gắn EMAIL_ALREADY_EXISTS vào ô email', () => {
    expect(
      getRegisterFailure(
        new ApiError({
          errorCode: 'EMAIL_ALREADY_EXISTS',
          message: 'technical duplicate details',
          status: 409,
        }),
      ),
    ).toEqual({
      fieldErrors: { email: 'Email này đã được sử dụng.' },
      notice: { kind: 'email-exists' },
    })
  })

  it('chỉ nhận lỗi 422 của năm trường form', () => {
    expect(
      getRegisterFailure(
        new ApiError({
          details: [
            { field: 'displayName', message: 'Tên chưa hợp lệ.' },
            { field: 'acceptTerms', message: 'Bạn chưa đồng ý.' },
            { field: 'internal', message: 'Không được hiển thị.' },
          ],
          errorCode: 'VALIDATION_FAILED',
          message: 'validation',
          status: 422,
        }),
      ),
    ).toEqual({
      fieldErrors: {
        acceptTerms: 'Bạn chưa đồng ý.',
        displayName: 'Tên chưa hợp lệ.',
      },
      notice: { kind: 'validation' },
    })
  })

  it('không lộ thông báo kỹ thuật với lỗi không xác định', () => {
    expect(
      getRegisterFailure(
        new ApiError({
          errorCode: 'INTERNAL_ERROR',
          message: 'java.lang.IllegalStateException',
          requestId: 'req-123',
          status: 500,
        }),
      ).notice,
    ).toEqual({
      kind: 'generic',
      message: 'Không thể đăng ký lúc này. Vui lòng thử lại.',
      requestId: 'req-123',
    })
  })
})
