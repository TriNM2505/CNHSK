import { describe, expect, it } from 'vitest'
import {
  createCountdown,
  formatCountdown,
  formatVietnamDate,
  remainingMilliseconds,
} from './countdown'

describe('contest server clock', () => {
  it('FR016_AT20_uses the server interval and monotonic elapsed time', () => {
    const anchor = createCountdown('2026-11-14T12:20:00Z', '2026-11-14T12:00:00Z', 100)
    expect(remainingMilliseconds(anchor, 100)).toBe(1_200_000)
    expect(remainingMilliseconds(anchor, 3100)).toBe(1_197_000)
    expect(remainingMilliseconds(anchor, 1_300_100)).toBe(0)
    expect(remainingMilliseconds(anchor, 0)).toBe(1_200_000)
  })

  it('FR016_rejects invalid server timestamps instead of starting a false timer', () => {
    expect(() => createCountdown('invalid', '2026-11-14T12:00:00Z')).toThrow()
    expect(
      remainingMilliseconds(createCountdown('2026-11-14T11:59:00Z', '2026-11-14T12:00:00Z')),
    ).toBe(0)
  })

  it('FR015_formats Vietnamese time and never displays a negative clock', () => {
    expect(formatVietnamDate('2026-11-14T12:00:00Z')).toContain('19:00')
    expect(formatVietnamDate('2026-11-14T12:00:00Z')).toContain('14/11/2026')
    expect(formatCountdown(1001)).toBe('00:02')
    expect(formatCountdown(-1000)).toBe('00:00')
    expect(formatCountdown(20 * 60_000)).toBe('20:00')
  })
})
