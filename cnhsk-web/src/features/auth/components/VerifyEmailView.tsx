import { CircleAlert, CircleCheck, LoaderCircle, MailCheck } from 'lucide-react'
import type { ReactNode } from 'react'

import { Button, Skeleton } from '@/shared/ui'

import { formatCountdown } from '../verifyEmailFlow'

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

function ContinueButton({ children, target }: { children: ReactNode; target: ContinueTarget }) {
  return 'to' in target ? (
    <Button to={target.to}>{children}</Button>
  ) : (
    <Button href={target.href}>{children}</Button>
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
      <div className="mt-6 rounded-md border border-line bg-surface-subtle p-4">
        <p className="text-body-sm text-ink-secondary">
          Đăng nhập để gửi lại liên kết xác thực tới email của bạn.
        </p>
        <Button className="mt-3" to={loginToResend} variant="outline">
          Đăng nhập để gửi lại
        </Button>
      </div>
    )
  }

  const sending = resend.kind === 'sending'
  const cooling = resend.kind === 'cooldown'

  return (
    <div className="mt-6">
      <Button disabled={cooling} loading={sending} onClick={onResend} variant="outline">
        {cooling
          ? `Gửi lại sau ${formatCountdown(resend.secondsLeft)}`
          : sending
            ? 'Đang gửi lại'
            : 'Gửi lại email xác thực'}
      </Button>

      {resend.kind === 'sent' ? (
        <p className="mt-3 flex items-start gap-2 text-body-sm text-success" role="status">
          <CircleCheck aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          <span>Đã gửi lại. Email mới sẽ đến trong vài phút.</span>
        </p>
      ) : null}

      {cooling ? (
        <p className="mt-3 text-body-sm text-ink-secondary" role="status">
          Bạn đã gửi lại nhiều lần. Vui lòng chờ trước khi gửi tiếp.
        </p>
      ) : null}

      {resend.kind === 'error' ? (
        <div className="mt-3 flex items-start gap-2 text-body-sm text-danger" role="alert">
          <CircleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          <div className="min-w-0">
            <p>{resend.message}</p>
            {resend.requestId ? (
              <p className="mt-1 text-caption">Mã lỗi: {resend.requestId}</p>
            ) : null}
          </div>
        </div>
      ) : null}
    </div>
  )
}

function VerifiedContent({
  authenticated,
  continueTarget,
  loginTo,
}: Pick<VerifyEmailViewProps, 'authenticated' | 'continueTarget' | 'loginTo'>) {
  return (
    <>
      <p className="mb-2 flex items-center gap-2 text-label text-success">
        <CircleCheck aria-hidden="true" className="size-5" />
        <span>Đã xác thực</span>
      </p>
      <h1 className="text-h1-sm text-ink sm:text-h1">Email đã được xác thực</h1>
      <p className="mb-6 mt-2 text-body text-ink-secondary">
        Tài khoản của bạn đã được xác thực. Bạn có thể tiếp tục học.
      </p>
      {authenticated ? (
        <ContinueButton target={continueTarget}>Tiếp tục</ContinueButton>
      ) : (
        <Button to={loginTo}>Đăng nhập để tiếp tục</Button>
      )}
    </>
  )
}

function GuideContent(props: VerifyEmailViewProps) {
  const { authenticated, continueTarget, email, loginTo, registerTo } = props
  return (
    <>
      <p className="mb-2 flex items-center gap-2 text-label text-brand-700">
        <MailCheck aria-hidden="true" className="size-5" />
        <span>Xác thực email</span>
      </p>
      <h1 className="text-h1-sm text-ink sm:text-h1">Kiểm tra hộp thư của bạn</h1>
      <p className="mt-2 break-words text-body text-ink-secondary">
        {email ? (
          <>
            Liên kết xác thực đã gửi tới <strong className="text-ink">{email}</strong>. Mở email và
            bấm vào liên kết để xác thực tài khoản.
          </>
        ) : (
          'Mở email chúng tôi đã gửi và bấm vào liên kết để xác thực tài khoản.'
        )}
      </p>
      <ul className="mt-4 list-disc space-y-1 pl-5 text-body-sm text-ink-secondary">
        <li>Không thấy email? Kiểm tra thư mục spam hoặc quảng cáo.</li>
        <li>Liên kết có hiệu lực trong 24 giờ.</li>
        <li>Bạn vẫn học được trước khi xác thực.</li>
      </ul>

      <ResendSection {...props} />

      <div className="mt-6 flex flex-wrap items-center gap-3">
        {authenticated ? (
          <ContinueButton target={continueTarget}>Bỏ qua, vào học</ContinueButton>
        ) : (
          <Button to={loginTo} variant="ghost">
            Bỏ qua, vào học
          </Button>
        )}
      </div>

      <p className="mt-6 flex flex-wrap items-center gap-1 text-body-sm text-ink-secondary">
        <span>Sai địa chỉ email?</span>
        <Button size="sm" to={registerTo} variant="ghost">
          Đăng ký lại
        </Button>
      </p>
    </>
  )
}

function InvalidContent(props: VerifyEmailViewProps) {
  return (
    <>
      <p className="mb-2 flex items-center gap-2 text-label text-warning">
        <CircleAlert aria-hidden="true" className="size-5" />
        <span>Liên kết không dùng được</span>
      </p>
      <h1 className="text-h1-sm text-ink sm:text-h1">Liên kết đã hết hạn hoặc đã được dùng</h1>
      <p className="mt-2 text-body text-ink-secondary">
        Mỗi liên kết xác thực chỉ dùng được một lần và có hiệu lực trong 24 giờ. Hãy gửi lại để nhận
        liên kết mới.
      </p>
      <ResendSection {...props} />
    </>
  )
}

function ErrorContent({ errorRequestId, onRetryVerify }: VerifyEmailViewProps) {
  return (
    <div
      className="flex items-start gap-3 rounded-md border border-danger bg-danger-bg p-4 text-body-sm text-danger"
      role="alert"
    >
      <CircleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
      <div className="min-w-0">
        <p className="font-semibold">Chưa kiểm tra được liên kết</p>
        <p className="mt-1">Đã có lỗi khi xác thực email. Vui lòng thử lại.</p>
        {errorRequestId ? <p className="mt-1 text-caption">Mã lỗi: {errorRequestId}</p> : null}
        <Button className="mt-3" onClick={onRetryVerify} size="sm" variant="outline">
          Thử lại
        </Button>
      </div>
    </div>
  )
}

export function VerifyEmailView(props: VerifyEmailViewProps) {
  const { mode } = props

  return (
    <main className="grid min-h-[calc(100vh-4rem)] min-w-0 bg-surface lg:grid-cols-[minmax(0,3fr)_minmax(20rem,2fr)]">
      <section className="flex min-w-0 items-center justify-center px-4 py-10 sm:px-6 lg:px-10">
        <div className="w-full max-w-md">
          {mode === 'loading' ? <Skeleton variant="card" /> : null}
          {mode === 'verifying' ? (
            <p className="flex items-center gap-3 text-body text-ink" role="status">
              <LoaderCircle aria-hidden="true" className="size-5 motion-safe:animate-spin" />
              <span>Đang kiểm tra liên kết xác thực…</span>
            </p>
          ) : null}
          {mode === 'verified' ? <VerifiedContent {...props} /> : null}
          {mode === 'guide' ? <GuideContent {...props} /> : null}
          {mode === 'invalid' ? <InvalidContent {...props} /> : null}
          {mode === 'error' ? <ErrorContent {...props} /> : null}
        </div>
      </section>

      <aside className="hidden min-w-0 items-center bg-brand-800 px-10 py-12 text-surface lg:flex">
        <div className="mx-auto max-w-md">
          <h2 className="text-h2">Xác thực email để giữ tài khoản an toàn</h2>
          <p className="mt-4 text-body">
            Email đã xác thực giúp bạn khôi phục mật khẩu khi cần. Bạn vẫn học được trước khi xác
            thực.
          </p>
        </div>
      </aside>
    </main>
  )
}
