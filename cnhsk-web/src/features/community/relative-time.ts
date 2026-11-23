const RELATIVE = new Intl.RelativeTimeFormat('vi', { numeric: 'auto' })

/** "3 giờ trước" for recent moments, a plain date after a week. */
export function formatRelative(value: string, now = Date.now()): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const minutes = Math.round((date.getTime() - now) / 60_000)
  if (Math.abs(minutes) < 60) return RELATIVE.format(Math.min(minutes, 0), 'minute')
  const hours = Math.round(minutes / 60)
  if (Math.abs(hours) < 24) return RELATIVE.format(hours, 'hour')
  const days = Math.round(hours / 24)
  if (Math.abs(days) < 7) return RELATIVE.format(days, 'day')
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeZone: 'Asia/Ho_Chi_Minh' }).format(date)
}
