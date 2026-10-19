import { createElement } from 'react'

import type { PreviewEntry } from '@/app/preview/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { PublicLayout } from '@/shared/layout'

import { SignInView } from './components/SignInView'
import { signInPreviews } from './components/SignInView.preview'
import { RegisterView } from './components/RegisterView'
import { registerPreviews } from './components/RegisterView.preview'
import { ForgotPasswordView } from './components/ForgotPasswordView'
import {
  forgotPasswordPreviews,
  resetPasswordPreviews,
} from './components/PasswordResetViews.preview'
import { ResetPasswordView } from './components/ResetPasswordView'
import { VerifyEmailView } from './components/VerifyEmailView'
import { verifyEmailPreviews } from './components/VerifyEmailView.preview'

const signInEntries = [
  ['scr-002-default', 'Đăng nhập — Mặc định', signInPreviews.default],
  ['scr-002-submitting', 'Đăng nhập — Đang gửi', signInPreviews.submitting],
  ['scr-002-invalid-credentials', 'Đăng nhập — Lỗi 401', signInPreviews.invalidCredentials],
  ['scr-002-blocked', 'Đăng nhập — Bị chặn 423', signInPreviews.blocked],
  ['scr-002-banned', 'Đăng nhập — Bị cấm 403', signInPreviews.banned],
  ['scr-002-password-reset', 'Đăng nhập — Vừa đặt lại mật khẩu', signInPreviews.passwordReset],
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

const verifyEmailEntries = [
  [
    'scr-004-after-register',
    'Xác thực email — Sau đăng ký (khách)',
    verifyEmailPreviews.afterRegister,
  ],
  [
    'scr-004-signed-in',
    'Xác thực email — Đã đăng nhập, chưa xác thực',
    verifyEmailPreviews.signedInUnverified,
  ],
  ['scr-004-resend-sending', 'Xác thực email — Đang gửi lại', verifyEmailPreviews.resendSending],
  ['scr-004-resend-sent', 'Xác thực email — Đã gửi lại', verifyEmailPreviews.resendSent],
  [
    'scr-004-resend-cooldown',
    'Xác thực email — Chờ gửi lại 429',
    verifyEmailPreviews.resendCooldown,
  ],
  ['scr-004-resend-error', 'Xác thực email — Gửi lại lỗi', verifyEmailPreviews.resendError],
  ['scr-004-loading', 'Xác thực email — Đang tải', verifyEmailPreviews.loading],
  ['scr-004-verifying', 'Xác thực email — Đang kiểm tra link', verifyEmailPreviews.verifying],
  [
    'scr-004-verified-guest',
    'Xác thực email — Đã xác thực (khách)',
    verifyEmailPreviews.verifiedGuest,
  ],
  [
    'scr-004-verified-signed-in',
    'Xác thực email — Đã xác thực (đã đăng nhập)',
    verifyEmailPreviews.verifiedSignedIn,
  ],
  [
    'scr-004-invalid-guest',
    'Xác thực email — Link hết hạn (khách)',
    verifyEmailPreviews.invalidGuest,
  ],
  [
    'scr-004-invalid-signed-in',
    'Xác thực email — Link hết hạn (đã đăng nhập)',
    verifyEmailPreviews.invalidSignedIn,
  ],
  ['scr-004-error', 'Xác thực email — Lỗi hệ thống', verifyEmailPreviews.error],
] as const

const forgotPasswordEntries = [
  ['scr-005-default', 'Quên mật khẩu — Mặc định', forgotPasswordPreviews.default],
  ['scr-005-submitting', 'Quên mật khẩu — Đang gửi', forgotPasswordPreviews.submitting],
  [
    'scr-005-invalid-email',
    'Quên mật khẩu — Email sai định dạng',
    forgotPasswordPreviews.invalidEmail,
  ],
  ['scr-005-sent', 'Quên mật khẩu — Đã nhận yêu cầu', forgotPasswordPreviews.sent],
  ['scr-005-error', 'Quên mật khẩu — Lỗi hệ thống', forgotPasswordPreviews.error],
] as const

const resetPasswordEntries = [
  ['scr-006-default', 'Đặt lại mật khẩu — Mặc định', resetPasswordPreviews.default],
  ['scr-006-submitting', 'Đặt lại mật khẩu — Đang gửi', resetPasswordPreviews.submitting],
  ['scr-006-validation', 'Đặt lại mật khẩu — Lỗi phía khách', resetPasswordPreviews.validation],
  [
    'scr-006-unchanged',
    'Đặt lại mật khẩu — Trùng mật khẩu cũ 422',
    resetPasswordPreviews.unchanged,
  ],
  ['scr-006-missing-token', 'Đặt lại mật khẩu — Thiếu token', resetPasswordPreviews.missingToken],
  [
    'scr-006-invalid-token',
    'Đặt lại mật khẩu — Token hỏng 400',
    resetPasswordPreviews.invalidToken,
  ],
  ['scr-006-generic-error', 'Đặt lại mật khẩu — Lỗi hệ thống', resetPasswordPreviews.genericError],
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
  ...verifyEmailEntries.map(([id, title, props]) => ({
    fullFrame: true,
    group: 'screen' as const,
    id,
    render: () =>
      createElement(
        AuthContext.Provider,
        { value: guestAuth },
        createElement(PublicLayout, null, createElement(VerifyEmailView, props)),
      ),
    scr: 'SCR-004',
    title,
  })),
  ...forgotPasswordEntries.map(([id, title, props]) => ({
    fullFrame: true,
    group: 'screen' as const,
    id,
    render: () =>
      createElement(
        AuthContext.Provider,
        { value: guestAuth },
        createElement(PublicLayout, null, createElement(ForgotPasswordView, props)),
      ),
    scr: 'SCR-005',
    title,
  })),
  ...resetPasswordEntries.map(([id, title, props]) => ({
    fullFrame: true,
    group: 'screen' as const,
    id,
    render: () =>
      createElement(
        AuthContext.Provider,
        { value: guestAuth },
        createElement(PublicLayout, null, createElement(ResetPasswordView, props)),
      ),
    scr: 'SCR-006',
    title,
  })),
] satisfies readonly PreviewEntry[]
