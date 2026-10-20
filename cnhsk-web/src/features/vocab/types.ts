import type { HskLevel } from '@/shared/ui'
import type { SessionView } from '@/features/practice'

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

export type TopicWord = {
  audioUrl: string | null
  hskLevel: HskLevel | null
  learned: boolean
  lexemeId: number
  meaning: string
  pinyin: string | null
  sinoViet: string | null
  text: string
}

export type TopicTestBlockedReason = 'NOT_ENOUGH_LEARNED' | 'TOO_FEW_WORDS' | 'RETRY_TOO_SOON'

export type TopicActions = {
  canPracticeListening: boolean
  canPracticeRecognition: boolean
  canStudy: boolean
  canTakeTest: boolean
  requiredWords?: number
  retryAvailableAt?: string | null
  testBlockedReason?: TopicTestBlockedReason | null
}

export type TopicDetail = TopicSummary & {
  actions: TopicActions
  words: TopicWord[]
}

export type StartTopicStudyResult =
  | SessionView
  | {
      reason: 'NO_NEW_WORDS'
      session: null
    }

export type TopicPracticeMode = 'RECOGNITION' | 'LISTENING'

export type StartTopicPracticeRequest = {
  mode: TopicPracticeMode
  focusLexemeIds?: number[]
}

export type TopicWrongWord = {
  lexemeId: number
  meaning: string
  pinyin?: string | null
  text: string
}

/** Khớp schema TopicTestExtra — phần `extra` của kết quả nộp bài kiểm tra (FR-039). */
export type TopicTestExtra = {
  bestPercent?: number
  firstCompletion?: boolean
  passed?: boolean
  topicCompleted?: boolean
  wrongWords?: TopicWrongWord[]
}
