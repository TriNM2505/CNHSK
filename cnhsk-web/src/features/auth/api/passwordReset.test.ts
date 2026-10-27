import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from '@/shared/api'

import { requestPasswordReset } from './passwordReset'

vi.mock('@/shared/api', () => ({ apiRequest: vi.fn() }))

describe('requestPasswordReset', () => {
  beforeEach(() => {
    vi.mocked(apiRequest).mockReset()
  })

  it('returns the OpenAPI 202 message response from the forgot-password endpoint', async () => {
    const response = {
      message: 'Nếu email có tài khoản, hướng dẫn sẽ được gửi.',
    }
    vi.mocked(apiRequest).mockResolvedValue(response)

    await expect(requestPasswordReset('learner@example.com')).resolves.toEqual(response)
    expect(apiRequest).toHaveBeenCalledWith('/api/auth/forgot-password', {
      body: { email: 'learner@example.com' },
      method: 'POST',
    })
  })
})
