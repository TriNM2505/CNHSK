import type { RoleCode } from '@/features/auth/types'

import type { UserStatus } from './types'

// Nguồn chính thức: constitution §bảng role (dòng 138–144), ưu tiên cao hơn spec 001.
export const ROLE_LABELS: Record<RoleCode, string> = {
  LEARNER: 'Người học',
  TEACHER: 'Giáo viên',
  MODERATOR: 'Kiểm duyệt viên',
  CONTENT_EDITOR: 'Biên tập viên nội dung',
  ACCOUNTANT: 'Kế toán',
  ADMIN: 'Quản trị viên',
}

export const ROLE_OPTIONS = Object.entries(ROLE_LABELS) as [RoleCode, string][]

export const USER_STATUS_LABELS: Record<UserStatus, string> = {
  ACTIVE: 'Hoạt động',
  UNVERIFIED: 'Chưa xác minh',
  BANNED: 'Bị cấm',
}
