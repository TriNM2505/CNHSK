import type { RoleCode } from './types'

export type RoleArea = {
  label: string
  role: RoleCode
  status: 'available' | 'under-construction'
  to?: string
}

const ROLE_AREAS: Readonly<Record<RoleCode, RoleArea>> = {
  LEARNER: {
    label: 'Học tập',
    role: 'LEARNER',
    status: 'available',
    to: '/learn/topics',
  },
  TEACHER: {
    label: 'Giáo viên',
    role: 'TEACHER',
    status: 'under-construction',
  },
  MODERATOR: {
    label: 'Kiểm duyệt',
    role: 'MODERATOR',
    status: 'under-construction',
  },
  CONTENT_EDITOR: {
    label: 'Nội dung',
    role: 'CONTENT_EDITOR',
    status: 'available',
    to: '/content/imports',
  },
  ACCOUNTANT: {
    label: 'Kế toán',
    role: 'ACCOUNTANT',
    status: 'under-construction',
  },
  ADMIN: {
    label: 'Quản trị',
    role: 'ADMIN',
    status: 'available',
    to: '/admin/users',
  },
}

export function getRoleAreas(roles: readonly RoleCode[]): RoleArea[] {
  return [...new Set(roles)].map((role) => ROLE_AREAS[role])
}

export function getAvailableRoleAreas(roles: readonly RoleCode[]): RoleArea[] {
  return getRoleAreas(roles).filter((area) => area.status === 'available')
}
