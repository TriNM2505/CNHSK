import type { AiUsage, SettingHistoryPage, SettingItem } from './types'

export const settingFixture: SettingItem = {
  key: 'assistant.daily-limit',
  group: 'Trợ lý & AI',
  description: 'Số câu hỏi trợ lý mỗi ngày.',
  type: 'INT',
  currentValue: 20,
  defaultValue: 20,
  min: 0,
  max: 10000,
  overridden: false,
  updatedBy: null,
  updatedAt: null,
}
export const settingsFixtures: SettingItem[] = [
  settingFixture,
  {
    ...settingFixture,
    key: 'ai.monthly-token-budget',
    description: 'Hạn mức token dùng chung mỗi tháng.',
    currentValue: 1000000,
    defaultValue: 1000000,
    max: 100000000,
  },
  {
    ...settingFixture,
    key: 'grading.min-hanzi',
    group: 'Chấm bài',
    description: 'Số Hán tự tối thiểu.',
    currentValue: 100,
    defaultValue: 100,
    min: 1,
    max: 1000,
  },
  {
    ...settingFixture,
    key: 'grading.max-hanzi',
    group: 'Chấm bài',
    description: 'Số Hán tự tối đa.',
    currentValue: 500,
    defaultValue: 500,
    min: 10,
    max: 2000,
  },
  {
    ...settingFixture,
    key: 'ai-generate.max-batches-per-run',
    group: 'AI sinh câu',
    description: 'Số lô tối đa trong mỗi lần chạy.',
    currentValue: 0,
    defaultValue: 1,
    max: 20,
    overridden: true,
    updatedBy: { id: 31, displayName: 'Quản trị nội dung 示例' },
    updatedAt: '2026-11-15T05:00:00Z',
  },
]
export const usageFixture: AiUsage = {
  month: '2026-11',
  used: 123456,
  budget: 1000000,
  available: true,
}
export const historyFixture: SettingHistoryPage = {
  page: 0,
  size: 20,
  totalElements: 2,
  items: [
    {
      oldValue: 25,
      newValue: null,
      reason: 'Về mức chuẩn cho giai đoạn mới.',
      actor: { id: 31, displayName: null },
      at: '2026-11-15T05:00:00Z',
    },
    {
      oldValue: 20,
      newValue: 25,
      reason: 'Điều chỉnh theo nhu cầu học tập.',
      actor: { id: 31, displayName: 'Quản trị nội dung 示例' },
      at: '2026-11-14T05:00:00Z',
    },
  ],
}
