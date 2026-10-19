import { toApiError } from '@/shared/api'

export const BAN_REASON_MIN_LENGTH = 5
export const BAN_REASON_MAX_LENGTH = 500

export type UserDetailAction =
  | { kind: 'grant'; role: import('@/features/auth/types').RoleCode }
  | { kind: 'revoke'; role: import('@/features/auth/types').RoleCode }
  | { kind: 'ban' }
  | { kind: 'unban' }

export interface UserDetailActionError {
  fieldError?: string
  message: string
  reloadDetail?: boolean
  requestId?: string
  userMissing?: boolean
}

export function validateBanReason(reason: string) {
  const value = reason.trim()

  if (value.length < BAN_REASON_MIN_LENGTH) {
    return {
      error: `Lý do phải có ít nhất ${BAN_REASON_MIN_LENGTH} ký tự sau khi bỏ khoảng trắng.`,
      value,
    }
  }

  if (value.length > BAN_REASON_MAX_LENGTH) {
    return {
      error: `Lý do không được vượt quá ${BAN_REASON_MAX_LENGTH} ký tự.`,
      value,
    }
  }

  return { value }
}

function reasonFieldError(details: unknown) {
  if (!details || typeof details !== 'object') return undefined
  const reason = (details as Record<string, unknown>).reason
  if (typeof reason === 'string') return reason
  if (Array.isArray(reason) && typeof reason[0] === 'string') return reason[0]
  return undefined
}

export function mapUserDetailActionError(error: unknown): UserDetailActionError {
  const apiError = toApiError(error)
  const base = { requestId: apiError.requestId }

  switch (apiError.errorCode) {
    case 'SELF_ROLE_GRANT':
      return { ...base, message: 'Bạn không thể tự cấp vai trò cho chính mình.' }
    case 'SELF_ROLE_REVOKE':
      return { ...base, message: 'Bạn không thể tự thu hồi vai trò ADMIN của chính mình.' }
    case 'SELF_BAN':
      return { ...base, message: 'Bạn không thể cấm tài khoản của chính mình.' }
    case 'ROLE_NOT_GRANTED':
      return {
        ...base,
        message: 'Vai trò này không còn được cấp. Danh sách vai trò đã được tải lại.',
        reloadDetail: true,
      }
    case 'ROLE_ALREADY_GRANTED':
      return {
        ...base,
        message: 'Vai trò này đã được cấp. Danh sách vai trò đã được tải lại.',
        reloadDetail: true,
      }
    case 'REVOKE_LAST_ADMIN':
    case 'BAN_LAST_ADMIN':
      return { ...base, message: 'Đây là quản trị viên cuối cùng còn hoạt động.' }
    case 'USER_NOT_FOUND':
      return { ...base, message: 'Không tìm thấy tài khoản.', userMissing: true }
    default: {
      const fieldError = apiError.status === 422 ? reasonFieldError(apiError.details) : undefined
      return {
        ...base,
        fieldError,
        message:
          fieldError ??
          (apiError.status === 422
            ? 'Lý do cấm chưa hợp lệ. Vui lòng kiểm tra lại.'
            : apiError.message),
      }
    }
  }
}

export function getActionSuccessMessage(action: UserDetailAction) {
  switch (action.kind) {
    case 'grant':
      return 'Đã cấp vai trò cho tài khoản.'
    case 'revoke':
      return 'Đã thu hồi vai trò khỏi tài khoản.'
    case 'ban':
      return 'Đã cấm tài khoản và đăng xuất tài khoản khỏi mọi thiết bị.'
    case 'unban':
      return 'Đã gỡ cấm tài khoản.'
  }
}
