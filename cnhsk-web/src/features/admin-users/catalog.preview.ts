import { createElement } from 'react'

import type { PreviewEntry } from '@/app/preview/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { AdminLayout } from '@/shared/layout'
import { previewAdministrator } from '@/shared/layout/Layout.preview'

import { UserListView } from './components/UserListView'
import { userListPreviews } from './components/UserListView.preview'
import { UserDetailView } from './components/UserDetailView'
import { userDetailPreviews } from './components/UserDetailView.preview'

const userListEntries = [
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

const userDetailEntries = [
  ['scr-077-ready', 'Chi tiết tài khoản — Có dữ liệu', userDetailPreviews.ready],
  ['scr-077-loading', 'Chi tiết tài khoản — Đang tải', userDetailPreviews.loading],
  ['scr-077-empty', 'Chi tiết tài khoản — Không tìm thấy', userDetailPreviews.empty],
  ['scr-077-error', 'Chi tiết tài khoản — Lỗi tải', userDetailPreviews.error],
  ['scr-077-ban-confirm', 'Chi tiết tài khoản — Xác nhận cấm', userDetailPreviews.banConfirm],
  ['scr-077-banned', 'Chi tiết tài khoản — Đã bị cấm', userDetailPreviews.banned],
] as const

const administratorAuth: AuthContextValue = {
  error: null,
  reloadCurrentUser: async () => undefined,
  setAuthenticatedUser: () => undefined,
  signOut: async () => undefined,
  status: 'authenticated',
  user: previewAdministrator,
}

export const previews = [
  ...userListEntries.map(([id, title, props]) => ({
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
  })),
  ...userDetailEntries.map(([id, title, props]) => ({
    fullFrame: true,
    group: 'screen' as const,
    id,
    render: () =>
      createElement(
        AuthContext.Provider,
        { value: administratorAuth },
        createElement(AdminLayout, null, createElement(UserDetailView, props)),
      ),
    scr: 'SCR-077',
    title,
  })),
] satisfies readonly PreviewEntry[]
