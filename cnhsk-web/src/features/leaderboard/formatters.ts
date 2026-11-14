export function formatDuration(durationMs: number | null): string {
  if (durationMs === null) return '—'
  const seconds = Math.floor(Math.max(0, durationMs) / 1000)
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

export function formatPeriodRange(start: string | null, end: string | null): string {
  if (start === null || end === null) return 'Toàn bộ thời gian'
  const startDate = new Date(start)
  const endDate = new Date(new Date(end).getTime() - 1)
  const formatter = new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    timeZone: 'Asia/Ho_Chi_Minh',
  })
  const formatDay = (date: Date) => {
    const parts = Object.fromEntries(
      formatter.formatToParts(date).map(({ type, value }) => [type, value]),
    )
    return `${parts.day}/${parts.month}`
  }
  return `${formatDay(startDate)} – ${formatDay(endDate)}`
}
