import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from '@/shared/api'

import { getPronunciationStages, startPronunciationStageSession } from './pronunciation'

vi.mock('@/shared/api', () => ({ apiRequest: vi.fn() }))

const apiRequestMock = vi.mocked(apiRequest)

describe('pronunciation API', () => {
  beforeEach(() => apiRequestMock.mockReset().mockResolvedValue([]))

  it('tải danh sách tầng đúng một lần, không kèm tham số', async () => {
    const controller = new AbortController()
    await getPronunciationStages(controller.signal)

    expect(apiRequestMock).toHaveBeenCalledWith('/api/pronunciation/stages', {
      signal: controller.signal,
    })
  })

  it('bắt đầu lượt theo stageIndex', async () => {
    await startPronunciationStageSession(3)

    expect(apiRequestMock).toHaveBeenCalledWith('/api/pronunciation/stages/3/sessions', {
      method: 'POST',
    })
  })
})
