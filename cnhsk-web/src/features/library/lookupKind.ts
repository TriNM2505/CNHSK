import type { DictionarySearch } from './dictionarySearch'
import type { SearchResult, TranslationLanguage } from './types'

/**
 * How one entry of the merged "Dịch" screen is answered:
 * - `dictionary`: pinyin and radical lookups only ever read the dictionary;
 * - `word`: a single Chinese word or a short Vietnamese meaning is looked up first and translated by AI only
 *   when the dictionary has no entry for it;
 * - `phrase`: anything longer goes straight to AI translation.
 */
export type LookupKind = 'dictionary' | 'word' | 'phrase'

/** Longest Chinese entry still treated as one word; HSK 1-3 words are at most four characters. */
const MAX_WORD_HANZI = 4
/** Longest Vietnamese entry, in syllables, still looked up as a meaning before translating. */
const MAX_WORD_SYLLABLES = 3
const HAN = /^\p{Script=Han}+$/u
const SENTENCE_MARK = /[.,!?;:，。！？；：、]/

export function lookupKind(search: Pick<DictionarySearch, 'type' | 'q'>): LookupKind {
  const text = search.q.trim()
  if (search.type === 'HANZI') {
    return HAN.test(text) && Array.from(text).length <= MAX_WORD_HANZI ? 'word' : 'phrase'
  }
  if (search.type === 'MEANING') {
    return !SENTENCE_MARK.test(text) && text.split(/\s+/).length <= MAX_WORD_SYLLABLES ? 'word' : 'phrase'
  }
  return 'dictionary'
}

/**
 * A word counts as found only when the dictionary has it: an exact Chinese entry, not the per-character
 * fallback the server returns for unknown words, or any entry for a Vietnamese meaning.
 */
export function foundInDictionary(search: Pick<DictionarySearch, 'type' | 'q'>, result: SearchResult): boolean {
  if (search.type === 'HANZI') return result.items.some((item) => item.text === search.q.trim())
  return result.items.length > 0
}

export function translationSource(type: DictionarySearch['type']): TranslationLanguage {
  return type === 'MEANING' ? 'VI' : 'ZH'
}
