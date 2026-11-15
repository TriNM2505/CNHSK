import { apiRequest } from '@/shared/api'

import type { AiUsage, SettingHistoryPage, SettingItem } from '../types'

const settingPath = (key: string) => `/api/admin/settings/${encodeURIComponent(key)}`

export function listSettings(signal?: AbortSignal) {
  return apiRequest<SettingItem[]>('/api/admin/settings', { signal })
}
export function updateSetting(key: string, value: number, reason: string) {
  return apiRequest<SettingItem>(settingPath(key), { method: 'PUT', body: { value, reason } })
}
export function resetSetting(key: string, reason: string) {
  return apiRequest<SettingItem>(settingPath(key), { method: 'DELETE', body: { reason } })
}
export function getSettingHistory(key: string, page = 0, signal?: AbortSignal) {
  const query = new URLSearchParams({ page: String(page), size: '20' })
  return apiRequest<SettingHistoryPage>(`${settingPath(key)}/history?${query}`, { signal })
}
export function getAiUsage(signal?: AbortSignal) {
  return apiRequest<AiUsage>('/api/admin/ai-usage', { signal })
}
