import { describe, expect, it } from 'vitest'

import {
  NEW_PASSWORD_REQUIREMENT,
  PASSWORD_MATCHES_EMAIL,
  PASSWORD_WHITESPACE,
  passwordRules,
  validateNewPassword,
} from './passwordValidation'

describe('validateNewPassword', () => {
  it.each(['Abc1234', 'A'.repeat(72), 'A'.repeat(72) + '1', 'abcdefgh', '12345678'])(
    'từ chối mật khẩu không đạt quy tắc: %s',
    (password) => {
      expect(validateNewPassword(password, 'learner@example.com')).toBe(NEW_PASSWORD_REQUIREMENT)
    },
  )

  it('chấp nhận đúng hai biên 8 và 72 ký tự khi có chữ và số', () => {
    expect(validateNewPassword('Abcdefg1', 'learner@example.com')).toBeUndefined()
    expect(validateNewPassword(`${'A'.repeat(71)}1`, 'learner@example.com')).toBeUndefined()
  })

  it('không tự trim khoảng trắng và từ chối mật khẩu trùng email đã chuẩn hoá', () => {
    expect(validateNewPassword(' Abcdefg1', 'learner@example.com')).toBe(PASSWORD_WHITESPACE)
    expect(validateNewPassword('Abcdefg1 ', 'learner@example.com')).toBe(PASSWORD_WHITESPACE)
    expect(validateNewPassword('learner1@example.com', 'learner1@example.com')).toBe(
      PASSWORD_MATCHES_EMAIL,
    )
  })

  it('đánh dấu từng điều kiện theo nội dung đang gõ', () => {
    expect(passwordRules('abc').map((rule) => rule.met)).toEqual([false, true, false])
    expect(passwordRules('HocTieng1').every((rule) => rule.met)).toBe(true)
  })
})
