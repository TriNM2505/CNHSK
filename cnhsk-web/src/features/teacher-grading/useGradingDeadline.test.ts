import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { deadlineLabel, useGradingDeadline } from './useGradingDeadline'

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2035-11-15T00:00:00Z'))
})
afterEach(() => vi.useRealTimers())
describe('grading deadline display', () => {
  it('counts down against the absolute deadline, clamps to zero and clears its timer', () => {
    const { result, unmount } = renderHook(() => useGradingDeadline('2035-11-15T00:01:01Z'))
    expect(result.current).toBe(61)
    act(() => vi.advanceTimersByTime(1000))
    expect(result.current).toBe(60)
    act(() => vi.advanceTimersByTime(65000))
    expect(result.current).toBe(0)
    expect(deadlineLabel(result.current)).toBe('Hết hạn chấm')
    unmount()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('replaces the old interval on deadline changes and stops when no longer assigned', () => {
    const { result, rerender } = renderHook(
      ({ deadline }: { deadline?: string }) => useGradingDeadline(deadline),
      { initialProps: { deadline: '2035-11-15T00:01:00Z' } as { deadline?: string } },
    )
    act(() => vi.advanceTimersByTime(10000))
    rerender({ deadline: '2035-11-15T00:02:00Z' })
    expect(result.current).toBe(110)
    expect(vi.getTimerCount()).toBe(1)
    rerender({ deadline: undefined })
    expect(result.current).toBeNull()
    expect(vi.getTimerCount()).toBe(0)
    expect(deadlineLabel(null)).toBe('—')
    expect(deadlineLabel(3661)).toBe('1 giờ 1 phút 1 giây')
  })
})
