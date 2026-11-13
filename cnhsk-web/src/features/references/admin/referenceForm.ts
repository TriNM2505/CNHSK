import type { ApiError } from '@/shared/api'
import type { ReferenceKind } from '../types'
import type { AdminReference, ReferenceAction, ReferenceInput, ReferenceStatus } from './types'

export type ReferenceDraft = {
  kind: ReferenceKind
  title: string
  description: string
  url: string
  thumbnailUrl: string
  hskMin: string
  hskMax: string
  tags: string[]
  sortOrder: string
  author: string
  publisher: string
  publishedYear: string
  isAffiliate: boolean
}
export type FieldErrors = Partial<Record<keyof ReferenceDraft, string>>
export const kindLabels: Record<ReferenceKind, string> = {
  YOUTUBE_CHANNEL: 'YouTube',
  PODCAST: 'Podcast',
  BOOK: 'Sách',
}
export const statusLabels: Record<ReferenceStatus, string> = {
  DRAFT: 'Bản nháp',
  PUBLISHED: 'Đã công bố',
  HIDDEN: 'Đã ẩn',
}
export const actionLabels: Record<ReferenceAction, string> = {
  publish: 'Công bố',
  hide: 'Ẩn',
  unhide: 'Hiện lại',
}

export function availableActions(status: ReferenceStatus): ReferenceAction[] {
  return status === 'DRAFT'
    ? ['publish', 'hide']
    : status === 'PUBLISHED'
      ? ['hide']
      : ['unhide', 'publish']
}
export function newDraft(): ReferenceDraft {
  return {
    kind: 'YOUTUBE_CHANNEL',
    title: '',
    description: '',
    url: '',
    thumbnailUrl: '',
    hskMin: '',
    hskMax: '',
    tags: [],
    sortOrder: '0',
    author: '',
    publisher: '',
    publishedYear: '',
    isAffiliate: false,
  }
}
export function draftOf(item: AdminReference): ReferenceDraft {
  return {
    kind: item.kind,
    title: item.title,
    description: item.description ?? '',
    url: item.url,
    thumbnailUrl: item.thumbnailUrl ?? '',
    hskMin: item.hskMin?.toString() ?? '',
    hskMax: item.hskMax?.toString() ?? '',
    tags: [...item.tags],
    sortOrder: String(item.sortOrder),
    author: item.author ?? '',
    publisher: item.publisher ?? '',
    publishedYear: item.publishedYear?.toString() ?? '',
    isAffiliate: item.isAffiliate ?? false,
  }
}
export function inputOf(draft: ReferenceDraft): ReferenceInput {
  const optional = (value: string) => (value === '' ? null : value)
  return {
    kind: draft.kind,
    title: draft.title,
    description: optional(draft.description),
    url: draft.url,
    thumbnailUrl: optional(draft.thumbnailUrl),
    hskMin: draft.hskMin === '' ? null : Number(draft.hskMin),
    hskMax: draft.hskMax === '' ? null : Number(draft.hskMax),
    tags: [...draft.tags],
    sortOrder: Number(draft.sortOrder),
    author: draft.kind === 'BOOK' ? optional(draft.author) : null,
    publisher: draft.kind === 'BOOK' ? optional(draft.publisher) : null,
    publishedYear:
      draft.kind === 'BOOK' && draft.publishedYear !== '' ? Number(draft.publishedYear) : null,
    isAffiliate: draft.kind === 'BOOK' && draft.isAffiliate,
  }
}
export function validateDraft(draft: ReferenceDraft): FieldErrors {
  const errors: FieldErrors = {}
  for (const [field, maximum] of [
    ['title', 200],
    ['description', 2000],
    ['url', 2000],
    ['thumbnailUrl', 2000],
    ...(draft.kind === 'BOOK'
      ? [
          ['author', 200],
          ['publisher', 200],
        ]
      : []),
  ] as [keyof ReferenceDraft, number][]) {
    if ([...String(draft[field])].length > maximum) errors[field] = `Tối đa ${maximum} ký tự.`
  }
  if (!draft.title.trim()) errors.title = 'Nhập tên tài nguyên.'
  if (!draft.url) errors.url = 'Nhập liên kết tài nguyên.'
  for (const field of ['hskMin', 'hskMax'] as const) {
    if (draft[field] !== '' && !/^[1-9]$/.test(draft[field]))
      errors[field] = 'Cấp HSK không hợp lệ.'
  }
  if (
    !errors.hskMin &&
    !errors.hskMax &&
    draft.hskMin &&
    draft.hskMax &&
    Number(draft.hskMin) > Number(draft.hskMax)
  )
    errors.hskMax = 'Cấp tối đa không nhỏ hơn cấp tối thiểu.'
  if (
    !/^-?\d+$/.test(draft.sortOrder) ||
    Number(draft.sortOrder) < -2147483648 ||
    Number(draft.sortOrder) > 2147483647
  )
    errors.sortOrder = 'Nhập số nguyên trong khoảng int32.'
  if (
    draft.kind === 'BOOK' &&
    draft.publishedYear !== '' &&
    (!/^\d{4}$/.test(draft.publishedYear) ||
      Number(draft.publishedYear) < 1900 ||
      Number(draft.publishedYear) > 2100)
  )
    errors.publishedYear = 'Năm xuất bản từ 1900 đến 2100.'
  if (draft.tags.length > 10 || draft.tags.some((tag) => [...tag].length > 30))
    errors.tags = 'Tối đa 10 tag, mỗi tag tối đa 30 ký tự.'
  return errors
}
export function fieldErrors(error?: ApiError): FieldErrors {
  if (
    !error ||
    error.status !== 422 ||
    ![
      'MALFORMED_URL',
      'NON_HTTPS_LINK',
      'DOMAIN_NOT_ALLOWED',
      'PUBLISH_INVALID_RESOURCE',
      'VALIDATION_FAILED',
    ].includes(error.errorCode) ||
    !Array.isArray(error.details)
  )
    return {}
  const result: FieldErrors = {}
  for (const detail of error.details) {
    if (
      detail &&
      typeof detail === 'object' &&
      typeof detail.field === 'string' &&
      Object.prototype.hasOwnProperty.call(newDraft(), detail.field) &&
      typeof detail.message === 'string'
    )
      result[detail.field as keyof ReferenceDraft] = detail.message
  }
  return result
}
