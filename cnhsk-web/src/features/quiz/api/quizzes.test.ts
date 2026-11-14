import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '@/shared/api'
import { listQuizzes, startQuizAttempt } from './quizzes'
import { quizSession } from '../test-fixtures'

vi.mock('@/shared/api', () => ({ apiRequest: vi.fn() }))

describe('quiz API contract', () => {
  beforeEach(() => vi.mocked(apiRequest).mockReset())
  it('uses catalog defaults, zero-based pagination, optional HSK 1–9 and AbortSignal', async () => {
    const controller = new AbortController()
    await listQuizzes()
    await listQuizzes({ hsk: 9, page: 2, size: 50 }, controller.signal)
    expect(apiRequest).toHaveBeenNthCalledWith(1, '/api/quizzes?page=0&size=20', {
      signal: undefined,
    })
    expect(apiRequest).toHaveBeenNthCalledWith(2, '/api/quizzes?page=2&size=50&hsk=9', {
      signal: controller.signal,
    })
  })
  it('POSTs without a body and preserves the server session for create or resume', async () => {
    vi.mocked(apiRequest).mockResolvedValue(quizSession)
    expect(await startQuizAttempt(43)).toBe(quizSession)
    expect(apiRequest).toHaveBeenCalledWith('/api/quizzes/43/attempts', { method: 'POST' })
  })
  it.each([0, -1, 1.5, Number.MAX_SAFE_INTEGER + 1])(
    'rejects unsafe topic identifier %s before sending',
    (id) => {
      expect(() => startQuizAttempt(id)).toThrow('Mã chủ đề không hợp lệ.')
      expect(apiRequest).not.toHaveBeenCalled()
    },
  )
})
