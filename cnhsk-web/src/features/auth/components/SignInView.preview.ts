import type { SignInViewProps } from './SignInView'

const idleCallbacks = {
  onEmailChange: () => undefined,
  onPasswordChange: () => undefined,
  onSubmit: () => undefined,
  registerTo: '/register?redirect=%2Flearn%2Ftopics',
} satisfies Pick<SignInViewProps, 'onEmailChange' | 'onPasswordChange' | 'onSubmit' | 'registerTo'>

export const signInPreviews = {
  default: {
    ...idleCallbacks,
    state: 'ready',
    values: { email: '', password: '' },
  },
  submitting: {
    ...idleCallbacks,
    state: 'submitting',
    values: { email: 'nguoi.hoc@example.com', password: 'mat-khau-mau' },
  },
  invalidCredentials: {
    ...idleCallbacks,
    notice: { kind: 'invalid-credentials' },
    state: 'error',
    values: { email: 'nguoi.hoc@example.com', password: 'mat-khau-mau' },
  },
  blocked: {
    ...idleCallbacks,
    notice: { blockedUntil: '2026-10-20T14:30:00+07:00', kind: 'blocked' },
    state: 'error',
    submitDisabled: true,
    values: { email: 'nguoi.hoc@example.com', password: '' },
  },
  banned: {
    ...idleCallbacks,
    notice: { banReason: 'Vi phạm quy định cộng đồng.', kind: 'banned' },
    state: 'error',
    values: { email: 'nguoi.hoc@example.com', password: '' },
  },
  fieldErrors: {
    ...idleCallbacks,
    fieldErrors: {
      email: 'Email không đúng định dạng.',
      password: 'Mật khẩu không được vượt quá 72 ký tự.',
    },
    notice: { kind: 'validation' },
    state: 'error',
    values: { email: 'email-khong-hop-le', password: 'mat-khau-mau' },
  },
} as const satisfies Record<string, SignInViewProps>
