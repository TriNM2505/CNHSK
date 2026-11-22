import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from '@/shared/api'

import { register, sendRegisterCode } from './register'

vi.mock('@/shared/api', () => ({ apiRequest: vi.fn() }))

describe('register', () => {
  beforeEach(() => {
    vi.mocked(apiRequest).mockReset()
  })

  it('gửi mã tới đúng endpoint OpenAPI', async () => {
    const request = {
      acceptTerms: true,
      confirmPassword: 'HocTieng1',
      displayName: 'Nguyễn An',
      email: 'learner@example.com',
      password: 'HocTieng1',
    }
    await sendRegisterCode(request)
    expect(apiRequest).toHaveBeenCalledWith('/api/auth/register/code', {
      body: request,
      method: 'POST',
    })
  })

  it('gửi đủ sáu trường đến đúng endpoint OpenAPI', async () => {
    const request = {
      acceptTerms: true,
      confirmPassword: 'HocTieng1',
      displayName: 'Nguyễn An',
      email: 'learner@example.com',
      password: 'HocTieng1',
      code: '123456',
    }
    vi.mocked(apiRequest).mockResolvedValue({
      email: request.email,
      emailVerified: true,
      userId: 1,
    })

    await register(request)

    expect(apiRequest).toHaveBeenCalledWith('/api/auth/register', {
      body: request,
      method: 'POST',
    })
  })
})
