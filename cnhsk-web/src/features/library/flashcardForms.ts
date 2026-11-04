import type { ApiError } from '@/shared/api'

import type { CardRequest, DeckRequest, ReviewRating } from './types'

export const flashcardPageSize = 20
export type DeckDraft = {
  name: string
  description: string
  source: 'empty' | 'topic'
  topicId: string
}
export type CardDraft = { id?: number; front: string; back: string }
export type TopicChoice = { id: number; name: string; totalWords: number }

export const reviewChoices: readonly { rating: ReviewRating; label: string }[] = [
  { rating: 'AGAIN', label: 'Quên' },
  { rating: 'HARD', label: 'Khó' },
  { rating: 'GOOD', label: 'Được' },
  { rating: 'EASY', label: 'Dễ' },
]

export function characterCount(value: string): number {
  return Array.from(value).length
}
export function emptyDeckDraft(): DeckDraft {
  return { name: '', description: '', source: 'empty', topicId: '' }
}
export function emptyCardDraft(): CardDraft {
  return { front: '', back: '' }
}

export function resourceId(value: string): number | undefined {
  const id = /^\d+$/.test(value) ? Number(value) : 0
  return Number.isSafeInteger(id) && id > 0 ? id : undefined
}

export function validateDeckDraft(
  draft: DeckDraft,
  topics?: readonly TopicChoice[],
): string | undefined {
  if (!draft.name.trim() || characterCount(draft.name.trim()) > 100)
    return 'Tên bộ thẻ cần 1–100 ký tự.'
  if (characterCount(draft.description) > 500) return 'Mô tả tối đa 500 ký tự.'
  if (
    draft.source === 'topic' &&
    (resourceId(draft.topicId) === undefined ||
      !topics?.some((topic) => String(topic.id) === draft.topicId))
  )
    return 'Chọn một chủ đề đã tải để tạo bộ thẻ.'
  return undefined
}

export function deckRequest(draft: DeckDraft): DeckRequest {
  return {
    name: draft.name.trim(),
    description: draft.description || null,
    ...(draft.source === 'topic' ? { topicId: resourceId(draft.topicId) } : {}),
  }
}

export function validateCardDraft(draft: CardDraft): string | undefined {
  if (!draft.front.trim() || characterCount(draft.front) > 200) return 'Mặt trước cần 1–200 ký tự.'
  if (!draft.back.trim() || characterCount(draft.back) > 2000) return 'Mặt sau cần 1–2.000 ký tự.'
  return undefined
}

export function cardRequest(draft: CardDraft): CardRequest {
  return { front: draft.front, back: draft.back }
}

export function flashcardError(error: ApiError): string {
  switch (error.errorCode) {
    case 'CARD_DUPLICATE':
      return 'Thẻ đã có trong bộ này. Hãy sửa mặt trước hoặc chọn nội dung khác.'
    case 'COLLECTION_NOT_OWNED':
      return 'Bạn không có quyền truy cập bộ thẻ hoặc thẻ này.'
    case 'TOPIC_NOT_FOUND':
      return 'Chủ đề không còn tồn tại. Hãy chọn lại chủ đề.'
    case 'LEXEME_NOT_FOUND':
      return 'Mục từ không còn tồn tại.'
    case 'LIBRARY_LIMIT_REACHED': {
      const details = error.details
      if (
        typeof details === 'object' &&
        details !== null &&
        'limit' in details &&
        'kind' in details &&
        typeof details.limit === 'number' &&
        Number.isSafeInteger(details.limit) &&
        details.limit > 0 &&
        (details.kind === 'DECKS' || details.kind === 'CARDS')
      )
        return `Đã đạt giới hạn ${details.limit.toLocaleString('vi-VN')} ${details.kind === 'DECKS' ? 'bộ thẻ' : 'thẻ trong bộ'}.`
      return 'Đã đạt giới hạn thư viện.'
    }
    case 'RATE_LIMITED': {
      const delay = error.extra.retryAfterSeconds
      return typeof delay === 'number' && Number.isFinite(delay) && delay > 0
        ? `Bạn đã ôn quá nhanh. Thử lại sau ${Math.ceil(delay)} giây.`
        : 'Bạn đã đạt giới hạn lượt ôn. Vui lòng thử lại sau.'
    }
    default:
      return error.message
  }
}

export function flashcardDate(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? 'Thời gian chưa hợp lệ'
    : new Intl.DateTimeFormat('vi-VN', {
        dateStyle: 'short',
        timeStyle: 'short',
        timeZone: 'Asia/Ho_Chi_Minh',
      }).format(date)
}

export function clientPage(page: number, total: number): number {
  return Math.min(Math.max(0, page), Math.max(0, Math.ceil(total / flashcardPageSize) - 1))
}
