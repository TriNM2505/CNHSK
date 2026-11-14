import type { HskLevel } from '@/shared/ui'

export type FirstAttemptSummary = {
  score: number
  submittedAt: string
  suspicious: boolean
}

export type QuizListing = {
  topicId: number
  topicName: string
  hskLevel: HskLevel
  questionCount: number
  availableQuestions: number
  myFirstAttempt: FirstAttemptSummary | null
  myBestRetryScore: number | null
}

export type QuizListingPage = {
  items: QuizListing[]
  page: number
  size: number
  total: number
}

export type QuizOption = { key: string; text: string }
export type QuizItem = {
  itemId: number
  prompt: string
  options: QuizOption[]
  audioUrl?: string | null
}

export type QuizSession = {
  sessionId: number
  topicId: number
  rankingEligible: boolean
  expiresAt: string
  items: QuizItem[]
}

export type QuizListParams = { hsk?: HskLevel; page?: number; size?: number }

export type QuizResultItem = {
  itemId: number
  prompt: string
  options: QuizOption[]
  chosen: string | null
  correct: string
  explanation: string | null
}

export type FirstAttemptRank = {
  score: number
  durationMs: number | null
  rank: number | null
  total: number | null
}

export type QuizResult = {
  sessionId: number
  topicId: number
  topicName: string
  score: number
  correctCount: number
  wrongCount: number
  durationMs: number
  rankingEligible: boolean
  suspicious: boolean
  items: QuizResultItem[]
  firstAttempt: FirstAttemptRank | null
}

export type TopicRankEntry = {
  rank: number
  userId: number
  displayName: string
  avatarUrl: string | null
  score: number
  durationMs: number | null
}

export type TopicLeaderboard = {
  type: 'TOPIC'
  ref: string
  period: 'all'
  periodStart: string | null
  periodEnd: string | null
  entries: TopicRankEntry[]
  myEntry: TopicRankEntry | null
}

export function quizDuration(milliseconds: number) {
  const seconds = Math.max(0, Math.floor(milliseconds / 1000))
  return `${Math.floor(seconds / 60)} phút ${seconds % 60} giây`
}
