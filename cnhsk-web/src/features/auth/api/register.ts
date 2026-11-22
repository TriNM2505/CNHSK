import { apiRequest } from '@/shared/api'

import type {
  RegisterCodeResponse,
  RegisterRequest,
  RegisterResponse,
  RegisterWithCodeRequest,
} from '../types'

export function sendRegisterCode(data: RegisterRequest): Promise<RegisterCodeResponse> {
  return apiRequest<RegisterCodeResponse>('/api/auth/register/code', {
    body: data,
    method: 'POST',
  })
}

export function register(data: RegisterWithCodeRequest): Promise<RegisterResponse> {
  return apiRequest<RegisterResponse>('/api/auth/register', {
    body: data,
    method: 'POST',
  })
}
