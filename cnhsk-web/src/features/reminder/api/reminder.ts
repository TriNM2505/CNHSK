import { apiRequest } from '@/shared/api'

import type { ReminderSettings, ReminderUpdate, TodayReminder } from '../types'

export function getReminderSettings(signal?: AbortSignal): Promise<ReminderSettings> {
  return apiRequest<ReminderSettings>('/api/me/reminder', { signal })
}

export function updateReminderSettings(body: ReminderUpdate): Promise<ReminderSettings> {
  return apiRequest<ReminderSettings>('/api/me/reminder', { body, method: 'PUT' })
}

export function unsubscribeReminderEmail(token: string): Promise<void> {
  return apiRequest<void>('/api/public/reminder/unsubscribe', {
    body: { token },
    method: 'POST',
  })
}

export function getTodayReminder(signal?: AbortSignal): Promise<TodayReminder> {
  return apiRequest<TodayReminder>('/api/reminders/today', { signal })
}
