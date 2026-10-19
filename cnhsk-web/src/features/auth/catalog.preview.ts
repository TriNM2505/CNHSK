import { createElement } from 'react'

import type { PreviewEntry } from '@/app/preview/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { PublicLayout } from '@/shared/layout'

import { SignInView } from './components/SignInView'
import { signInPreviews } from './components/SignInView.preview'
import { RegisterView } from './components/RegisterView'
import { registerPreviews } from './components/RegisterView.preview'

const signInEntries = [
  ['scr-002-default', 'Đăng nhập — Mặc định', signInPreviews.default],
  ['scr-002-submitting', 'Đăng nhập — Đang gửi', signInPreviews.submitting],
  ['scr-002-invalid-credentials', 'Đăng nhập — Lỗi 401', signInPreviews.invalidCredentials],
  ['scr-002-blocked', 'Đăng nhập — Bị chặn 423', signInPreviews.blocked],
  ['scr-002-banned', 'Đăng nhập — Bị cấm 403', signInPreviews.banned],
  ['scr-002-field-errors', 'Đăng nhập — Lỗi trường 422', signInPreviews.fieldErrors],
] as const

const registerEntries = [
  ['scr-003-default', 'Đăng ký — Mặc định', registerPreviews.default],
  ['scr-003-submitting', 'Đăng ký — Đang gửi', registerPreviews.submitting],
  ['scr-003-client-validation', 'Đăng ký — Lỗi phía khách', registerPreviews.clientValidation],
  ['scr-003-email-exists', 'Đăng ký — Email đã tồn tại 409', registerPreviews.emailExists],
  ['scr-003-field-errors', 'Đăng ký — Lỗi trường 422', registerPreviews.fieldErrors],
  ['scr-003-generic-error', 'Đăng ký — Lỗi hệ thống', registerPreviews.genericError],
] as const

const guestAuth: AuthContextValue = {
  error: null,
  reloadCurrentUser: async () => undefined,
  setAuthenticatedUser: () => undefined,
  signOut: async () => undefined,
  status: 'guest',
  user: null,
}

export const previews = [
  ...signInEntries.map(([id, title, props]) => ({
    fullFrame: true,
    group: 'screen' as const,
    id,
    render: () =>
      createElement(
        AuthContext.Provider,
        { value: guestAuth },
        createElement(PublicLayout, null, createElement(SignInView, props)),
      ),
    scr: 'SCR-002',
    title,
  })),
  ...registerEntries.map(([id, title, props]) => ({
    fullFrame: true,
    group: 'screen' as const,
    id,
    render: () =>
      createElement(
        AuthContext.Provider,
        { value: guestAuth },
        createElement(PublicLayout, null, createElement(RegisterView, props)),
      ),
    scr: 'SCR-003',
    title,
  })),
] satisfies readonly PreviewEntry[]
