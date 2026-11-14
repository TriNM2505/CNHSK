import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '@/shared/api'
import { getQuizTopTen } from './leaderboards'
import { quizLeaderboard } from '../test-fixtures'

vi.mock('@/shared/api', () => ({ apiRequest: vi.fn() }))
describe('quiz leaderboard adapter', () => {
  beforeEach(() => vi.mocked(apiRequest).mockReset())
  it('uses the exact public topic endpoint and all-time period without recalculating ranks', async () => {
    vi.mocked(apiRequest).mockResolvedValue(quizLeaderboard)
    const controller = new AbortController()
    expect(await getQuizTopTen(43, controller.signal)).toBe(quizLeaderboard)
    expect(apiRequest).toHaveBeenCalledWith('/api/public/leaderboards/topics/43?period=all', {
      signal: controller.signal,
    })
  })
  it.each([0, -1, 1.5, Number.MAX_SAFE_INTEGER + 1])(
    'rejects unsafe topic %s before network',
    (id) => {
      expect(() => getQuizTopTen(id)).toThrow()
      expect(apiRequest).not.toHaveBeenCalled()
    },
  )
})
