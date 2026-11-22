export const NEW_PASSWORD_REQUIREMENT = 'Mật khẩu cần 8–72 ký tự, gồm chữ và số.'
export const PASSWORD_WHITESPACE = 'Mật khẩu không được bắt đầu hoặc kết thúc bằng dấu cách.'
export const PASSWORD_MATCHES_EMAIL = 'Mật khẩu không được trùng email.'

const HAS_LETTER = /\p{L}/u
const HAS_NUMBER = /\p{N}/u

export interface PasswordRule {
  label: string
  met: boolean
}

export function passwordRules(password: string): PasswordRule[] {
  return [
    { label: '8–72 ký tự', met: password.length >= 8 && password.length <= 72 },
    { label: 'Có chữ', met: HAS_LETTER.test(password) },
    { label: 'Có số', met: HAS_NUMBER.test(password) },
  ]
}

export function validateNewPassword(password: string, normalizedEmail: string): string | undefined {
  if (passwordRules(password).some((rule) => !rule.met)) return NEW_PASSWORD_REQUIREMENT
  if (password !== password.trim()) return PASSWORD_WHITESPACE
  if (password === normalizedEmail) return PASSWORD_MATCHES_EMAIL
  return undefined
}
