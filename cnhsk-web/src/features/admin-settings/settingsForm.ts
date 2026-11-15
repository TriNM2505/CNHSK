import type { SettingDraft, SettingItem } from './types'

export const formatNumber = (value: number) => value.toLocaleString('vi-VN')
export const formatTime = (value: string) => new Date(value).toLocaleString('vi-VN')

export function validateSetting(
  item: SettingItem,
  draft: SettingDraft,
  catalog: readonly SettingItem[],
): { value?: string; reason?: string } {
  const errors: { value?: string; reason?: string } = {}
  const raw = draft.value.trim()
  const value = draft.mode === 'reset' ? item.defaultValue : Number(raw)
  if (
    (draft.mode === 'edit' && !/^-?\d+$/.test(raw)) ||
    !Number.isSafeInteger(value) ||
    value < item.min ||
    value > item.max
  ) {
    errors.value = `Nhập số nguyên từ ${formatNumber(item.min)} đến ${formatNumber(item.max)}.`
  } else {
    const minKey = 'grading.min-hanzi'
    const maxKey = 'grading.max-hanzi'
    if (item.key === minKey || item.key === maxKey) {
      const min =
        item.key === minKey ? value : catalog.find((row) => row.key === minKey)?.currentValue
      const max =
        item.key === maxKey ? value : catalog.find((row) => row.key === maxKey)?.currentValue
      if (min !== undefined && max !== undefined && min >= max)
        errors.value = 'Số Hán tự tối thiểu phải nhỏ hơn số Hán tự tối đa.'
    }
  }
  if (!draft.reason.trim()) errors.reason = 'Vui lòng nhập lý do.'
  else if (Array.from(draft.reason).length > 500) errors.reason = 'Lý do không được quá 500 ký tự.'
  return errors
}
