import { describe, expect, it } from 'vitest'

import { NEW_PASSWORD_REQUIREMENT, validateNewPassword } from './passwordValidation'

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
    expect(validateNewPassword(' Abcdefg1', 'learner@example.com')).toBe(NEW_PASSWORD_REQUIREMENT)
    expect(validateNewPassword('Abcdefg1 ', 'learner@example.com')).toBe(NEW_PASSWORD_REQUIREMENT)
    expect(validateNewPassword('learner@example.com', 'learner@example.com')).toBe(
      NEW_PASSWORD_REQUIREMENT,
    )
  })
})
