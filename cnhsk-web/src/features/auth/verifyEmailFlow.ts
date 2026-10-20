import { toApiError } from '@/shared/api'

/** Kết quả xử lý link xác thực trong email (FR-088). */
export type VerifyLinkStatus = 'verifying' | 'verified' | 'invalid' | 'error'

export type ResendState =
  | { kind: 'idle' }
  | { kind: 'sending' }
  | { kind: 'sent' }
  | { kind: 'cooldown'; retryUntil: number }
  | { kind: 'already-verified' }
  | { kind: 'error'; message: string; requestId?: string }

/** Thời gian chờ mặc định khi 429 không kèm retryAfterSeconds. */
const DEFAULT_RETRY_SECONDS = 60

export interface VerifyLinkFailure {
  status: Extract<VerifyLinkStatus, 'invalid' | 'error'>
  requestId?: string
}

export function getVerifyLinkFailure(error: unknown): VerifyLinkFailure {
  const apiError = toApiError(error)
  if (apiError.status === 400 && apiError.errorCode === 'INVALID_VERIFY_TOKEN') {
    return { status: 'invalid' }
  }
  return { requestId: apiError.requestId, status: 'error' }
}

export function getResendFailure(error: unknown, now: number): ResendState {
  const apiError = toApiError(error)

  if (apiError.status === 429) {
    const retryAfter = Number(apiError.extra.retryAfterSeconds)
    const seconds =
      Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : DEFAULT_RETRY_SECONDS
    return { kind: 'cooldown', retryUntil: now + seconds * 1000 }
  }

  if (apiError.status === 409 && apiError.errorCode === 'EMAIL_ALREADY_VERIFIED') {
    return { kind: 'already-verified' }
  }

  if (apiError.status === 401) {
    return {
      kind: 'error',
      message: 'Phiên đăng nhập đã hết. Vui lòng đăng nhập lại để gửi email.',
    }
  }

  return {
    kind: 'error',
    message:
      apiError.status === 0
        ? 'Không thể kết nối tới máy chủ. Vui lòng kiểm tra kết nối và thử lại.'
        : 'Chưa gửi lại được email. Vui lòng thử lại sau.',
    requestId: apiError.requestId,
  }
}

/** Số giây còn lại (làm tròn lên), không âm. */
export function secondsUntil(retryUntil: number, now: number): number {
  return Math.max(0, Math.ceil((retryUntil - now) / 1000))
}

/** 90 → "1:30". */
export function formatCountdown(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}
