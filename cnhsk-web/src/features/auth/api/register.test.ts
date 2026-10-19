import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from '@/shared/api'

import { register } from './register'

vi.mock('@/shared/api', () => ({ apiRequest: vi.fn() }))

describe('register', () => {
  beforeEach(() => {
    vi.mocked(apiRequest).mockReset()
  })

  it('gửi đủ năm trường đến đúng endpoint OpenAPI', async () => {
    const request = {
      acceptTerms: true,
      confirmPassword: 'HocTieng1',
      displayName: 'Nguyễn An',
      email: 'learner@example.com',
      password: 'HocTieng1',
    }
    vi.mocked(apiRequest).mockResolvedValue({
      email: request.email,
      emailVerified: false,
      userId: 1,
    })

    await register(request)

    expect(apiRequest).toHaveBeenCalledWith('/api/auth/register', {
      body: request,
      method: 'POST',
    })
  })
})
