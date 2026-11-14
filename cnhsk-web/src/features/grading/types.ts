export type RequestStatus = 'PENDING' | 'ASSIGNED' | 'COMPLETED' | 'FAILED' | 'EXPIRED'

export const statusLabels: Readonly<Record<RequestStatus, string>> = {
  PENDING: 'Chờ nhận',
  ASSIGNED: 'Đang chấm',
  COMPLETED: 'Đã chấm',
  FAILED: 'Không hoàn thành',
  EXPIRED: 'Hết hạn',
}

export type GradingPolicy = {
  claimHours: number
  gradingHours: number
  minHanzi: number
  maxHanzi: number
  maxChars: number
  cost: number
  refundTerms: string
}

export type GradingRequestWrite = {
  essay: string
  prompt?: string | null
  hskLevel?: number | null
}
export type Criteria = { grammar: number; vocabulary: number; coherence: number; writing: number }
export type Correction = { original: string; corrected: string; note?: string | null }
export type GradingResult = {
  score: number
  criteria: Criteria
  feedback: string
  corrections: Correction[]
}

export type GradingRequest = {
  id: number
  reference: string
  status: RequestStatus
  essay: string
  prompt: string | null
  hskLevel: number | null
  createdAt: string
  deadline: string | null
  completedAt: string | null
  result: GradingResult | null
  refunded: boolean
}

export type GradingRequestSummary = {
  id: number
  reference: string
  essayPreview: string
  status: RequestStatus
  createdAt: string
  deadline: string | null
  completedAt: string | null
  score: number | null
}
export type GradingRequestPage = {
  items: GradingRequestSummary[]
  page: number
  size: number
  totalElements: number
}
export type ListParams = { status?: RequestStatus; page?: number; size?: number }

export function displayTime(value: string | null): string {
  if (value === null) return '—'
  return new Intl.DateTimeFormat('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value))
}
