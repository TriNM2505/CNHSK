import type { SessionView } from '@/features/practice'
import type { HskLevel } from '@/shared/ui'

export type Domain = 'NA' | 'CH' | 'TV' | 'NP' | 'NL' | 'VB'
export type QuestionType = 'MULTIPLE_CHOICE' | 'TRUE_FALSE'
export type PlacementLevel = 'BELOW_1' | 'HSK1' | 'HSK2' | 'HSK3' | 'ABOVE_3'
export type PlacementConfidence = 'SURE' | 'TENTATIVE'

export type Score = { maxScore: number; percent: number; score: number }

export type ExamSection = { index: number; name: string; questionCount: number }

export type ExamSummary = {
  activeSessionId: number | null
  code: string
  hskLevel: HskLevel
  id: number
  myAttempts: number
  myBest: Score | null
  questionCount: number
  sections: ExamSection[]
  title: string
}

export type SectionScore = { index: number; maxScore: number; name: string; score: number }
export type ResultOption = { key: string; text: string }

export type ResultItem = {
  correctKey: string
  errorKid: string | null
  errorNote: string | null
  explanation: string | null
  no: number
  options: ResultOption[]
  primaryKid: string | null
  primaryKidName: string | null
  result: 'CORRECT' | 'WRONG' | 'BLANK'
  sectionIndex: number
  stem: string
  yourAnswer: string | null
}

export type KidCount = { kid: string; nameVi: string; total: number; wrong: number }
export type PreviousAttempt = Score & { submittedAt: string }

export type ExamResult = {
  bySection: SectionScore[]
  correctCount: number
  exam: Pick<ExamSummary, 'hskLevel' | 'id' | 'title'>
  items: ResultItem[]
  maxScore: number
  percent: number
  previous: PreviousAttempt | null
  score: number
  sessionId: number
  submittedAt: string
  totalCount: number
  wrongByKid: KidCount[]
}

export type WeakPoint = { kid: string; mastery: number; nameVi: string; state: string | null }

export type TargetedPracticeRequest = {
  excludeQuestionId?: number | null
  kid: string
  qtype?: QuestionType
}

export type NoQuestions = { reason: 'NO_QUESTIONS'; session: null }

export type PlacementSummary = {
  active: { runId: number; sessionId: number } | null
  last: {
    completedAt: string
    confidence: PlacementConfidence
    level: PlacementLevel
    runId: number
  } | null
}

export type PlacementStartRequest = { startLevel?: 1 | 2 | 3 }
export type PlacementStep = { concluded?: boolean; runId: number; session: SessionView | null }
export type LevelScore = { correct: number; level: 1 | 2 | 3; percent: number; total: number }

export type DomainScore = {
  correct: number
  domain: Domain
  measured: boolean
  percent: number | null
  tentative: boolean
  total: number
}

export type PlacementResult = {
  byLevel: LevelScore[]
  completedAt: string
  confidence: PlacementConfidence
  domains: DomainScore[]
  itemCount: number
  level: PlacementLevel
  runId: number
  wrongKids: KidCount[]
}
