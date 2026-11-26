/** Short label for the HSK range a reference recommends; null on either end means open-ended. */
export function hskRangeLabel(min: number | null, max: number | null): string {
  if (min === null && max === null) return 'Mọi cấp HSK'
  if (min === null) return `Đến HSK ${max}`
  if (max === null) return `Từ HSK ${min}`
  return min === max ? `HSK ${min}` : `HSK ${min}–${max}`
}
