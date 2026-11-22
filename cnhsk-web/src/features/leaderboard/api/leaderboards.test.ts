import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from '@/shared/api'

import {
  getGameLeaderboard,
  getTopicLeaderboard,
  listLeaderboardGames,
  listLeaderboardTopics,
} from './leaderboards'

vi.mock('@/shared/api', () => ({ apiRequest: vi.fn() }))

describe('leaderboard API', () => {
  beforeEach(() => vi.clearAllMocks())

  it('calls topic catalog with optional HSK and abort signal', () => {
    const signal = new AbortController().signal
    listLeaderboardTopics(2, signal)
    expect(apiRequest).toHaveBeenCalledWith('/api/public/leaderboards/topics?hsk=2', { signal })
  })

  it('calls leaderboard endpoints with encoded selections and period', () => {
    const signal = new AbortController().signal
    getTopicLeaderboard(43, 'month', signal)
    getGameLeaderboard('CHAR_RAIN', 'all', 2, signal)
    listLeaderboardGames(signal)
    expect(apiRequest).toHaveBeenNthCalledWith(
      1,
      '/api/public/leaderboards/topics/43?period=month',
      { signal },
    )
    expect(apiRequest).toHaveBeenNthCalledWith(
      2,
      '/api/public/leaderboards/games/CHAR_RAIN?period=all&hskLevel=2',
      { signal },
    )
    expect(apiRequest).toHaveBeenNthCalledWith(3, '/api/public/leaderboards/games', { signal })
  })

  it('rejects invalid identifiers before making a request', () => {
    expect(() => getTopicLeaderboard(0, 'week')).toThrow('Mã chủ đề không hợp lệ.')
    expect(() => getGameLeaderboard('char_rain', 'week', 1)).toThrow('Mã game không hợp lệ.')
    expect(apiRequest).not.toHaveBeenCalled()
  })
})
