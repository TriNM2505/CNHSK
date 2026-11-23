export type LeaderboardPeriod = 'week' | 'month' | 'year' | 'all'

export type LeaderboardEntry = {
  rank: number
  userId: number
  displayName: string
  avatarUrl: string | null
  score: number
  durationMs: number | null
}

export type Leaderboard = {
  type: 'TOPIC' | 'GAME' | 'COMMUNITY'
  ref: string
  period: LeaderboardPeriod
  periodStart: string | null
  periodEnd: string | null
  entries: LeaderboardEntry[]
  myEntry: LeaderboardEntry | null
}

export type TopicOption = { id: number; name: string; hskLevel: number }
export type GameOption = { code: string; name: string }
