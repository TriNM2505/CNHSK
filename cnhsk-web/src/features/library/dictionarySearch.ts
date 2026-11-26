import type { DictionarySearchType } from './types'

export type DictionaryMode = DictionarySearchType | 'RADICAL'
export type DictionarySearch = {
  type: DictionaryMode
  q: string
  radical: string
  strokes: string
  page: number
}

export const dictionaryModes: readonly { value: DictionaryMode; label: string }[] = [
  { value: 'HANZI', label: 'Tiếng Trung → Tiếng Việt' },
  { value: 'MEANING', label: 'Tiếng Việt → Tiếng Trung' },
  { value: 'PINYIN', label: 'Tra theo pinyin' },
  { value: 'RADICAL', label: 'Tra theo bộ thủ + số nét' },
]

/** Longest text one translation may carry, matching the server limit. */
export const MAX_TRANSLATE_LENGTH = 2000

export function readDictionarySearch(params: URLSearchParams): DictionarySearch {
  const rawPage = params.get('page') ?? '0'
  const page = /^\d+$/.test(rawPage) ? Number(rawPage) : 0
  return {
    type: dictionaryModes.find(({ value }) => value === params.get('type'))?.value ?? 'HANZI',
    q: params.get('q') ?? '',
    radical: params.get('radical') ?? '',
    strokes: params.get('strokes') ?? '',
    page: Number.isSafeInteger(page) ? page : 0,
  }
}

export function validateDictionarySearch(search: DictionarySearch): string | undefined {
  if (search.type === 'RADICAL') {
    if (!search.radical.trim() && !search.strokes.trim()) return 'Chọn bộ thủ hoặc nhập số nét.'
    if (search.radical.length > 10) return 'Bộ thủ không quá 10 ký tự.'
    if (
      search.strokes &&
      (!/^\d+$/.test(search.strokes) || Number(search.strokes) < 1 || Number(search.strokes) > 64)
    ) {
      return 'Số nét phải là số nguyên từ 1 đến 64.'
    }
    return undefined
  }
  if (!search.q.trim()) return 'Nhập nội dung cần dịch.'
  if (search.type === 'MEANING' && Array.from(search.q.trim()).length < 2) {
    return 'Nghĩa Việt cần ít nhất 2 ký tự.'
  }
  if (Array.from(search.q).length > MAX_TRANSLATE_LENGTH) return 'Tối đa 2.000 ký tự mỗi lần dịch.'
  return undefined
}

export function dictionarySearchParams(search: DictionarySearch): URLSearchParams {
  const params = new URLSearchParams({ type: search.type })
  if (search.type === 'RADICAL') {
    if (search.radical) params.set('radical', search.radical)
    if (search.strokes) params.set('strokes', search.strokes)
  } else params.set('q', search.q)
  params.set('page', String(search.page))
  return params
}

/** The merged screen lives at /translate; old /dictionary links are still accepted as a way back. */
export const LOOKUP_PATH = '/translate'

export function dictionaryReturnTo(value: string | null): string {
  return value && /^\/(?:translate|dictionary)(?:\?[^#]*)?$/.test(value) ? value : LOOKUP_PATH
}

export function dictionaryDetailHref(id: number, returnTo: string): string {
  return `/dictionary/${id}?${new URLSearchParams({ from: dictionaryReturnTo(returnTo) })}`
}
