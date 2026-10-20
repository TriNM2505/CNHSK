const adminUserDateFormatter = new Intl.DateTimeFormat('vi-VN', {
  dateStyle: 'short',
  timeZone: 'Asia/Ho_Chi_Minh',
})

export function formatAdminUserDate(value: string): string {
  return adminUserDateFormatter.format(new Date(value))
}
