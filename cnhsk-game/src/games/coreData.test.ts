import { describe, expect, it } from 'vitest'
import { choices, pinyinBoards, pool, radicalByChar, radicalPool } from './coreData'

describe('four CNHSK game sample pools', () => {
  it.each([1, 2] as const)('has three playable pinyin boards for HSK %i', (level) => {
    const boards = pinyinBoards(level)
    expect(boards).toHaveLength(3)
    for (const board of boards) {
      expect(board).toHaveLength(6)
      expect(new Set(board.map((word) => word.pinyinKey)).size).toBe(6)
      expect(board.every((word) => word.kind === 'WORD')).toBe(true)
    }
  })

  it.each([1, 2] as const)('has enough verified radical samples for HSK %i', (level) => {
    const words = radicalPool(level)
    expect(words.length).toBeGreaterThanOrEqual(15)
    expect(words.every((word) => radicalByChar.has(word.text))).toBe(true)
  })

  it('never creates duplicate meaning choices or omits the correct answer', () => {
    const words = pool(1)
    for (const word of words.slice(0, 25)) {
      const options = choices(word, words, 4, (item) => item.meaning)
      expect(options).toHaveLength(4)
      expect(new Set(options.map((item) => item.meaning)).size).toBe(4)
      expect(options.some((item) => item.id === word.id)).toBe(true)
    }
  })
})
