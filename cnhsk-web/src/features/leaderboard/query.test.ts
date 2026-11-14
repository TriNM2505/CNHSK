import { describe, expect, it } from 'vitest'

import { firstGame, firstTopic, parseHsk, parsePeriod, parseTopicId } from './query'

describe('leaderboard URL selection helpers', () => {
  it('defaults unknown periods to week and accepts the three supported periods', () => {
    expect(parsePeriod(null)).toBe('week')
    expect(parsePeriod('invalid')).toBe('week')
    expect(parsePeriod('month')).toBe('month')
    expect(parsePeriod('all')).toBe('all')
  })

  it('parses HSK and positive safe topic ids', () => {
    expect(parseHsk('5')).toBe(5)
    expect(parseHsk('10')).toBeUndefined()
    expect(parseTopicId('43')).toBe(43)
    expect(parseTopicId('0')).toBeUndefined()
    expect(parseTopicId('1.5')).toBeUndefined()
  })

  it('uses the first API result when a selection is absent', () => {
    expect(firstTopic([{ id: 43, name: 'Chủ đề', hskLevel: 2 }])?.id).toBe(43)
    expect(firstGame([{ code: 'CHAR_RAIN', name: 'Mưa Chữ' }])?.code).toBe('CHAR_RAIN')
    expect(firstTopic([])).toBeUndefined()
    expect(firstGame([])).toBeUndefined()
  })
})
