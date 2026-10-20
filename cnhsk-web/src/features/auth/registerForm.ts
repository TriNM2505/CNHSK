import { toApiError } from '@/shared/api'

import { normalizeEmail, validateEmail } from './emailValidation'
import { validateNewPassword } from './passwordValidation'
import type { RegisterRequest } from './types'

export type RegisterFieldErrors = Partial<Record<keyof RegisterRequest, string>>

export type RegisterNotice =
  | { kind: 'email-exists' }
  | { kind: 'validation' }
  | { kind: 'generic'; message: string; requestId?: string }

export interface RegisterFailure {
  fieldErrors: RegisterFieldErrors
  notice: RegisterNotice
}

const REGISTER_FIELDS = new Set<keyof RegisterRequest>([
  'acceptTerms',
  'confirmPassword',
  'displayName',
  'email',
  'password',
])

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

export function normalizeRegisterRequest(values: RegisterRequest): RegisterRequest {
  return {
    acceptTerms: values.acceptTerms,
    confirmPassword: values.confirmPassword,
    displayName: values.displayName.trim(),
    email: normalizeEmail(values.email),
    password: values.password,
  }
}

export function validateRegister(values: RegisterRequest): RegisterFieldErrors {
  const errors: RegisterFieldErrors = {}
  const displayName = values.displayName.trim()
  const email = normalizeEmail(values.email)

  if (displayName.length < 2 || displayName.length > 100) {
    errors.displayName = 'Tên hiển thị cần từ 2–100 ký tự.'
  }

  const emailError = validateEmail(values.email)
  if (emailError) errors.email = emailError

  const passwordError = validateNewPassword(values.password, email)
  if (passwordError) errors.password = passwordError

  if (values.confirmPassword !== values.password) {
    errors.confirmPassword = 'Mật khẩu nhập lại chưa khớp.'
  }

  if (!values.acceptTerms) {
    errors.acceptTerms = 'Bạn cần đồng ý với điều khoản để đăng ký.'
  }

  return errors
}

function fieldErrorsFromDetails(details: unknown): RegisterFieldErrors {
  if (!Array.isArray(details)) return {}

  return details.reduce<RegisterFieldErrors>((errors, detail) => {
    if (
      !isRecord(detail) ||
      typeof detail.field !== 'string' ||
      typeof detail.message !== 'string' ||
      !REGISTER_FIELDS.has(detail.field as keyof RegisterRequest)
    ) {
      return errors
    }

    errors[detail.field as keyof RegisterRequest] = detail.message
    return errors
  }, {})
}

export function getRegisterFailure(error: unknown): RegisterFailure {
  const apiError = toApiError(error)

  if (apiError.status === 409 && apiError.errorCode === 'EMAIL_ALREADY_EXISTS') {
    return {
      fieldErrors: { email: 'Email này đã được sử dụng.' },
      notice: { kind: 'email-exists' },
    }
  }

  if (apiError.status === 422) {
    return {
      fieldErrors: fieldErrorsFromDetails(apiError.details),
      notice: { kind: 'validation' },
    }
  }

  return {
    fieldErrors: {},
    notice: {
      kind: 'generic',
      message:
        apiError.status === 0
          ? 'Không thể kết nối tới máy chủ. Vui lòng kiểm tra kết nối và thử lại.'
          : 'Không thể đăng ký lúc này. Vui lòng thử lại.',
      requestId: apiError.requestId,
    },
  }
}
