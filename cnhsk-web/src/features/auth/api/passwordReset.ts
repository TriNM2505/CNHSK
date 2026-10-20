import { apiRequest } from '@/shared/api'

import type { EmailRequest, ResetPasswordRequest } from '../types'

/** FR-038: luôn trả 202 không có nội dung, dù email có tồn tại hay không. */
export function requestPasswordReset(email: string): Promise<void> {
  const body: EmailRequest = { email }
  return apiRequest<void>('/api/auth/forgot-password', { body, method: 'POST' })
}

/** FR-042: thành công trả 204. */
export function resetPassword(body: ResetPasswordRequest): Promise<void> {
  return apiRequest<void>('/api/auth/reset-password', { body, method: 'POST' })
}
