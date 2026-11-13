import type { ReminderSettings, TodayReminder } from './types'
import type { AuthContextValue } from '@/shared/auth'

export const reminderAuth: AuthContextValue = {
  status: 'authenticated',
  user: {
    id: 7,
    displayName: 'Người học thử',
    email: 'reminder@example.test',
    emailVerified: false,
    roles: ['LEARNER'],
  },
  error: null,
  reloadCurrentUser: async () => undefined,
  setAuthenticatedUser: () => undefined,
  signOut: async () => undefined,
}

export const defaultReminderSettings: ReminderSettings = {
  reminderTime: '20:00',
  channels: { web: true, email: false },
  emailVerified: false,
  configured: false,
  timezone: 'Asia/Ho_Chi_Minh',
}

export const todayReminderFixture: TodayReminder = {
  show: true,
  dueCount: 12,
  reminderTime: '20:00',
}
