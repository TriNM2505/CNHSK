import type { GameOption, LeaderboardPeriod, TopicOption } from './types'

export const LEADERBOARD_PERIODS: readonly { value: LeaderboardPeriod; label: string }[] = [
  { value: 'week', label: 'Tuần này' },
  { value: 'month', label: 'Tháng này' },
  { value: 'all', label: 'Tất cả thời gian' },
]

export function parsePeriod(value: string | null): LeaderboardPeriod {
  return value === 'month' || value === 'all' ? value : 'week'
}

export function parseHsk(value: string | null): number | undefined {
  if (value === null || !/^[1-9]$/.test(value)) return undefined
  return Number(value)
}

export function parseTopicId(value: string | null): number | undefined {
  if (!value || !/^\d+$/.test(value)) return undefined
  const id = Number(value)
  return Number.isSafeInteger(id) && id > 0 ? id : undefined
}

export function firstTopic(topics: readonly TopicOption[]): TopicOption | undefined {
  return topics[0]
}

export function firstGame(games: readonly GameOption[]): GameOption | undefined {
  return games[0]
}
