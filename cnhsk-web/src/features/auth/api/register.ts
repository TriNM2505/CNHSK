import { apiRequest } from '@/shared/api'

import type { RegisterRequest, RegisterResponse } from '../types'

export function register(data: RegisterRequest): Promise<RegisterResponse> {
  return apiRequest<RegisterResponse>('/api/auth/register', {
    body: data,
    method: 'POST',
  })
}
