import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from '@/shared/api'

import {
  getPlacementResult,
  getPlacementSummary,
  nextPlacementBlock,
  startPlacementRun,
} from './placement'

vi.mock('@/shared/api', () => ({ apiRequest: vi.fn() }))

const apiRequestMock = vi.mocked(apiRequest)

describe('placement API', () => {
  beforeEach(() => apiRequestMock.mockReset().mockResolvedValue(undefined))

  it('FR001_placement: gọi summary, start, next và result đúng contract', async () => {
    const controller = new AbortController()
    await getPlacementSummary(controller.signal)
    await startPlacementRun({ startLevel: 2 })
    await nextPlacementBlock(51)
    await getPlacementResult(51)

    expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/placement/summary', {
      signal: controller.signal,
    })
    expect(apiRequestMock).toHaveBeenNthCalledWith(2, '/api/placement/runs', {
      body: { startLevel: 2 },
      method: 'POST',
    })
    expect(apiRequestMock).toHaveBeenNthCalledWith(3, '/api/placement/runs/51/next', {
      method: 'POST',
    })
    expect(apiRequestMock).toHaveBeenNthCalledWith(4, '/api/placement/runs/51/result', {
      signal: undefined,
    })
  })
})
