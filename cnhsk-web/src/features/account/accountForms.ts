import { validateNewPassword } from '@/features/auth/passwordValidation'
import { toApiError } from '@/shared/api'

import type { ChangePasswordRequest, Profile, ProfilePatch } from './types'

/** Giá trị trên form — ô trống dùng chuỗi rỗng, chuyển sang null khi gửi. */
export interface ProfileFormValues {
  displayName: string
  avatarUrl: string
  hskLevel: string
}

export type ProfileFieldErrors = Partial<Record<keyof ProfileFormValues, string>>
export type PasswordFieldErrors = Partial<Record<keyof ChangePasswordRequest, string>>

export interface FormFailure<Errors> {
  fieldErrors: Errors
  message?: string
  requestId?: string
}

const PROFILE_FIELDS = new Set<keyof ProfileFormValues>(['displayName', 'avatarUrl', 'hskLevel'])
const PASSWORD_FIELDS = new Set<keyof ChangePasswordRequest>([
  'currentPassword',
  'newPassword',
  'confirmPassword',
])

export function profileToForm(profile: Profile): ProfileFormValues {
  return {
    avatarUrl: profile.avatarUrl ?? '',
    displayName: profile.displayName,
    hskLevel: profile.hskLevel ? String(profile.hskLevel) : '',
  }
}

export function formToProfilePatch(values: ProfileFormValues): ProfilePatch {
  const avatarUrl = values.avatarUrl.trim()
  return {
    avatarUrl: avatarUrl || null,
    displayName: values.displayName.trim(),
    hskLevel: values.hskLevel ? Number(values.hskLevel) : null,
  }
}

/** FR-095: tên 2–100 ký tự; ảnh là URL https:// tối đa 500 ký tự hoặc để trống; HSK 1–9 hoặc chưa chọn. */
export function validateProfile(values: ProfileFormValues): ProfileFieldErrors {
  const errors: ProfileFieldErrors = {}
  const displayName = values.displayName.trim()
  if (displayName.length < 2 || displayName.length > 100) {
    errors.displayName = 'Tên hiển thị cần từ 2–100 ký tự.'
  }

  const avatarUrl = values.avatarUrl.trim()
  if (avatarUrl) {
    if (avatarUrl.length > 500) {
      errors.avatarUrl = 'Đường dẫn ảnh không được vượt quá 500 ký tự.'
    } else if (!/^https:\/\/\S+$/.test(avatarUrl)) {
      errors.avatarUrl = 'Đường dẫn ảnh phải bắt đầu bằng https:// và không có khoảng trắng.'
    }
  }

  if (values.hskLevel) {
    const level = Number(values.hskLevel)
    if (!Number.isInteger(level) || level < 1 || level > 9) {
      errors.hskLevel = 'Cấp HSK phải từ 1 đến 9.'
    }
  }

  return errors
}

export function validatePasswordChange(
  values: ChangePasswordRequest,
  email: string,
): PasswordFieldErrors {
  const errors: PasswordFieldErrors = {}
  if (!values.currentPassword) errors.currentPassword = 'Vui lòng nhập mật khẩu hiện tại.'
  const newPasswordError = validateNewPassword(values.newPassword, email)
  if (newPasswordError) {
    errors.newPassword = newPasswordError
  } else if (values.newPassword === values.currentPassword) {
    errors.newPassword = 'Mật khẩu mới phải khác mật khẩu hiện tại.'
  }
  if (values.confirmPassword !== values.newPassword) {
    errors.confirmPassword = 'Mật khẩu nhập lại chưa khớp.'
  }
  return errors
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function detailErrors<Field extends string>(
  details: unknown,
  allowed: ReadonlySet<Field>,
): Partial<Record<Field, string>> {
  const errors: Partial<Record<Field, string>> = {}
  if (!Array.isArray(details)) return errors
  for (const detail of details) {
    if (
      isRecord(detail) &&
      typeof detail.field === 'string' &&
      typeof detail.message === 'string' &&
      allowed.has(detail.field as Field)
    ) {
      errors[detail.field as Field] = detail.message
    }
  }
  return errors
}

function genericMessage(status: number, fallback: string): string {
  return status === 0
    ? 'Không thể kết nối tới máy chủ. Vui lòng kiểm tra kết nối và thử lại.'
    : fallback
}

export function getProfileFailure(error: unknown): FormFailure<ProfileFieldErrors> {
  const apiError = toApiError(error)
  if (apiError.status === 422) {
    return {
      fieldErrors: detailErrors(apiError.details, PROFILE_FIELDS),
      message: 'Vui lòng kiểm tra lại các trường được đánh dấu.',
    }
  }
  return {
    fieldErrors: {},
    message: genericMessage(apiError.status, 'Chưa lưu được hồ sơ. Vui lòng thử lại.'),
    requestId: apiError.requestId,
  }
}

export function getPasswordChangeFailure(error: unknown): FormFailure<PasswordFieldErrors> {
  const apiError = toApiError(error)
  if (apiError.status === 422 && apiError.errorCode === 'CURRENT_PASSWORD_INCORRECT') {
    return { fieldErrors: { currentPassword: 'Mật khẩu hiện tại không đúng.' } }
  }
  if (apiError.status === 422 && apiError.errorCode === 'PASSWORD_UNCHANGED') {
    return { fieldErrors: { newPassword: 'Mật khẩu mới phải khác mật khẩu hiện tại.' } }
  }
  if (apiError.status === 422) {
    return {
      fieldErrors: detailErrors(apiError.details, PASSWORD_FIELDS),
      message: 'Vui lòng kiểm tra lại các trường được đánh dấu.',
    }
  }
  return {
    fieldErrors: {},
    message: genericMessage(apiError.status, 'Chưa đổi được mật khẩu. Vui lòng thử lại.'),
    requestId: apiError.requestId,
  }
}
