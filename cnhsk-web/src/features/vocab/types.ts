import type { HskLevel } from '@/shared/ui'

export type TopicStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED'

export type TopicSummary = {
  bestTestPercent: number | null
  code: string
  hskLevel: HskLevel
  id: number
  learnedWords: number
  name: string
  percent: number
  status: TopicStatus
  totalWords: number
}
