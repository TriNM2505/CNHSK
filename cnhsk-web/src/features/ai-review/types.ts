export type ReviewQueueParams = {
  kid?: string
  hsk?: number
  from?: string
  to?: string
  page?: number
  size?: number
}

export type AIQuestionSummary = {
  id: number
  stemSummary: string
  hskLevel: number
  labels: { kid: string; role: 'PRIMARY' | 'SECONDARY' }[]
  generatedAt: string
  runId: number | null
  viewedByMe: boolean
}

export type AIQuestionPage = {
  items: AIQuestionSummary[]
  page: number
  size: number
  totalElements: number
}

export type ReviewResult = { id: number; status: 'APPROVED' | 'REJECTED'; reviewedAt: string }
export type BulkOutcome = 'APPROVED' | 'NOT_VIEWED' | 'NOT_PENDING' | 'MALFORMED'
export type BulkResult = { results: { id: number; outcome: BulkOutcome }[] }
