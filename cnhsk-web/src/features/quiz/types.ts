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
