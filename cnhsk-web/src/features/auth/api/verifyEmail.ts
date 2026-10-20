import { apiRequest } from '@/shared/api'

import type { EmailVerifiedResponse, TokenRequest } from '../types'

/** FR-009, FR-094: xác thực bằng token trong email; gọi lại token đã dùng vẫn trả 200. */
export function verifyEmail(token: string): Promise<EmailVerifiedResponse> {
  const body: TokenRequest = { token }
  return apiRequest<EmailVerifiedResponse>('/api/auth/verify-email', { body, method: 'POST' })
}

/** FR-012: gửi lại email cho tài khoản đang đăng nhập; server trả 202 không có nội dung. */
export function resendVerificationEmail(): Promise<void> {
  return apiRequest<void>('/api/auth/verify-email/resend', { method: 'POST' })
}
