import { apiRequest } from '@/shared/api'
import type { RoleCode } from '@/features/auth/types'

import type {
  AdminUserDetail,
  AdminUserListParams,
  AdminUserPage,
  BanRequest,
  GrantRoleRequest,
} from '../types'

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

export function getAdminUserDetail(id: number, signal?: AbortSignal): Promise<AdminUserDetail> {
  return apiRequest<AdminUserDetail>(`/api/admin/users/${id}`, { signal })
}

export function grantAdminUserRole(id: number, body: GrantRoleRequest): Promise<void> {
  return apiRequest<void>(`/api/admin/users/${id}/roles`, { body, method: 'POST' })
}

export function revokeAdminUserRole(id: number, roleCode: RoleCode): Promise<void> {
  return apiRequest<void>(`/api/admin/users/${id}/roles/${encodeURIComponent(roleCode)}`, {
    method: 'DELETE',
  })
}

export function banAdminUser(id: number, body: BanRequest): Promise<void> {
  return apiRequest<void>(`/api/admin/users/${id}/ban`, { body, method: 'POST' })
}

export function unbanAdminUser(id: number): Promise<void> {
  return apiRequest<void>(`/api/admin/users/${id}/ban`, { method: 'DELETE' })
}
