import { apiRequest } from '@/shared/api'

import type { EmailRequest, ForgotPasswordResponse, ResetPasswordRequest } from '../types'

/** FR-038: luôn trả 202 cùng thông báo, dù email có tồn tại hay không. */
export function requestPasswordReset(email: string): Promise<ForgotPasswordResponse> {
  const body: EmailRequest = { email }
  return apiRequest<ForgotPasswordResponse>('/api/auth/forgot-password', { body, method: 'POST' })
}

/** FR-042: thành công trả 204. */
export function resetPassword(body: ResetPasswordRequest): Promise<void> {
  return apiRequest<void>('/api/auth/reset-password', { body, method: 'POST' })
}
