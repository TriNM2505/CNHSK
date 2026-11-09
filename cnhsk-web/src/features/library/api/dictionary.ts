import { apiRequest } from '@/shared/api'

import { checkedIdentifiers, identifierPath } from './identifiers'

import type {
  DictionarySearchParams,
  LexemeDetail,
  RadicalCount,
  RadicalSearchParams,
  SearchResult,
} from '../types'

const dictionaryPath = '/api/public/dictionary'

export async function searchDictionary(
  { type, q, page = 0, size = 20 }: DictionarySearchParams,
  signal?: AbortSignal,
): Promise<SearchResult> {
  const query = new URLSearchParams({ type, q, page: String(page), size: String(size) })
  return checkedIdentifiers(
    apiRequest<SearchResult>(`${dictionaryPath}/search?${query.toString()}`, { signal }),
  )
}

export async function listRadicals(signal?: AbortSignal): Promise<RadicalCount[]> {
  return checkedIdentifiers(apiRequest<RadicalCount[]>(`${dictionaryPath}/radicals`, { signal }))
}

export async function searchByRadical(
  { radical, strokes, page = 0, size = 20 }: RadicalSearchParams,
  signal?: AbortSignal,
): Promise<SearchResult> {
  const query = new URLSearchParams()
  if (radical !== undefined) query.set('radical', radical)
  if (strokes !== undefined) query.set('strokes', String(strokes))
  query.set('page', String(page))
  query.set('size', String(size))
  return checkedIdentifiers(
    apiRequest<SearchResult>(`${dictionaryPath}/by-radical?${query.toString()}`, { signal }),
  )
}

export async function getLexeme(id: number, signal?: AbortSignal): Promise<LexemeDetail> {
  return checkedIdentifiers(
    apiRequest<LexemeDetail>(`${dictionaryPath}/lexemes/${identifierPath(id)}`, {
      signal,
    }),
  )
}
