import type { ForgotPasswordViewProps } from './ForgotPasswordView'
import type { ResetPasswordViewProps } from './ResetPasswordView'

/** Dữ liệu mẫu chỉ dùng cho /__preview. */
const forgotBase = {
  email: '',
  loginTo: '/login',
  onEmailChange: () => undefined,
  onSendAgain: () => undefined,
  onSubmit: () => undefined,
  state: 'ready',
} satisfies ForgotPasswordViewProps

export const forgotPasswordPreviews = {
  default: forgotBase,
  submitting: { ...forgotBase, email: 'nguyen.an@example.com', state: 'submitting' },
  invalidEmail: {
    ...forgotBase,
    email: 'nguyen.an@',
    emailError: 'Vui lòng nhập email đúng định dạng.',
  },
  sent: { ...forgotBase, sentTo: 'nguyen.an@example.com', state: 'sent' },
  error: {
    ...forgotBase,
    email: 'nguyen.an@example.com',
    error: {
      message: 'Chưa gửi được yêu cầu lúc này. Vui lòng thử lại.',
      requestId: 'mau-req-0003',
    },
    state: 'error',
  },
} satisfies Record<string, ForgotPasswordViewProps>

const resetBase = {
  forgotTo: '/forgot-password',
  linkUsable: true,
  onConfirmPasswordChange: () => undefined,
  onNewPasswordChange: () => undefined,
  onSubmit: () => undefined,
  state: 'ready',
  values: { confirmPassword: '', newPassword: '' },
} satisfies ResetPasswordViewProps

export const resetPasswordPreviews = {
  default: resetBase,
  submitting: {
    ...resetBase,
    state: 'submitting',
    values: { confirmPassword: 'MatKhauMoi1', newPassword: 'MatKhauMoi1' },
  },
  validation: {
    ...resetBase,
    fieldErrors: {
      confirmPassword: 'Mật khẩu nhập lại chưa khớp.',
      newPassword: 'Mật khẩu cần 8–72 ký tự, gồm chữ và số.',
    },
    notice: { kind: 'validation' },
    state: 'error',
    values: { confirmPassword: 'khac', newPassword: 'abcdefgh' },
  },
  unchanged: {
    ...resetBase,
    fieldErrors: { newPassword: 'Mật khẩu mới phải khác mật khẩu hiện tại.' },
    state: 'error',
    values: { confirmPassword: 'MatKhauCu1', newPassword: 'MatKhauCu1' },
  },
  missingToken: { ...resetBase, linkUsable: false },
  invalidToken: { ...resetBase, notice: { kind: 'invalid-token' }, state: 'error' },
  genericError: {
    ...resetBase,
    notice: {
      kind: 'generic',
      message: 'Chưa đặt lại được mật khẩu. Vui lòng thử lại.',
      requestId: 'mau-req-0004',
    },
    state: 'error',
  },
} satisfies Record<string, ResetPasswordViewProps>
