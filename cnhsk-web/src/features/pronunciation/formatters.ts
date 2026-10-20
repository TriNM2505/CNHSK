const percentageFormatter = new Intl.NumberFormat('vi-VN', {
  maximumFractionDigits: 2,
})

export function formatPercentage(value: number | null | undefined) {
  return value === null || value === undefined ? 'Chưa có' : `${percentageFormatter.format(value)}%`
}
