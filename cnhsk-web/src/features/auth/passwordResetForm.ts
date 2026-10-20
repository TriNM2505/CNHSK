import { toApiError } from '@/shared/api'

import { validateNewPassword } from './passwordValidation'

export interface ResetPasswordValues {
  newPassword: string
  confirmPassword: string
}

export type ResetPasswordFieldErrors = Partial<Record<keyof ResetPasswordValues, string>>

export type ResetPasswordNotice =
  | { kind: 'validation' }
  | { kind: 'invalid-token' }
  | { kind: 'generic'; message: string; requestId?: string }

export interface ResetPasswordFailure {
  fieldErrors: ResetPasswordFieldErrors
  notice?: ResetPasswordNotice
}

export const PASSWORD_UNCHANGED_MESSAGE = 'Mật khẩu mới phải khác mật khẩu hiện tại.'

/** Thông báo chung khi gửi yêu cầu quên mật khẩu lỗi mạng/hệ thống (không lộ email tồn tại). */
export function forgotPasswordErrorMessage(error: unknown): {
  message: string
  requestId?: string
} {
  const apiError = toApiError(error)
  return {
    message:
      apiError.status === 0
        ? 'Không thể kết nối tới máy chủ. Vui lòng kiểm tra kết nối và thử lại.'
        : 'Chưa gửi được yêu cầu lúc này. Vui lòng thử lại.',
    requestId: apiError.requestId,
  }
}

/** Đặt lại mật khẩu không biết email nên không kiểm luật "khác email" ở client (server kiểm). */
export function validateResetPassword(values: ResetPasswordValues): ResetPasswordFieldErrors {
  const errors: ResetPasswordFieldErrors = {}
  const passwordError = validateNewPassword(values.newPassword, '')
  if (passwordError) errors.newPassword = passwordError
  if (values.confirmPassword !== values.newPassword) {
    errors.confirmPassword = 'Mật khẩu nhập lại chưa khớp.'
  }
  return errors
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

const RESET_FIELDS = new Set(['newPassword', 'confirmPassword'])

export function getResetPasswordFailure(error: unknown): ResetPasswordFailure {
  const apiError = toApiError(error)

  if (apiError.status === 400 && apiError.errorCode === 'INVALID_RESET_TOKEN') {
    return { fieldErrors: {}, notice: { kind: 'invalid-token' } }
  }

  if (apiError.status === 422 && apiError.errorCode === 'PASSWORD_UNCHANGED') {
    return { fieldErrors: { newPassword: PASSWORD_UNCHANGED_MESSAGE } }
  }

  if (apiError.status === 422) {
    const fieldErrors: ResetPasswordFieldErrors = {}
    if (Array.isArray(apiError.details)) {
      for (const detail of apiError.details) {
        if (
          isRecord(detail) &&
          typeof detail.field === 'string' &&
          typeof detail.message === 'string' &&
          RESET_FIELDS.has(detail.field)
        ) {
          fieldErrors[detail.field as keyof ResetPasswordValues] = detail.message
        }
      }
    }
    return { fieldErrors, notice: { kind: 'validation' } }
  }

  return {
    fieldErrors: {},
    notice: {
      kind: 'generic',
      message:
        apiError.status === 0
          ? 'Không thể kết nối tới máy chủ. Vui lòng kiểm tra kết nối và thử lại.'
          : 'Chưa đặt lại được mật khẩu. Vui lòng thử lại.',
      requestId: apiError.requestId,
    },
  }
}
