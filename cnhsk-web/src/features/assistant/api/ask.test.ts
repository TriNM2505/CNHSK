import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError, apiRequest } from '@/shared/api'

import { systemAnswer, knowledgeAnswer } from '../test-fixtures'
import { askAssistant } from './ask'

vi.mock('@/shared/api', async (original) => ({
  ...(await original<typeof import('@/shared/api')>()),
  apiRequest: vi.fn(),
}))

describe('assistant API contract', () => {
  beforeEach(() => {
    vi.mocked(apiRequest).mockReset()
  })

  it.each([systemAnswer, knowledgeAnswer])(
    'FR013_posts the contract body and preserves result',
    async (result) => {
      const controller = new AbortController()
      const body = {
        question: 'Tôi chọn chủ đề ở đâu?',
        history: [{ role: 'user' as const, text: 'Xin hướng dẫn.' }],
        pageContext: 'SCR-030' as const,
      }
      vi.mocked(apiRequest).mockResolvedValue(result)
      await expect(askAssistant(body, controller.signal)).resolves.toEqual(result)
      expect(apiRequest).toHaveBeenCalledWith('/api/assistant/ask', {
        body,
        method: 'POST',
        signal: controller.signal,
      })
    },
  )

  it.each([
    [401, 'UNAUTHENTICATED'],
    [403, 'ASSISTANT_BLOCKED_DURING_TEST'],
    [422, 'VALIDATION_FAILED'],
    [429, 'RATE_LIMITED'],
    [503, 'ASSISTANT_UNAVAILABLE'],
  ])('preserves HTTP %i for the panel error handling', async (status, errorCode) => {
    const error = new ApiError({
      status: Number(status),
      errorCode: String(errorCode),
      message: 'Lỗi thử nghiệm',
    })
    vi.mocked(apiRequest).mockRejectedValue(error)
    await expect(askAssistant({ question: 'Tôi mở chủ đề thế nào?' })).rejects.toBe(error)
  })
})
