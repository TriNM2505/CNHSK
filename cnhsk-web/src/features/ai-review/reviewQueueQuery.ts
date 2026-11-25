import { SUPPORTED_HSK_LEVELS } from '@/shared/ui'

import type { AIQuestionSummary, ReviewQueueParams, ReviewQuestionType, ReviewStep } from './types'

const QUESTION_TYPES: ReviewQuestionType[] = ['MULTIPLE_CHOICE', 'TRUE_FALSE']

export type ReviewQueueQuery = {
  kid: string
  hsk: string
  /** Empty keeps every question type. */
  qtype?: string
  from: string
  to: string
  page: number
  size: number
}

export function validReviewDate(value: string): boolean {
  return (
    /^\d{4}-\d{2}-\d{2}$/.test(value) &&
    !Number.isNaN(Date.parse(`${value}T00:00:00Z`)) &&
    new Date(`${value}T00:00:00Z`).toISOString().startsWith(value)
  )
}

export function parseReviewQueueQuery(search: URLSearchParams): ReviewQueueQuery {
  const hsk = search.get('hsk') ?? ''
  const qtype = search.get('qtype') ?? ''
  const from = search.get('from') ?? ''
  const to = search.get('to') ?? ''
  const page = Number(search.get('page') ?? 0)
  const size = Number(search.get('size') ?? 20)
  return {
    kid: Array.from((search.get('kid') ?? '').trim())
      .slice(0, 20)
      .join(''),
    hsk: SUPPORTED_HSK_LEVELS.some((level) => String(level) === hsk) ? hsk : '',
    qtype: QUESTION_TYPES.includes(qtype as ReviewQuestionType) ? qtype : '',
    from: validReviewDate(from) ? from : '',
    to: validReviewDate(to) && (!validReviewDate(from) || to >= from) ? to : '',
    page: Number.isSafeInteger(page) && page >= 0 ? page : 0,
    size: Number.isInteger(size) && size >= 1 && size <= 50 ? size : 20,
  }
}

export function reviewQueueSearch(query: ReviewQueueQuery): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(query))
    if (value !== '' && value !== undefined) search.set(key, String(value))
  return search.toString()
}

export function reviewQueueParams(query: ReviewQueueQuery): ReviewQueueParams {
  return {
    kid: query.kid || undefined,
    hsk: query.hsk ? Number(query.hsk) : undefined,
    qtype: query.qtype ? (query.qtype as ReviewQuestionType) : undefined,
    from: query.from ? new Date(`${query.from}T00:00:00+07:00`).toISOString() : undefined,
    to: query.to ? new Date(`${query.to}T23:59:59.999+07:00`).toISOString() : undefined,
    page: query.page,
    size: query.size,
  }
}

export function reviewQueueBackTo(search: URLSearchParams): string | undefined {
  return search.get('reviewQueue') === '1'
    ? `/content/ai-review?${reviewQueueSearch(parseReviewQueueQuery(search))}`
    : undefined
}

export function reviewDetailTo(id: number, query: ReviewQueueQuery, step?: ReviewStep): string {
  const stepParam = step ? `reviewStep=${step}&` : ''
  return `/content/questions/${id}?reviewQueue=1&${stepParam}${reviewQueueSearch(query)}`
}

/**
 * A row can be ticked for bulk approval once opened; at the content step it can be ticked straight away, since
 * a teacher already checked it and the content editor only publishes it.
 */
export function canBulkSelect(row: Pick<AIQuestionSummary, 'viewedByMe' | 'step'>): boolean {
  return row.viewedByMe || row.step === 'CONTENT'
}

/** Review step the detail page was opened for from the queue; absent when opened from the question bank. */
export function reviewStepFrom(search: URLSearchParams): ReviewStep | undefined {
  const step = search.get('reviewStep')
  return step === 'TEACHER' || step === 'CONTENT' ? step : undefined
}

export function rejectionReasonError(reason: string): string | undefined {
  const length = Array.from(reason.trim()).length
  return length < 1 || length > 1000 ? 'Nhập lý do từ 1 đến 1000 ký tự.' : undefined
}
