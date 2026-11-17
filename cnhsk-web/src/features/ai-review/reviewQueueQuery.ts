import { SUPPORTED_HSK_LEVELS } from '@/shared/ui'

import type { ReviewQueueParams } from './types'

export type ReviewQueueQuery = {
  kid: string
  hsk: string
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
  const from = search.get('from') ?? ''
  const to = search.get('to') ?? ''
  const page = Number(search.get('page') ?? 0)
  const size = Number(search.get('size') ?? 20)
  return {
    kid: Array.from((search.get('kid') ?? '').trim())
      .slice(0, 20)
      .join(''),
    hsk: SUPPORTED_HSK_LEVELS.some((level) => String(level) === hsk) ? hsk : '',
    from: validReviewDate(from) ? from : '',
    to: validReviewDate(to) && (!validReviewDate(from) || to >= from) ? to : '',
    page: Number.isSafeInteger(page) && page >= 0 ? page : 0,
    size: Number.isInteger(size) && size >= 1 && size <= 50 ? size : 20,
  }
}

export function reviewQueueSearch(query: ReviewQueueQuery): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) if (value !== '') search.set(key, String(value))
  return search.toString()
}

export function reviewQueueParams(query: ReviewQueueQuery): ReviewQueueParams {
  return {
    kid: query.kid || undefined,
    hsk: query.hsk ? Number(query.hsk) : undefined,
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

export function reviewDetailTo(id: number, query: ReviewQueueQuery): string {
  return `/content/questions/${id}?reviewQueue=1&${reviewQueueSearch(query)}`
}

export function rejectionReasonError(reason: string): string | undefined {
  const length = Array.from(reason.trim()).length
  return length < 1 || length > 1000 ? 'Nhập lý do từ 1 đến 1000 ký tự.' : undefined
}
