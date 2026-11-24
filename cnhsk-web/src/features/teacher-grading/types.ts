import type {
  Correction,
  Criteria,
  GradingMode,
  GradingResult,
  RequestStatus,
  RubricCode,
} from '@/features/grading/types'

export type { Correction, Criteria, GradingResult }
export type QueueItem = {
  id: number
  reference: string
  hanziCount: number
  hskLevel: number | null
  sentAt: string
  claimDeadline: string
  payoutCredits: number
  mode?: GradingMode
}
export type QueuePage = { items: QueueItem[]; page: number; size: number; totalElements: number }
export type AssignedItem = {
  id: number
  reference: string
  hanziCount: number
  assignedAt: string
  gradingDeadline: string
  mode?: GradingMode
}
export type TeacherGradingDetail = {
  id: number
  reference: string
  status: RequestStatus
  essay: string
  prompt: string | null
  hskLevel: number | null
  hanziCount: number
  assignedAt: string
  gradingDeadline: string
  payoutCredits: number
  result: GradingResult | null
  mode?: GradingMode
  /** The learner's recording when `mode` is SPEAKING. */
  audioUrl?: string | null
}
/** New grades always use the rubric of the work's kind; legacy four-mark grades are only read. */
export type GradingResultWrite = {
  rubric: RubricCode
  scores: Record<string, number>
  errors: string[]
  feedback: string
  corrections: Correction[]
}
export type ResultDraft = {
  scores: Record<string, string>
  errors: string[]
  feedback: string
  corrections: { original: string; corrected: string; note: string }[]
}
/** An error code of appendix D; `kid` is null for communicative codes, which land on the task's node. */
export type ErrorCodeOption = {
  code: string
  kid: string | null
  severity: number
  nameVi: string
  example: string | null
  fixHint: string | null
}
