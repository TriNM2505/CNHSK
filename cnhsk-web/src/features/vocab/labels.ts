import type { StatusTone } from '@/shared/ui'

import type { TopicStatus } from './types'

export const TOPIC_STATUS_LABELS: Record<TopicStatus, string> = {
  LOCKED: 'Đang khoá',
  NOT_STARTED: 'Chưa học',
  IN_PROGRESS: 'Đang học',
  COMPLETED: 'Hoàn thành',
}

export const TOPIC_STATUS_TONES: Record<TopicStatus, StatusTone> = {
  LOCKED: 'warning',
  NOT_STARTED: 'neutral',
  IN_PROGRESS: 'info',
  COMPLETED: 'success',
}
