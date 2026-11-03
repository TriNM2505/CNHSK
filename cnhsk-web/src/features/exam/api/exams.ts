import type { SessionView } from '@/features/practice'
import { apiRequest } from '@/shared/api'
import type { HskLevel } from '@/shared/ui'

import type { ExamResult, ExamSummary, WeakPoint } from '../types'

function examPath(examId: number, suffix = ''): string {
  return `/api/exams/${encodeURIComponent(String(examId))}${suffix}`
}

export function getExams(hskLevel?: HskLevel, signal?: AbortSignal): Promise<ExamSummary[]> {
  const query = hskLevel === undefined ? '' : `?hskLevel=${hskLevel}`
  return apiRequest<ExamSummary[]>(`/api/exams${query}`, { signal })
}

export function startExamAttempt(examId: number): Promise<SessionView> {
  return apiRequest<SessionView>(examPath(examId, '/attempts'), { method: 'POST' })
}

export function getExamResult(sessionId: number, signal?: AbortSignal): Promise<ExamResult> {
  return apiRequest<ExamResult>(
    `/api/exam-attempts/${encodeURIComponent(String(sessionId))}/result`,
    {
      signal,
    },
  )
}

export function getExamWeakPoints(sessionId: number, signal?: AbortSignal): Promise<WeakPoint[]> {
  return apiRequest<WeakPoint[]>(
    `/api/exam-attempts/${encodeURIComponent(String(sessionId))}/weak-points`,
    { signal },
  )
}
