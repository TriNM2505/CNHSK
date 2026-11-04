export type MasteryState =
  'notyet' | 'insufficient' | 'review' | 'consolidate' | 'extend' | 'inferred'
export type EvidenceContext = 'EXAM' | 'QUIZ' | 'GRADED' | 'PRACTICE'
export type EvidenceForm = 'OPEN' | 'MCQ4' | 'MCQ3' | 'BINARY'
export type EvidenceRole = 'PRIMARY' | 'SECONDARY' | 'ERROR'
export type FlashcardRating = 'AGAIN' | 'HARD' | 'GOOD' | 'EASY'

export interface MasteryNode {
  kid: string
  value: number | null
  confidence: number
  evidenceCount: number
  state: MasteryState
  frontier: boolean
  dueAt?: string | null
}

export interface MasteryTree {
  nodes: MasteryNode[]
}

export interface EvidenceView {
  answeredAt: string
  source: string
  context: EvidenceContext
  form: EvidenceForm
  role: EvidenceRole
  score: 0 | 100
  weight: number
}

export interface PrereqState {
  kid: string
  nameVi: string
  state: MasteryState
}

export interface MasteryNodeDetail extends MasteryNode {
  prereqs: PrereqState[]
  evidence: EvidenceView[]
  approvedQuestionCount: number
}

export interface Recommendation {
  kid: string
  nameVi: string
  kind: 'PREREQ' | 'REVIEW' | 'CONSOLIDATE' | 'NEW'
  reason: string
  value?: number | null
}

export interface ReviewSummary {
  dueCount: number
  nextDueAt: string | null
}

export interface ReviewStartRequest {
  ahead?: boolean
}

export interface NothingDue {
  session: null
  reason: 'NOTHING_DUE'
}
