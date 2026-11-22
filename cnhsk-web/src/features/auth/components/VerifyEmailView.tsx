import { LoaderCircle } from 'lucide-react'
import type { ReactNode } from 'react'

import { Button, Skeleton } from '@/shared/ui'

import { formatCountdown } from '../verifyEmailFlow'
import { AuthHeading } from './AuthHeading'
import { AuthInkWashStage } from './AuthInkWashStage'
import { AuthLink } from './AuthLink'
import { AuthNotice } from './AuthNotice'

export type VerifyEmailMode = 'loading' | 'guide' | 'verifying' | 'verified' | 'invalid' | 'error'

export type ResendView =
  | { kind: 'idle' }
  | { kind: 'sending' }
  | { kind: 'sent' }
  | { kind: 'cooldown'; secondsLeft: number }
  | { kind: 'error'; message: string; requestId?: string }

/** Đích "Tiếp tục": đường dẫn nội bộ hoặc trang game (origin ngoài). */
export type ContinueTarget = { to: string } | { href: string }

export interface VerifyEmailViewProps {
  authenticated: boolean
  continueTarget: ContinueTarget
  email?: string
  errorRequestId?: string
  loginTo: string
  loginToResend: string
  mode: VerifyEmailMode
  onResend: () => void
  onRetryVerify: () => void
  registerTo: string
  resend: ResendView
}

function ContinueButton({
  children,
  target,
  variant = 'primary',
}: {
  children: ReactNode
  target: ContinueTarget
  variant?: 'ghost' | 'primary'
}) {
  return 'to' in target ? (
    <Button to={target.to} variant={variant}>
      {children}
    </Button>
  ) : (
    <Button href={target.href} variant={variant}>
      {children}
    </Button>
  )
}

function ResendSection({
  authenticated,
  loginToResend,
  onResend,
  resend,
}: Pick<VerifyEmailViewProps, 'authenticated' | 'loginToResend' | 'onResend' | 'resend'>) {
  if (!authenticated) {
    return (
      <Button className="w-full" to={loginToResend}>
        Đăng nhập để gửi lại
      </Button>
    )
  }

  const cooling = resend.kind === 'cooldown'

  return (
    <div>
      {resend.kind === 'sent' ? (
        <AuthNotice tone="success">Đã gửi lại. Email mới sẽ đến trong vài phút.</AuthNotice>
      ) : null}
      {resend.kind === 'error' ? (
        <AuthNotice requestId={resend.requestId}>{resend.message}</AuthNotice>
      ) : null}
      <Button
        className="w-full"
        disabled={cooling}
        loading={resend.kind === 'sending'}
        onClick={onResend}
        variant="outline"
      >
        {cooling
          ? `Gửi lại sau ${formatCountdown(resend.secondsLeft)}`
          : resend.kind === 'sending'
            ? 'Đang gửi lại'
            : 'Gửi lại email xác thực'}
      </Button>
      {cooling ? (
        <p className="mt-2 text-body-sm text-ink-secondary" role="status">
          Bạn đã gửi lại nhiều lần. Vui lòng chờ trước khi gửi tiếp.
        </p>
      ) : null}
    </div>
  )
}

const HEADINGS: Record<VerifyEmailMode, { subtitle?: string; title: string }> = {
  error: { title: 'Chưa kiểm tra được liên kết' },
  guide: {
    subtitle: 'Cần xác thực để đăng bài, bình luận, đăng ký cuộc thi và nhận nhắc học qua email.',
    title: 'Xác thực email của bạn',
  },
  invalid: {
    subtitle: 'Mỗi liên kết dùng được một lần trong 24 giờ. Hãy gửi lại để nhận liên kết mới.',
    title: 'Liên kết đã hết hạn hoặc đã được dùng',
  },
  loading: { title: 'Xác thực email' },
  verified: {
    subtitle: 'Bạn có thể đăng bài, bình luận, đăng ký cuộc thi và nhận nhắc học qua email.',
    title: 'Email đã được xác thực',
  },
  verifying: { title: 'Xác thực email' },
}

export function VerifyEmailView(props: VerifyEmailViewProps) {
  const {
    authenticated,
    continueTarget,
    email,
    errorRequestId,
    loginTo,
    mode,
    onRetryVerify,
    registerTo,
  } = props
  const heading = HEADINGS[mode]

  return (
    <AuthInkWashStage variant="004">
      <AuthHeading seal="验证" title={heading.title}>
        {heading.subtitle}
      </AuthHeading>

      {mode === 'loading' ? <Skeleton variant="card" /> : null}

      {mode === 'verifying' ? (
        <p className="flex items-center gap-3 py-2 text-body text-ink" role="status">
          <LoaderCircle aria-hidden="true" className="size-5 motion-safe:animate-spin" />
          <span>Đang kiểm tra liên kết xác thực…</span>
        </p>
      ) : null}

      {mode === 'verified' ? (
        <div className="flex flex-col">
          {authenticated ? (
            <ContinueButton target={continueTarget}>Tiếp tục</ContinueButton>
          ) : (
            <Button to={loginTo}>Đăng nhập để tiếp tục</Button>
          )}
        </div>
      ) : null}

      {mode === 'guide' ? (
        <div className="space-y-4">
          <div className="space-y-1">
            <p className="min-w-0 break-words text-body text-ink">
              {email ? (
                <>
                  Bấm liên kết trong thư CNHSK gửi tới <strong>{email}</strong>. Liên kết có hiệu lực
                  trong 24 giờ.
                </>
              ) : (
                'Bấm liên kết trong thư CNHSK gửi tới email bạn đã đăng ký. Liên kết có hiệu lực trong 24 giờ.'
              )}
            </p>
            <p className="text-body-sm text-ink-secondary">
              Chưa thấy thư? Xem mục thư rác.
            </p>
          </div>

          <ResendSection {...props} />

          {authenticated ? (
            <div className="flex flex-col">
              <ContinueButton target={continueTarget} variant="ghost">
                Để sau, vào học
              </ContinueButton>
            </div>
          ) : (
            <p className="text-body-sm text-ink-secondary">
              Chưa có tài khoản? <AuthLink to={registerTo}>Đăng ký</AuthLink>
            </p>
          )}
        </div>
      ) : null}

      {mode === 'invalid' ? <ResendSection {...props} /> : null}

      {mode === 'error' ? (
        <>
          <AuthNotice requestId={errorRequestId}>
            Đã có lỗi khi xác thực email. Vui lòng thử lại.
          </AuthNotice>
          <Button className="w-full" onClick={onRetryVerify}>
            Thử lại
          </Button>
        </>
      ) : null}
    </AuthInkWashStage>
  )
}
