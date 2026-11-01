import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from '@/shared/api'

import { recordWritingAttempt } from './characters'

vi.mock('@/shared/api', () => ({ apiRequest: vi.fn() }))

const apiRequestMock = vi.mocked(apiRequest)

describe('recordWritingAttempt', () => {
  beforeEach(() => apiRequestMock.mockReset())

  it('FR010_postsModeEvidenceAndReturnsServerResult', async () => {
    const request = {
      accuracy: 0.83,
      completed: true,
      durationMs: 4200,
      lexemeId: 11,
      mode: 'STROKE' as const,
      strokeMisses: 1,
      usedAnimation: true,
    }
    const response = { attemptId: 17, counted: false, recorded: true, recallUnlocked: true }
    apiRequestMock.mockResolvedValue(response)

    await expect(recordWritingAttempt(request)).resolves.toEqual(response)
    expect(apiRequestMock).toHaveBeenCalledWith('/api/writing/attempts', {
      body: request,
      method: 'POST',
    })
  })
})
