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
