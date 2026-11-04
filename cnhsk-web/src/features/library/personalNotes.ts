import type { ApiError } from '@/shared/api'

import type { Note, NoteRequest } from './types'

export const notesPageSize = 20

export type NotesSearch = { q: string; tag: string; page: number; tab: 'active' | 'trash' }
export type NoteDraft = {
  id?: number
  title: string
  content: string
  tags: string[]
  tagInput: string
  lexemeId?: number | null
  sourceType?: NoteRequest['sourceType']
}

export function readNotesSearch(params: URLSearchParams): NotesSearch {
  const rawPage = params.get('page') ?? '0'
  const page = /^\d+$/.test(rawPage) ? Number(rawPage) : 0
  return {
    q: (params.get('q') ?? '').trim().normalize('NFC'),
    tag: params.get('tag') ?? '',
    page: Number.isSafeInteger(page) ? page : 0,
    tab: params.get('tab') === 'trash' ? 'trash' : 'active',
  }
}

export function notesSearchParams(search: NotesSearch): URLSearchParams {
  const params = new URLSearchParams()
  if (search.q) params.set('q', search.q)
  if (search.tag) params.set('tag', search.tag)
  if (search.tab === 'trash') params.set('tab', 'trash')
  params.set('page', String(search.page))
  return params
}

export function validateNotesSearch(q: string, tag: string): string | undefined {
  const normalized = q.trim().normalize('NFC')
  const characters = Array.from(normalized)
  if (
    normalized &&
    (characters.length < 2 ||
      characters.length > 100 ||
      characters.some((character) => {
        const code = character.codePointAt(0)!
        return code < 32 || (code >= 127 && code <= 159)
      }))
  ) {
    return 'Từ khoá phải từ 2 đến 100 ký tự, không chứa ký tự điều khiển.'
  }
  if (Array.from(tag).length > 50 || tag.includes('\0')) return 'Thẻ lọc tối đa 50 ký tự.'
  return undefined
}

export function newNoteDraft(): NoteDraft {
  return { title: '', content: '', tags: [], tagInput: '', lexemeId: null, sourceType: 'MANUAL' }
}

export function noteDraft(note: Note): NoteDraft {
  return {
    id: note.id,
    title: note.title ?? '',
    content: note.content,
    tags: [...note.tags],
    tagInput: '',
    lexemeId: note.lexemeId,
    sourceType: note.sourceType as NoteRequest['sourceType'],
  }
}

export function draftTags(draft: NoteDraft): string[] {
  return draft.tagInput.trim() ? [...draft.tags, draft.tagInput.trim()] : draft.tags
}

export function validateNoteDraft(draft: NoteDraft): string | undefined {
  const tags = draftTags(draft)
  if (draft.title.length > 200) return 'Tiêu đề tối đa 200 ký tự.'
  if (!draft.content.trim() || draft.content.length > 10_000) return 'Nội dung cần 1–10.000 ký tự.'
  if (tags.length > 10 || tags.some((tag) => tag.length > 50))
    return 'Tối đa 10 thẻ, mỗi thẻ tối đa 50 ký tự.'
  if ([draft.title, draft.content, ...tags].some((text) => text.includes('\0')))
    return 'Ghi chú không được chứa ký tự rỗng.'
  return undefined
}

export function noteRequest(draft: NoteDraft): NoteRequest {
  return {
    title: draft.title || null,
    content: draft.content,
    tags: draftTags(draft),
    lexemeId: draft.lexemeId,
    sourceType: draft.sourceType,
  }
}

function detailsRecord(error: ApiError): Record<string, unknown> | undefined {
  return typeof error.details === 'object' &&
    error.details !== null &&
    !Array.isArray(error.details)
    ? (error.details as Record<string, unknown>)
    : undefined
}

export function duplicateNoteId(error?: ApiError): number | undefined {
  if (error?.status !== 409 || error.errorCode !== 'NOTE_DUPLICATE') return undefined
  const id = detailsRecord(error)?.noteId
  return typeof id === 'number' && Number.isSafeInteger(id) && id > 0 ? id : undefined
}

export function noteErrorMessage(error: ApiError): string {
  switch (error.errorCode) {
    case 'NOTE_DUPLICATE':
      return 'Từ này đã có trong sổ tay'
    case 'LIBRARY_LIMIT_REACHED': {
      const limit = detailsRecord(error)?.limit
      return typeof limit === 'number' && Number.isSafeInteger(limit) && limit > 0
        ? `Sổ tay đã đạt giới hạn ${limit.toLocaleString('vi-VN')} ghi chú.`
        : 'Sổ tay đã đạt giới hạn ghi chú.'
    }
    case 'LEXEME_NOT_FOUND':
      return 'Mục từ gắn với ghi chú không còn tồn tại.'
    case 'COLLECTION_NOT_OWNED':
      return 'Bạn không có quyền truy cập ghi chú này.'
    case 'NOTE_NOT_RESTORABLE':
      return 'Ghi chú không thể khôi phục: đã quá hạn 30 ngày hoặc không còn trong thùng rác.'
    default:
      return error.message
  }
}

export function snippetParts(text: string, query: string): { text: string; match: boolean }[] {
  if (!query) return [{ text, match: false }]
  const escaped = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return text.split(new RegExp(`(${escaped})`, 'giu')).map((part, index) => ({
    text: part,
    match: index % 2 === 1,
  }))
}

export function noteDate(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? 'Thời gian chưa hợp lệ'
    : new Intl.DateTimeFormat('vi-VN', {
        dateStyle: 'short',
        timeStyle: 'short',
        timeZone: 'Asia/Ho_Chi_Minh',
      }).format(date)
}
