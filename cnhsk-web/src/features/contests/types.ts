import type { ItemState, ServedItemView, SessionStatus } from '@/features/practice/types'

export type ScheduleState = 'UPCOMING' | 'ONGOING' | 'ENDED'
export type Prize = { fromRank: number; toRank: number; description: string }

export type ContestSummary = {
  id: number
  name: string
  startsAt: string
  endsAt: string
  state: ScheduleState
  prizes: Prize[]
  maxParticipants: number | null
  participantCount: number
  rankingPublished: boolean
  /** Set for a game contest, which has its own page under /community/contests; null for an exam contest. */
  gameCode?: string | null
}

export type ContestPage = {
  items: ContestSummary[]
  page: number
  size: number
  totalElements: number
}

export type ContestDetail = ContestSummary & {
  description: string | null
  examTitle: string | null
  durationMinutes: number
  questionCount: number
}

export type ContestSession = {
  sessionId: number
  contestId: number
  deadline: string
  serverTime: string
  resumed: boolean
  items: ServedItemView[]
}

export type ContestDraft = {
  sessionId: number
  status: SessionStatus
  answered: ItemState[]
}

export type SubmitResult = { submittedAt: string }
export type RankingRow = {
  rank: number
  userId: number
  displayName: string | null
  score: number
  maxScore: number
  durationMs: number | null
}
export type RankingPage = {
  items: RankingRow[]
  page: number
  size: number
  totalElements: number
}
export type MyResult = {
  status: SessionStatus
  submittedAt: string | null
  score: number | null
  maxScore: number | null
  durationMs: number | null
  rank: number | null
  excluded: boolean | null
}
export type ContestResults = {
  contest: ContestSummary
  published: boolean
  mine: MyResult | null
  ranking: RankingPage | null
}
