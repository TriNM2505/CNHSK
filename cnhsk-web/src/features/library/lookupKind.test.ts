import { describe, expect, it } from 'vitest'

import { foundInDictionary, lookupKind, translationSource } from './lookupKind'
import type { SearchResult } from './types'

const entry = (id: number, text: string) => ({
  id,
  kind: 'WORD' as const,
  text,
  meaningVi: 'nghĩa',
  approximate: false,
})

describe('merged translate screen lookup rules', () => {
  it('looks short Chinese words up and translates longer Chinese text', () => {
    expect(lookupKind({ type: 'HANZI', q: ' 喝茶 ' })).toBe('word')
    expect(lookupKind({ type: 'HANZI', q: '图书馆' })).toBe('word')
    expect(lookupKind({ type: 'HANZI', q: '我喜欢喝茶' })).toBe('phrase')
    expect(lookupKind({ type: 'HANZI', q: '你好！' })).toBe('phrase')
    expect(lookupKind({ type: 'HANZI', q: '你 好' })).toBe('phrase')
  })

  it('looks short Vietnamese meanings up and translates sentences', () => {
    expect(lookupKind({ type: 'MEANING', q: 'uống trà' })).toBe('word')
    expect(lookupKind({ type: 'MEANING', q: 'tôi thích uống trà' })).toBe('phrase')
    expect(lookupKind({ type: 'MEANING', q: 'chào, bạn' })).toBe('phrase')
  })

  it('keeps pinyin and radical lookups in the dictionary', () => {
    expect(lookupKind({ type: 'PINYIN', q: 'he cha wo xi huan' })).toBe('dictionary')
    expect(lookupKind({ type: 'RADICAL', q: '' })).toBe('dictionary')
  })

  it('counts a Chinese word as found only on an exact entry, not on its characters', () => {
    const characters: SearchResult = { items: [entry(1, '喝'), entry(2, '茶')], total: 2 }
    expect(foundInDictionary({ type: 'HANZI', q: '喝茶' }, characters)).toBe(false)
    expect(foundInDictionary({ type: 'HANZI', q: '茶' }, characters)).toBe(true)
    expect(foundInDictionary({ type: 'MEANING', q: 'trà' }, characters)).toBe(true)
    expect(foundInDictionary({ type: 'MEANING', q: 'trà' }, { items: [], total: 0 })).toBe(false)
  })

  it('translates Vietnamese meanings into Chinese and Chinese into Vietnamese', () => {
    expect(translationSource('MEANING')).toBe('VI')
    expect(translationSource('HANZI')).toBe('ZH')
  })
})
