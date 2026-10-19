import { toApiError } from '@/shared/api'

import { normalizeEmail, validateEmail } from './emailValidation'
import type { LoginRequest } from './types'

export type SignInFieldErrors = Partial<Record<keyof LoginRequest, string>>

export type SignInNotice =
  | { kind: 'password-reset' }
  | { kind: 'invalid-credentials' }
  | { blockedUntil: string; kind: 'blocked' }
  | { banReason?: string; kind: 'banned' }
  | { kind: 'suspended'; suspendedUntil?: string }
  | { kind: 'validation' }
  | { kind: 'generic'; message: string; requestId?: string }

/** SCR-006 đặt cờ này khi chuyển về /login sau khi đặt lại mật khẩu thành công (FR-042). */
export interface PasswordResetNavigationState {
  passwordReset?: boolean
}

export interface SignInFailure {
  fieldErrors: SignInFieldErrors
  notice: SignInNotice
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function optionalString(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined
}

export function normalizeLoginRequest(values: LoginRequest): LoginRequest {
  return {
    email: normalizeEmail(values.email),
    password: values.password,
  }
}

export function validateLogin(values: LoginRequest): SignInFieldErrors {
  const errors: SignInFieldErrors = {}
  const emailError = validateEmail(values.email)
  if (emailError) errors.email = emailError

  if (!values.password) {
    errors.password = 'Vui lòng nhập mật khẩu.'
  } else if (values.password.length > 72) {
    errors.password = 'Mật khẩu không được vượt quá 72 ký tự.'
  }

  return errors
}

function fieldErrorsFromDetails(details: unknown): SignInFieldErrors {
  if (!Array.isArray(details)) return {}

  return details.reduce<SignInFieldErrors>((errors, detail) => {
    if (!isRecord(detail) || typeof detail.message !== 'string') return errors
    if (detail.field === 'email' || detail.field === 'password') {
      errors[detail.field] = detail.message
    }
    return errors
  }, {})
}

export function getSignInFailure(error: unknown): SignInFailure {
  const apiError = toApiError(error)

  if (apiError.status === 401) {
    return { fieldErrors: {}, notice: { kind: 'invalid-credentials' } }
  }

  if (apiError.status === 423 && apiError.errorCode === 'LOGIN_SOURCE_BLOCKED') {
    return {
      fieldErrors: {},
      notice: {
        blockedUntil: optionalString(apiError.extra.blockedUntil) ?? '',
        kind: 'blocked',
      },
    }
  }

  if (apiError.status === 403 && apiError.errorCode === 'ACCOUNT_BANNED') {
    return {
      fieldErrors: {},
      notice: { banReason: optionalString(apiError.extra.banReason), kind: 'banned' },
    }
  }

  if (apiError.status === 403 && apiError.errorCode === 'ACCOUNT_SUSPENDED') {
    return {
      fieldErrors: {},
      notice: {
        kind: 'suspended',
        suspendedUntil: optionalString(apiError.extra.suspendedUntil),
      },
    }
  }

  if (apiError.status === 422) {
    return {
      fieldErrors: fieldErrorsFromDetails(apiError.details),
      notice: { kind: 'validation' },
    }
  }

  if (apiError.status === 429) {
    return {
      fieldErrors: {},
      notice: {
        kind: 'generic',
        message: 'Bạn đã thử đăng nhập quá nhiều lần. Vui lòng chờ rồi thử lại.',
        requestId: apiError.requestId,
      },
    }
  }

  const message =
    apiError.status >= 500 ? 'Có lỗi xảy ra khi đăng nhập. Vui lòng thử lại.' : apiError.message

  return {
    fieldErrors: {},
    notice: { kind: 'generic', message, requestId: apiError.requestId },
  }
}
