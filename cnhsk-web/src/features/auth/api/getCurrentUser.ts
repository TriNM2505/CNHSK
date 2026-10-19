import { apiRequest } from '@/shared/api'

import type { CurrentUser } from '../types'

export function getCurrentUser(signal?: AbortSignal): Promise<CurrentUser> {
  return apiRequest<CurrentUser>('/api/auth/me', { signal })
}
