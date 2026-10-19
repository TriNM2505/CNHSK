import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from '@/shared/api'

import { signIn } from './signIn'

vi.mock('@/shared/api', () => ({ apiRequest: vi.fn() }))

describe('signIn', () => {
  beforeEach(() => {
    vi.mocked(apiRequest).mockReset()
  })

  it('gọi đúng endpoint đăng nhập web theo OpenAPI', async () => {
    const credentials = { email: 'learner@example.com', password: 'secret' }
    vi.mocked(apiRequest).mockResolvedValue({ user: {} })

    await signIn(credentials)

    expect(apiRequest).toHaveBeenCalledWith('/api/auth/login', {
      body: credentials,
      method: 'POST',
    })
  })
})
