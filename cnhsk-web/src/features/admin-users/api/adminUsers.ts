import { apiRequest } from '@/shared/api'

import type { AdminUserListParams, AdminUserPage } from '../types'

export function getAdminUsers(
  { page, q, role, size, status }: AdminUserListParams,
  signal?: AbortSignal,
): Promise<AdminUserPage> {
  const searchParams = new URLSearchParams()
  if (q) searchParams.set('q', q)
  if (role) searchParams.set('role', role)
  if (status) searchParams.set('status', status)
  searchParams.set('page', String(page))
  searchParams.set('size', String(size))

  return apiRequest<AdminUserPage>(`/api/admin/users?${searchParams.toString()}`, { signal })
}
