import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from '@/shared/api'

import { getTopic, getTopics, startTopicStudySession } from './topics'

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

  it('tải chi tiết chủ đề và bắt đầu lượt học từ mới', async () => {
    const controller = new AbortController()

    await getTopic(42, controller.signal)
    await startTopicStudySession(42)

    expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/topics/42', {
      signal: controller.signal,
    })
    expect(apiRequestMock).toHaveBeenNthCalledWith(2, '/api/topics/42/study-sessions', {
      method: 'POST',
    })
  })
})
