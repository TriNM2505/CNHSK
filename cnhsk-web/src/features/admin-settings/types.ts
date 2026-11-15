export type SettingActor = { id: number; displayName: string | null }

export type SettingItem = {
  key: string
  group: string
  description: string
  type: 'INT'
  currentValue: number
  defaultValue: number
  min: number
  max: number
  overridden: boolean
  updatedBy: SettingActor | null
  updatedAt: string | null
}

export type SettingHistoryEntry = {
  oldValue: number | null
  newValue: number | null
  reason: string
  actor: SettingActor
  at: string
}
export type SettingHistoryPage = {
  items: SettingHistoryEntry[]
  page: number
  size: number
  totalElements: number
}

export type AiUsage = {
  month: string
  used: number | null
  budget: number
  available: boolean
}
export type SettingDraft = {
  key: string
  mode: 'edit' | 'reset'
  value: string
  reason: string
  confirming: boolean
}
