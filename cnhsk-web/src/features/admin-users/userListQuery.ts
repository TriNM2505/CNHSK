import type { RoleCode } from '@/features/auth/types'

import type { UserStatus } from './types'

export const ADMIN_USER_PAGE_SIZE = 20
export const ADMIN_USER_SEARCH_DEBOUNCE_MS = 300

export interface AdminUserListQuery {
  page: number
  q: string
  role: RoleCode | ''
  status: UserStatus | ''
}

const ROLE_CODES: readonly RoleCode[] = [
  'LEARNER',
  'TEACHER',
  'MODERATOR',
  'CONTENT_EDITOR',
  'ACCOUNTANT',
  'ADMIN',
]
const USER_STATUSES: readonly UserStatus[] = ['ACTIVE', 'UNVERIFIED', 'BANNED']
const QUERY_KEYS = ['q', 'role', 'status', 'page'] as const

function isRoleCode(value: string): value is RoleCode {
  return ROLE_CODES.includes(value as RoleCode)
}

function isUserStatus(value: string): value is UserStatus {
  return USER_STATUSES.includes(value as UserStatus)
}

function parsePage(value: string | null): number {
  if (!value || !/^\d+$/.test(value)) return 0
  const page = Number(value)
  return Number.isSafeInteger(page) ? page : 0
}

export function toAdminUserSearchParams(
  query: AdminUserListQuery,
  current?: URLSearchParams,
): URLSearchParams {
  const result = new URLSearchParams(current)
  QUERY_KEYS.forEach((key) => result.delete(key))
  if (query.q) result.set('q', query.q)
  if (query.role) result.set('role', query.role)
  if (query.status) result.set('status', query.status)
  if (query.page > 0) result.set('page', String(query.page))
  return result
}

export function parseAdminUserSearchParams(searchParams: URLSearchParams): {
  canonical: URLSearchParams
  needsCleanup: boolean
  query: AdminUserListQuery
} {
  const rawQ = searchParams.get('q')
  const trimmedQ = rawQ?.trim() ?? ''
  const q = trimmedQ.length >= 2 && trimmedQ.length <= 100 ? trimmedQ : ''
  const rawRole = searchParams.get('role') ?? ''
  const role: RoleCode | '' = isRoleCode(rawRole) ? rawRole : ''
  const rawStatus = searchParams.get('status') ?? ''
  const status: UserStatus | '' = isUserStatus(rawStatus) ? rawStatus : ''
  const page = parsePage(searchParams.get('page'))
  const query: AdminUserListQuery = { page, q, role, status }
  const canonical = toAdminUserSearchParams(query, searchParams)

  return {
    canonical,
    needsCleanup: canonical.toString() !== searchParams.toString(),
    query,
  }
}

export function hasAdminUserFilters(query: AdminUserListQuery): boolean {
  return Boolean(query.q || query.role || query.status)
}

const createdAtFormatter = new Intl.DateTimeFormat('vi-VN', {
  dateStyle: 'short',
  timeZone: 'Asia/Ho_Chi_Minh',
})

export function formatAdminUserCreatedAt(value: string): string {
  return createdAtFormatter.format(new Date(value))
}
