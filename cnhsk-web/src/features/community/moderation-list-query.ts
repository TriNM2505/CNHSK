export const MODERATION_PAGE_SIZE = 20

export function parseModerationPage(value: string | null): number {
  if (value === null || !/^\d+$/.test(value)) return 0
  const page = Number(value)
  return Number.isSafeInteger(page) && page >= 0 ? page : 0
}

export function formatModerationTimestamp(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('vi-VN', {
        dateStyle: 'medium',
        timeStyle: 'short',
        timeZone: 'Asia/Ho_Chi_Minh',
      }).format(date)
}
