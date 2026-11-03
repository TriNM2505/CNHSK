import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from '@/shared/api'

import { getExamResult, getExams, getExamWeakPoints, startExamAttempt } from './exams'

vi.mock('@/shared/api', () => ({ apiRequest: vi.fn() }))

const apiRequestMock = vi.mocked(apiRequest)

describe('exam API', () => {
  beforeEach(() => apiRequestMock.mockReset().mockResolvedValue(undefined))

  it('FR001_listExams: đọc danh sách với cấp tùy chọn', async () => {
    const controller = new AbortController()
    await getExams(undefined, controller.signal)
    await getExams(3)

    expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/exams', { signal: controller.signal })
    expect(apiRequestMock).toHaveBeenNthCalledWith(2, '/api/exams?hskLevel=3', {
      signal: undefined,
    })
  })

  it('FR001_examEndpoints: bắt đầu lượt và đọc kết quả/điểm yếu theo session', async () => {
    const controller = new AbortController()
    await startExamAttempt(12)
    await getExamResult(34, controller.signal)
    await getExamWeakPoints(34)

    expect(apiRequestMock).toHaveBeenNthCalledWith(1, '/api/exams/12/attempts', { method: 'POST' })
    expect(apiRequestMock).toHaveBeenNthCalledWith(2, '/api/exam-attempts/34/result', {
      signal: controller.signal,
    })
    expect(apiRequestMock).toHaveBeenNthCalledWith(3, '/api/exam-attempts/34/weak-points', {
      signal: undefined,
    })
  })
})
