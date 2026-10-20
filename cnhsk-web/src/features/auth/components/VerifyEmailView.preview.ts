import type { VerifyEmailViewProps } from './VerifyEmailView'

/** Dữ liệu mẫu chỉ dùng cho /__preview. */
const base = {
  authenticated: false,
  continueTarget: { to: '/learn/topics' },
  loginTo: '/login',
  loginToResend: '/login?redirect=%2Fverify-email',
  mode: 'guide',
  onResend: () => undefined,
  onRetryVerify: () => undefined,
  registerTo: '/register',
  resend: { kind: 'idle' },
} satisfies VerifyEmailViewProps

const signedIn = { ...base, authenticated: true, email: 'nguyen.an@example.com' }

export const verifyEmailPreviews = {
  afterRegister: { ...base, email: 'nguyen.an@example.com' },
  signedInUnverified: signedIn,
  resendSending: { ...signedIn, resend: { kind: 'sending' } },
  resendSent: { ...signedIn, resend: { kind: 'sent' } },
  resendCooldown: { ...signedIn, resend: { kind: 'cooldown', secondsLeft: 87 } },
  resendError: {
    ...signedIn,
    resend: {
      kind: 'error',
      message: 'Chưa gửi lại được email. Vui lòng thử lại sau.',
      requestId: 'mau-req-0001',
    },
  },
  loading: { ...base, mode: 'loading' },
  verifying: { ...base, mode: 'verifying' },
  verifiedGuest: { ...base, mode: 'verified' },
  verifiedSignedIn: { ...signedIn, mode: 'verified' },
  invalidGuest: { ...base, mode: 'invalid' },
  invalidSignedIn: { ...signedIn, mode: 'invalid' },
  error: { ...base, errorRequestId: 'mau-req-0002', mode: 'error' },
} satisfies Record<string, VerifyEmailViewProps>
