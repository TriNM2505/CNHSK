import { describe, expect, it } from 'vitest'
import {
  gradePinyin,
  lettersOf,
  parseAttempt,
  segmentLetters,
  syllableTones,
  targetTones,
  toNumberedPinyin,
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

describe('gradePinyin', () => {
  const target = 'nǐ hǎo'

  it('accepts tone-mark input', () => {
    expect(gradePinyin('nǐhǎo', target).ok).toBe(true)
    expect(gradePinyin('nǐ hǎo', target).ok).toBe(true)
  })

  it('accepts tone-number input', () => {
    expect(gradePinyin('ni3hao3', target).ok).toBe(true)
    expect(gradePinyin('ni3 hao3', target).ok).toBe(true)
  })

  it('accepts tone-less input', () => {
    expect(gradePinyin('nihao', target).ok).toBe(true)
    expect(gradePinyin('ni hao', target).ok).toBe(true)
  })

  it('rejects wrong letters', () => {
    expect(gradePinyin('ninhao', target).verdict).toBe('wrong-letters')
  })

  it('rejects a wrong tone', () => {
    expect(gradePinyin('ni2hao3', target).verdict).toBe('wrong-tones')
  })

  it('asks for all tones instead of guessing a missing one', () => {
    expect(gradePinyin('ni3hao', target).verdict).toBe('partial-tones')
  })

  it('rejects empty input', () => {
    expect(gradePinyin('   ', target).verdict).toBe('empty')
  })

  it('is case insensitive', () => {
    expect(gradePinyin('NI3HAO3', target).ok).toBe(true)
  })

  it('works for data written without spaces', () => {
    expect(gradePinyin('xue2xi2', 'xuéxí').ok).toBe(true)
    expect(gradePinyin('xuexi', 'xuéxí').ok).toBe(true)
    expect(gradePinyin('xue2xi', 'xuéxí').verdict).toBe('partial-tones')
    expect(gradePinyin('xue1xi2', 'xuéxí').verdict).toBe('wrong-tones')
  })

  it('accepts v or u for u-diaeresis', () => {
    expect(gradePinyin('lv4', 'lǜ').ok).toBe(true)
    expect(gradePinyin('lu4', 'lǜ').ok).toBe(true)
  })

  it('always reports the correct answer for the reveal', () => {
    const r = gradePinyin('sai', 'nǐ hǎo')
    expect(r.expectedMarked).toBe('nǐ hǎo')
    expect(r.expectedNumbered).toBe('ni3hao3')
  })
})

describe('syllableTones', () => {
  it('attaches each tone mark to its own syllable', () => {
    expect(syllableTones('xué xí')).toEqual([
      { text: 'xue', tone: 2 },
      { text: 'xi', tone: 2 },
    ])
  })

  it('leaves an unmarked syllable unknown', () => {
    expect(syllableTones('māma')).toEqual([
      { text: 'ma', tone: 1 },
      { text: 'ma', tone: null },
    ])
  })
})
