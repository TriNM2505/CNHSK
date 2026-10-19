import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from '@/shared/api'

import { getGrammarLesson, getGrammarPoints, startGrammarLesson } from './grammar'

vi.mock('@/shared/api', () => ({ apiRequest: vi.fn() }))

const apiRequestMock = vi.mocked(apiRequest)

describe('grammar API', () => {
  beforeEach(() => apiRequestMock.mockReset().mockResolvedValue([]))

  it('tải toàn bộ điểm ngữ pháp mà không thêm tham số', async () => {
    const controller = new AbortController()
    await getGrammarPoints(undefined, controller.signal)

    expect(apiRequestMock).toHaveBeenCalledWith('/api/grammar', { signal: controller.signal })
  })

  it('lọc đúng một cấp khi tìm điểm tiếp theo', async () => {
    const controller = new AbortController()
    await getGrammarPoints(8, controller.signal)

    expect(apiRequestMock).toHaveBeenCalledWith('/api/grammar?hskLevel=8', {
      signal: controller.signal,
    })
  })

  it('tải chi tiết và gửi thời gian đọc khi bắt đầu bài', async () => {
    const controller = new AbortController()
    await getGrammarLesson(42, controller.signal)
    await startGrammarLesson(42, 1234)

    expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/grammar/42', {
      signal: controller.signal,
    })
    expect(apiRequestMock).toHaveBeenNthCalledWith(2, '/api/grammar/42/lessons', {
      body: { readDurationMs: 1234 },
      method: 'POST',
    })
  })
})
