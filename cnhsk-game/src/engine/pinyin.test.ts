import { describe, expect, it } from 'vitest'
import {
  lettersOf,
  parseAttempt,
  segmentLetters,
  targetTones,
  toNumberedPinyin,
  validatePinyinInput,
} from './pinyin'

describe('lettersOf', () => {
  it('folds tone marks and drops separators', () => {
    expect(lettersOf('nǐ hǎo')).toBe('nihao')
    expect(lettersOf('NǏHǍO')).toBe('nihao')
  })

  it('accepts v and u for u-diaeresis', () => {
    expect(lettersOf('lǜ')).toBe('lu')
    expect(lettersOf('lv4')).toBe('lu')
    expect(lettersOf('lu4')).toBe('lu')
  })

  it('ignores digits and punctuation', () => {
    expect(lettersOf('ni3, hao3!')).toBe('nihao')
  })
})

describe('segmentLetters', () => {
  it('splits a bare letter string into syllables', () => {
    expect(segmentLetters('nihao')).toEqual(['ni', 'hao'])
    expect(segmentLetters('xuexi')).toEqual(['xue', 'xi'])
    expect(segmentLetters('shuijiao')).toEqual(['shui', 'jiao'])
  })

  it('prefers the longest valid syllable', () => {
    expect(segmentLetters('zhongwen')).toEqual(['zhong', 'wen'])
    expect(segmentLetters('xihuan')).toEqual(['xi', 'huan'])
  })

  it('handles the whole word list without losing a letter', () => {
    for (const word of ['māma', 'xuéxí', 'shuìjiào', 'piàoliang', 'túshūguǎn', 'yīnyuè']) {
      const letters = lettersOf(word)
      expect(segmentLetters(letters).join('')).toBe(letters)
    }
  })
})

describe('toNumberedPinyin', () => {
  it('matches spec 007 FR-021 (numbered, no spaces)', () => {
    expect(toNumberedPinyin('nǐ hǎo')).toBe('ni3hao3')
    expect(toNumberedPinyin('wǒ')).toBe('wo3')
    expect(toNumberedPinyin('yī')).toBe('yi1')
  })

  it('numbers syllables even when the data has no spaces', () => {
    expect(toNumberedPinyin('xuéxí')).toBe('xue2xi2')
    expect(toNumberedPinyin('shuìjiào')).toBe('shui4jiao4')
  })

  it('leaves a neutral syllable without a number', () => {
    expect(toNumberedPinyin('xièxie')).toBe('xie4xie')
    expect(toNumberedPinyin('mā ma')).toBe('ma1ma')
  })
})

describe('targetTones', () => {
  it('returns one tone per syllable', () => {
    expect(targetTones('nǐ hǎo')).toEqual([3, 3])
    expect(targetTones('mā ma')).toEqual([1, 0])
    expect(targetTones('xuéxí')).toEqual([2, 2])
  })
})

describe('parseAttempt', () => {
  it('reads tones from marks', () => {
    expect(parseAttempt('nǐhǎo')).toEqual([
      { text: 'ni', tone: 3 },
      { text: 'hao', tone: 3 },
    ])
  })

  it('reads tones from digits and closes the syllable before each digit', () => {
    expect(parseAttempt('ni3hao3')).toEqual([
      { text: 'ni', tone: 3 },
      { text: 'hao', tone: 3 },
    ])
  })

  it('leaves tones unknown when none are given', () => {
    expect(parseAttempt('nihao')).toEqual([
      { text: 'ni', tone: null },
      { text: 'hao', tone: null },
    ])
  })

  it('marks a syllable as unknown when only some tones are typed', () => {
    expect(parseAttempt('ni3hao')).toEqual([
      { text: 'ni', tone: 3 },
      { text: 'hao', tone: null },
    ])
  })
})

describe('validatePinyinInput', () => {
  it('AT-22: rejects partial tones before sending an answer', () => {
    expect(validatePinyinInput('ni3hao')).toBe('partial-tones')
  })

  it.each(['xuéxí', 'xue2xi2', 'xuexi', 'lu:4', 'lv4'])('accepts the format of %s', (input) => {
    expect(validatePinyinInput(input)).toBe('ok')
  })

  it('rejects blank input and unsupported characters', () => {
    expect(validatePinyinInput('  ')).toBe('empty')
    expect(validatePinyinInput('ni0hao')).toBe('invalid')
    expect(validatePinyinInput('!')).toBe('invalid')
  })
})
