import type { RoleCode } from '@/features/auth/types'

export type UserStatus = 'ACTIVE' | 'UNVERIFIED' | 'BANNED'

export interface AdminUserSummary {
  id: number
  emailMasked: string
  displayName: string
  roles: RoleCode[]
  status: UserStatus
  createdAt: string
}

export interface AdminUserPage {
  items: AdminUserSummary[]
  page: number
  size: number
  totalElements: number
}

export interface AdminUserListParams {
  page: number
  q?: string
  role?: RoleCode
  size: number
  status?: UserStatus
}

export interface AdminUserRoleDetail {
  code: RoleCode
  grantedBy: number | null
  grantedAt: string
}

export interface AdminUserDetail {
  id: number
  email: string
  displayName: string
  roles: AdminUserRoleDetail[]
  status: UserStatus
  bannedAt?: string | null
  banReason?: string | null
  emailVerifiedAt?: string | null
  createdAt: string
}

export interface GrantRoleRequest {
  roleCode: RoleCode
}

export interface BanRequest {
  reason: string
}
