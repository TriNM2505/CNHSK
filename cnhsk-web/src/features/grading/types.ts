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
  /** A writing task of the competency tree; the teacher then grades it with R-VIET. */
  taskId?: number | null
}
export type Criteria = { grammar: number; vocabulary: number; coherence: number; writing: number }
export type GradingMode = 'ESSAY' | 'SPEAKING'

/** A speaking request reuses the four 0–25 marks of an essay under pronunciation names. */
export function criterionLabelsFor(mode?: GradingMode): Readonly<Record<keyof Criteria, string>> {
  return mode === 'SPEAKING'
    ? {
        grammar: 'Thanh điệu',
        vocabulary: 'Thanh mẫu, vận mẫu',
        coherence: 'Trôi chảy, ngữ điệu',
        writing: 'Rõ ràng',
      }
    : { grammar: 'Ngữ pháp', vocabulary: 'Từ vựng', coherence: 'Mạch lạc', writing: 'Chữ viết' }
}
export type Correction = { original: string; corrected: string; note?: string | null }
export type RubricCode = 'R_NOI' | 'R_VIET'
export type TaggedError = { code: string; nameVi: string; severity: number; fixHint: string | null }

/**
 * Legacy grades carry `criteria` (four 0–25 marks). Rubric grades carry `rubric`, `scores` per criterion
 * (C1…) and the error codes the teacher confirmed; `score` is then the rubric total out of 100.
 */
export type GradingResult = {
  score: number
  criteria?: Criteria | null
  feedback: string
  corrections: Correction[]
  rubric?: RubricCode
  scores?: Record<string, number>
  errors?: TaggedError[]
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
  mode?: GradingMode
  /** The learner's recording when `mode` is SPEAKING. */
  audioUrl?: string | null
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
  mode?: GradingMode
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
