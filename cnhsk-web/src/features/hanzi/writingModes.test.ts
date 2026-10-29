import { describe, expect, it } from 'vitest'

import type { CharacterDetail } from './types'
import { defaultWritingMode, writingModeAvailability } from './writingModes'

function character(writing: Partial<CharacterDetail['writing']>): CharacterDetail {
  return {
    audioUrl: null,
    hasAudio: false,
    hskLevel: 1,
    id: 1,
    meaning: 'tốt',
    pinyin: 'hǎo',
    radical: '女',
    sinoViet: 'hảo',
    strokeCount: 6,
    text: '好',
    writing: { dictationAvailable: false, recallUnlocked: false, strokeAvailable: true, ...writing },
  }
}

describe('writingModeAvailability (FR-029)', () => {
  it('khoá mọi chế độ khi chữ chưa có dữ liệu nét', () => {
    const noStroke = character({ strokeAvailable: false })
    for (const mode of ['STROKE', 'RECALL', 'DICTATION'] as const) {
      expect(writingModeAvailability(noStroke, mode)).toEqual({
        available: false,
        reason: 'Chữ chưa có dữ liệu nét',
      })
    }
    expect(defaultWritingMode(noStroke)).toBeUndefined()
  })

  it('nhớ rồi viết cần viết theo nét trước, nghe chép cần âm thanh', () => {
    const fresh = character({})
    expect(writingModeAvailability(fresh, 'STROKE').available).toBe(true)
    expect(writingModeAvailability(fresh, 'RECALL').reason).toBe('Viết theo nét trước')
    expect(writingModeAvailability(fresh, 'DICTATION').reason).toBe('Chữ chưa có âm thanh')
    expect(defaultWritingMode(fresh)).toBe('STROKE')
  })

  it('mở cả ba chế độ khi đủ điều kiện', () => {
    const ready = character({ dictationAvailable: true, recallUnlocked: true })
    expect(writingModeAvailability(ready, 'RECALL')).toEqual({ available: true })
    expect(writingModeAvailability(ready, 'DICTATION')).toEqual({ available: true })
  })
})
