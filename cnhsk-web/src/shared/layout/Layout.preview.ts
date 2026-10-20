import type { CurrentUser } from '@/features/auth/types'

export const previewLearner: CurrentUser = {
  id: 101,
  displayName: 'Minh Anh',
  email: 'mi•••@example.com',
  emailVerified: true,
  hskLevel: 4,
  roles: ['LEARNER'],
}

export const previewUnverifiedLearner: CurrentUser = {
  ...previewLearner,
  emailVerified: false,
}

export const previewContentEditor: CurrentUser = {
  id: 102,
  displayName: 'Biên tập viên',
  email: 'bi•••@example.com',
  emailVerified: true,
  roles: ['CONTENT_EDITOR'],
}

export const previewAdministrator: CurrentUser = {
  id: 103,
  displayName: 'Quản trị viên',
  email: 'qu•••@example.com',
  emailVerified: true,
  roles: ['ADMIN'],
}

export const previewMultiAreaUser: CurrentUser = {
  ...previewLearner,
  displayName: 'Người dùng nhiều khu vực',
  roles: ['LEARNER', 'CONTENT_EDITOR', 'ADMIN'],
}
