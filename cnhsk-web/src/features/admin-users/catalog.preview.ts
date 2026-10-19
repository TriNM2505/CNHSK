import { createElement } from 'react'

import type { PreviewEntry } from '@/app/preview/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { AdminLayout } from '@/shared/layout'
import { previewAdministrator } from '@/shared/layout/Layout.preview'

import { UserListView } from './components/UserListView'
import { userListPreviews } from './components/UserListView.preview'

const entries = [
  ['scr-076-ready', 'Quản lý tài khoản — Có dữ liệu', userListPreviews.ready],
  ['scr-076-loading', 'Quản lý tài khoản — Đang tải', userListPreviews.loading],
  ['scr-076-empty', 'Quản lý tài khoản — Chưa có tài khoản', userListPreviews.empty],
  [
    'scr-076-filtered-empty',
    'Quản lý tài khoản — Bộ lọc không có kết quả',
    userListPreviews.filteredEmpty,
  ],
  ['scr-076-out-of-range', 'Quản lý tài khoản — Trang vượt phạm vi', userListPreviews.outOfRange],
  ['scr-076-error', 'Quản lý tài khoản — Lỗi tải', userListPreviews.error],
] as const

const administratorAuth: AuthContextValue = {
  error: null,
  reloadCurrentUser: async () => undefined,
  setAuthenticatedUser: () => undefined,
  signOut: async () => undefined,
  status: 'authenticated',
  user: previewAdministrator,
}

export const previews = entries.map(([id, title, props]) => ({
  fullFrame: true,
  group: 'screen' as const,
  id,
  render: () =>
    createElement(
      AuthContext.Provider,
      { value: administratorAuth },
      createElement(AdminLayout, null, createElement(UserListView, props)),
    ),
  scr: 'SCR-076',
  title,
})) satisfies readonly PreviewEntry[]
