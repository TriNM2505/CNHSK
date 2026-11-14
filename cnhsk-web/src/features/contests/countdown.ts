export type CountdownAnchor = { remainingAtReceipt: number; receivedAt: number }

export function createCountdown(
  deadline: string,
  serverTime: string,
  receivedAt = performance.now(),
): CountdownAnchor {
  const remainingAtReceipt = Date.parse(deadline) - Date.parse(serverTime)
  if (!Number.isFinite(remainingAtReceipt)) throw new Error('Invalid contest deadline')
  return { remainingAtReceipt, receivedAt }
}

export function remainingMilliseconds(anchor: CountdownAnchor, now = performance.now()): number {
  return Math.max(0, anchor.remainingAtReceipt - Math.max(0, now - anchor.receivedAt))
}

export function formatCountdown(milliseconds: number): string {
  const total = Math.ceil(Math.max(0, milliseconds) / 1000)
  const minutes = Math.floor(total / 60)
  const seconds = String(total % 60).padStart(2, '0')
  return String(minutes).padStart(2, '0') + ':' + seconds
}

export function formatVietnamDate(value: string): string {
  return new Intl.DateTimeFormat('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}
