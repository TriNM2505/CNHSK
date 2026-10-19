const SIMPLE_EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/** FR-095: email gửi lên server luôn bỏ khoảng trắng hai đầu và viết thường. */
export function normalizeEmail(email: string): string {
  return email.trim().toLowerCase()
}

/** Kiểm email phía client (server vẫn kiểm lại). Trả thông báo lỗi hoặc undefined. */
export function validateEmail(email: string): string | undefined {
  const value = email.trim()
  if (!value) return 'Vui lòng nhập email.'
  if (value.length > 255) return 'Email không được vượt quá 255 ký tự.'
  if (!SIMPLE_EMAIL_PATTERN.test(value)) return 'Vui lòng nhập email đúng định dạng.'
  return undefined
}
