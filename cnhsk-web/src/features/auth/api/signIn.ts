import { apiRequest } from '@/shared/api'

import type { LoginRequest, LoginResponse } from '../types'

export function signIn(credentials: LoginRequest): Promise<LoginResponse> {
  return apiRequest<LoginResponse>('/api/auth/login', {
    body: credentials,
    method: 'POST',
  })
}
