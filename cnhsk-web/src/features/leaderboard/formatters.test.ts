import { describe, expect, it } from 'vitest'

import { formatDuration, formatPeriodRange } from './formatters'

describe('leaderboard formatters', () => {
  it('formats duration as mm:ss and keeps null distinct from zero', () => {
    expect(formatDuration(125000)).toBe('2:05')
    expect(formatDuration(0)).toBe('0:00')
    expect(formatDuration(null)).toBe('—')
  })

  it('formats server period bounds in Vietnam time and all-time without fake dates', () => {
    expect(formatPeriodRange('2026-11-09T00:00:00+07:00', '2026-11-16T00:00:00+07:00')).toBe(
      '09/11 – 15/11',
    )
    expect(formatPeriodRange(null, null)).toBe('Toàn bộ thời gian')
  })
})
