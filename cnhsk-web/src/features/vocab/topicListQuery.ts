import { SUPPORTED_HSK_LEVELS, type HskLevel } from '@/shared/ui'

import type { TopicStatus, TopicSummary } from './types'

export type TopicStatusFilter = TopicStatus | 'ALL'

export const HSK_LEVELS: readonly HskLevel[] = SUPPORTED_HSK_LEVELS
export const TOPIC_STATUSES: readonly TopicStatus[] = ['NOT_STARTED', 'IN_PROGRESS', 'COMPLETED']

export function parseTopicListSearchParams(searchParams: URLSearchParams) {
  const rawLevel = searchParams.get('hskLevel')
  const hskLevel = HSK_LEVELS.find((level) => String(level) === rawLevel)
  const canonical = new URLSearchParams(searchParams)

  if (hskLevel) canonical.set('hskLevel', String(hskLevel))
  else canonical.delete('hskLevel')

  return {
    canonical,
    hskLevel,
    needsCleanup: canonical.toString() !== searchParams.toString(),
  }
}

export function withHskLevel(searchParams: URLSearchParams, hskLevel?: HskLevel) {
  const next = new URLSearchParams(searchParams)
  if (hskLevel) next.set('hskLevel', String(hskLevel))
  else next.delete('hskLevel')
  return next
}

export function normalizeTopicSearch(value: string) {
  return value
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .replace(/[đĐ]/g, 'd')
    .toLocaleLowerCase('vi')
    .trim()
}

export function filterTopics(
  topics: readonly TopicSummary[],
  search: string,
  status: TopicStatusFilter,
) {
  const normalizedSearch = normalizeTopicSearch(search)
  return topics.filter(
    (topic) =>
      (status === 'ALL' || topic.status === status) &&
      (!normalizedSearch || normalizeTopicSearch(topic.name).includes(normalizedSearch)),
  )
}
