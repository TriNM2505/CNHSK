export const NEW_PASSWORD_REQUIREMENT = 'Mật khẩu cần 8–72 ký tự, gồm chữ và số.'

const HAS_LETTER = /\p{L}/u
const HAS_NUMBER = /\p{N}/u

export function validateNewPassword(password: string, normalizedEmail: string): string | undefined {
  const hasOuterWhitespace = password !== password.trim()
  const matchesEmail = password === normalizedEmail

  if (
    password.length < 8 ||
    password.length > 72 ||
    !HAS_LETTER.test(password) ||
    !HAS_NUMBER.test(password) ||
    hasOuterWhitespace ||
    matchesEmail
  ) {
    return NEW_PASSWORD_REQUIREMENT
  }

  return undefined
}
