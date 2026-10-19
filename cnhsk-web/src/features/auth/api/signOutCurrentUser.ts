import { apiRequest } from '@/shared/api'

export function signOutCurrentUser(): Promise<void> {
  return apiRequest<void>('/api/auth/logout', { method: 'POST' })
}
