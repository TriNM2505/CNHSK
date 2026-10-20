import { apiRequest } from '@/shared/api'

import type { ChangePasswordRequest, Profile, ProfilePatch } from '../types'

export function getProfile(signal?: AbortSignal): Promise<Profile> {
  return apiRequest<Profile>('/api/me/profile', { signal })
}

export function updateProfile(body: ProfilePatch): Promise<Profile> {
  return apiRequest<Profile>('/api/me/profile', { body, method: 'PATCH' })
}

/** FR-043, FR-044: thành công trả 204; các thiết bị khác bị đăng xuất. */
export function changePassword(body: ChangePasswordRequest): Promise<void> {
  return apiRequest<void>('/api/me/password', { body, method: 'POST' })
}
