export type ReminderSettings = {
  reminderTime: string
  channels: { web: boolean; email: boolean }
  emailVerified: boolean
  configured: boolean
  timezone: 'Asia/Ho_Chi_Minh'
}

export type ReminderUpdate = {
  reminderTime: string
  web: boolean
  email: boolean
}

export type TodayReminder = {
  show: boolean
  dueCount: number
  reminderTime: string
}
