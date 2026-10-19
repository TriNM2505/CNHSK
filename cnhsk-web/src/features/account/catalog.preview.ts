import { createElement } from 'react'

import type { PreviewEntry } from '@/app/preview/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { LearnerLayout } from '@/shared/layout'
import { previewLearner } from '@/shared/layout/Layout.preview'

import { changePasswordPreviews, profilePreviews } from './components/AccountViews.preview'
import { ChangePasswordView } from './components/ChangePasswordView'
import { ProfileView } from './components/ProfileView'

const profileEntries = [
  ['scr-007-default', 'Hồ sơ — Mặc định', profilePreviews.default],
  ['scr-007-loading', 'Hồ sơ — Đang tải', profilePreviews.loading],
  ['scr-007-load-error', 'Hồ sơ — Lỗi tải', profilePreviews.loadError],
  ['scr-007-unverified', 'Hồ sơ — Chưa xác thực email', profilePreviews.unverified],
  ['scr-007-saving', 'Hồ sơ — Đang lưu', profilePreviews.saving],
  ['scr-007-saved', 'Hồ sơ — Đã lưu', profilePreviews.saved],
  ['scr-007-validation', 'Hồ sơ — Lỗi trường', profilePreviews.validation],
  ['scr-007-save-error', 'Hồ sơ — Lỗi hệ thống', profilePreviews.saveError],
] as const

const changePasswordEntries = [
  ['scr-008-default', 'Đổi mật khẩu — Mặc định', changePasswordPreviews.default],
  ['scr-008-submitting', 'Đổi mật khẩu — Đang gửi', changePasswordPreviews.submitting],
  ['scr-008-validation', 'Đổi mật khẩu — Lỗi phía khách', changePasswordPreviews.validation],
  [
    'scr-008-wrong-current',
    'Đổi mật khẩu — Sai mật khẩu hiện tại 422',
    changePasswordPreviews.wrongCurrent,
  ],
  ['scr-008-success', 'Đổi mật khẩu — Thành công', changePasswordPreviews.success],
  ['scr-008-generic-error', 'Đổi mật khẩu — Lỗi hệ thống', changePasswordPreviews.genericError],
] as const

const learnerAuth: AuthContextValue = {
  error: null,
  reloadCurrentUser: async () => undefined,
  setAuthenticatedUser: () => undefined,
  signOut: async () => undefined,
  status: 'authenticated',
  user: previewLearner,
}

export const previews = [
  ...profileEntries.map(([id, title, props]) => ({
    fullFrame: true,
    group: 'screen' as const,
    id,
    render: () =>
      createElement(
        AuthContext.Provider,
        { value: learnerAuth },
        createElement(LearnerLayout, null, createElement(ProfileView, props)),
      ),
    scr: 'SCR-007',
    title,
  })),
  ...changePasswordEntries.map(([id, title, props]) => ({
    fullFrame: true,
    group: 'screen' as const,
    id,
    render: () =>
      createElement(
        AuthContext.Provider,
        { value: learnerAuth },
        createElement(LearnerLayout, null, createElement(ChangePasswordView, props)),
      ),
    scr: 'SCR-008',
    title,
  })),
] satisfies readonly PreviewEntry[]
