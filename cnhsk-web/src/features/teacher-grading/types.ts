import type { Correction, Criteria, GradingResult, RequestStatus } from '@/features/grading/types'

export type { Correction, Criteria, GradingResult }
export type QueueItem = {
  id: number
  reference: string
  hanziCount: number
  hskLevel: number | null
  sentAt: string
  claimDeadline: string
  payoutCredits: number
}
export type QueuePage = { items: QueueItem[]; page: number; size: number; totalElements: number }
export type AssignedItem = {
  id: number
  reference: string
  hanziCount: number
  assignedAt: string
  gradingDeadline: string
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
}
export type GradingResultWrite = { criteria: Criteria; feedback: string; corrections: Correction[] }
export type ResultDraft = {
  criteria: Record<keyof Criteria, string>
  feedback: string
  corrections: { original: string; corrected: string; note: string }[]
}
