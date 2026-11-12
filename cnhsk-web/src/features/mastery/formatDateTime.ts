const formatter = new Intl.DateTimeFormat('vi-VN', {
  dateStyle: 'medium',
  timeStyle: 'short',
  timeZone: 'Asia/Ho_Chi_Minh',
})

export function formatMasteryDateTime(value: string): string {
  return formatter.format(new Date(value))
}
