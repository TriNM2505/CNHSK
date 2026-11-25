export type ReviewQueueParams = {
  kid?: string
  hsk?: number
  qtype?: ReviewQuestionType
  from?: string
  to?: string
  page?: number
  size?: number
}

export type ReviewQuestionType = 'MULTIPLE_CHOICE' | 'TRUE_FALSE'

export type AIQuestionSummary = {
  id: number
  stemSummary: string
  qtype?: ReviewQuestionType
  hskLevel: number
  labels: { kid: string; role: 'PRIMARY' | 'SECONDARY' }[]
  generatedAt: string
  runId: number | null
  viewedByMe: boolean
  /** TEACHER: waiting for a teacher to check content; CONTENT: checked, waiting for a content editor to publish. */
  step?: ReviewStep
}

export type ReviewStep = 'TEACHER' | 'CONTENT'

export type AIQuestionPage = {
  items: AIQuestionSummary[]
  page: number
  size: number
  totalElements: number
}

export type ReviewResult = {
  id: number
  status: 'TEACHER_APPROVED' | 'APPROVED' | 'REJECTED'
  reviewedAt: string
}
export type BulkOutcome =
  'TEACHER_APPROVED' | 'APPROVED' | 'NOT_VIEWED' | 'NOT_PENDING' | 'MALFORMED'
export type BulkResult = { results: { id: number; outcome: BulkOutcome }[] }
