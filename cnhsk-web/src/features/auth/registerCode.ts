import { toApiError } from '@/shared/api'

export type RegisterCodeNotice =
  { kind: 'resent' } | { kind: 'error'; message: string; requestId?: string }

/** Chấp nhận mã dán kèm dấu cách hoặc gạch nối. */
export function normalizeRegisterCode(value: string): string {
  return value.replace(/\D/g, '').slice(0, 6)
}

export function validateRegisterCode(code: string): string | undefined {
  return code.length === 6 ? undefined : 'Vui lòng nhập đủ 6 số.'
}

/** Lỗi gắn vào ô mã khi máy chủ từ chối mã; undefined nếu lỗi thuộc loại khác. */
export function getRegisterCodeError(error: unknown): string | undefined {
  const apiError = toApiError(error)
  if (apiError.status !== 400 || apiError.errorCode !== 'INVALID_VERIFY_TOKEN') return undefined
  const left = apiError.extra.remainingAttempts
  return typeof left === 'number' && left > 0
    ? `Mã chưa đúng. Bạn còn ${left} lần thử.`
    : 'Mã đã hết hiệu lực. Vui lòng gửi lại mã mới.'
}

/** Số giây phải chờ khi máy chủ chặn gửi mã; undefined nếu không phải lỗi này. */
export function getRegisterCodeWait(error: unknown): number | undefined {
  const apiError = toApiError(error)
  if (apiError.status !== 429) return undefined
  const seconds = apiError.extra.retryAfterSeconds
  return typeof seconds === 'number' && seconds > 0 ? Math.ceil(seconds) : 60
}

export function registerCodeWaitMessage(seconds: number): string {
  return `Email này đã yêu cầu quá nhiều mã. Vui lòng thử lại sau ${Math.ceil(seconds / 60)} phút.`
}
