import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from '@/shared/api'

import { getTopics } from './topics'

vi.mock('@/shared/api', () => ({ apiRequest: vi.fn() }))

const apiRequestMock = vi.mocked(apiRequest)

describe('topics API', () => {
  beforeEach(() => apiRequestMock.mockReset().mockResolvedValue([]))

  it('tải tất cả chủ đề khi chưa chọn cấp HSK', async () => {
    const controller = new AbortController()
    await getTopics(undefined, controller.signal)

    expect(apiRequestMock).toHaveBeenCalledWith('/api/topics', { signal: controller.signal })
  })

  it('gửi cấp HSK lên server', async () => {
    const controller = new AbortController()
    await getTopics(9, controller.signal)

    expect(apiRequestMock).toHaveBeenCalledWith('/api/topics?hskLevel=9', {
      signal: controller.signal,
    })
  })
})
