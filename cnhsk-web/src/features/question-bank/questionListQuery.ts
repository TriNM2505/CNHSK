import { SUPPORTED_HSK_LEVELS, type HskLevel } from '@/shared/ui/LevelBadge'

import type { QuestionSource, QuestionStatus, QuestionType } from './types'

export const QUESTION_PAGE_SIZE = 10

export const QUESTION_TYPE_LABELS: Record<QuestionType, string> = {
  MULTIPLE_CHOICE: 'Trắc nghiệm',
  TRUE_FALSE: 'Đúng / Sai',
  IMAGE_MATCH: 'Ghép hình',
  SENTENCE_MATCH: 'Ghép câu',
  FILL_BLANK: 'Điền chỗ trống',
  SENTENCE_ORDER: 'Sắp xếp câu',
  ESSAY: 'Tự luận',
}

export const QUESTION_STATUS_LABELS: Record<QuestionStatus, string> = {
  PENDING_REVIEW: 'Chờ duyệt',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Đã từ chối',
  ARCHIVED: 'Đã lưu trữ',
}

export const QUESTION_SOURCE_LABELS: Record<QuestionSource, string> = {
  IMPORT: 'Nhập từ tệp',
  AI: 'AI',
  MANUAL: 'Soạn tay',
}

export type QuestionListQuery = {
  hskLevel: HskLevel | ''
  qtype: QuestionType | ''
  status: QuestionStatus | ''
  kid: string
  q: string
  page: number
}

export function questionSearchError(value: string): string | undefined {
  const length = value.trim().length
  return length > 0 && (length < 2 || length > 100)
    ? 'Nhập từ 2 đến 100 ký tự hoặc để trống.'
    : undefined
}

export function questionListSearchParams(
  query: QuestionListQuery,
  current?: URLSearchParams,
): URLSearchParams {
  const result = new URLSearchParams(current)
  for (const key of ['hskLevel', 'qtype', 'status', 'kid', 'q', 'page', 'size']) result.delete(key)
  if (query.hskLevel) result.set('hskLevel', String(query.hskLevel))
  if (query.qtype) result.set('qtype', query.qtype)
  if (query.status) result.set('status', query.status)
  if (query.kid) result.set('kid', query.kid)
  if (query.q) result.set('q', query.q)
  result.set('page', String(query.page))
  result.set('size', String(QUESTION_PAGE_SIZE))
  return result
}

export function parseQuestionListSearchParams(params: URLSearchParams) {
  const rawType = params.get('qtype') ?? ''
  const rawStatus = params.get('status') ?? ''
  const rawQ = params.get('q')?.trim() ?? ''
  const rawPage = params.get('page') ?? '0'
  const page = /^\d+$/.test(rawPage) ? Number(rawPage) : 0
  const query: QuestionListQuery = {
    hskLevel: SUPPORTED_HSK_LEVELS.find((level) => String(level) === params.get('hskLevel')) ?? '',
    qtype: Object.prototype.hasOwnProperty.call(QUESTION_TYPE_LABELS, rawType)
      ? (rawType as QuestionType)
      : '',
    status: Object.prototype.hasOwnProperty.call(QUESTION_STATUS_LABELS, rawStatus)
      ? (rawStatus as QuestionStatus)
      : '',
    kid: params.get('kid')?.trim() ?? '',
    q: questionSearchError(rawQ) ? '' : rawQ,
    page: Number.isSafeInteger(page) ? page : 0,
  }
  const canonical = questionListSearchParams(query, params)
  return { query, canonical, needsCleanup: canonical.toString() !== params.toString() }
}
