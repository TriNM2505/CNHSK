import type { HskLevel } from '@/shared/ui'

export type TopicRef = { id: number; name: string; hskLevel: HskLevel }
export type AdminTopic = TopicRef & {
  prereqs: TopicRef[]
  isRoot: boolean
  warnings?: string[]
}
export type AdminTopicPage = {
  items: AdminTopic[]
  page: number
  size: number
  totalElements: number
}
export type TopicFilters = { q: string; hsk?: HskLevel; page: number }
