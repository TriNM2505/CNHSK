import { CircleAlert, CircleCheck, LoaderCircle, MailCheck } from 'lucide-react'
import type { ReactNode } from 'react'

import { Button, PageHeader, Skeleton, StatusBadge } from '@/shared/ui'

import { formatCountdown } from '../verifyEmailFlow'
import { AuthInkWashStage } from './AuthInkWashStage'

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
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-brand-100 bg-surface-subtle p-4">
        <p className="text-body-sm text-ink-secondary">
          Đăng nhập để gửi lại liên kết xác thực tới email của bạn.
        </p>
        <Button to={loginToResend} variant="outline">
          Đăng nhập để gửi lại
        </Button>
      </div>
    )
  }

  const sending = resend.kind === 'sending'
  const cooling = resend.kind === 'cooldown'

  return (
    <div className="rounded-md border border-brand-100 bg-surface-subtle p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="space-y-0.5">
          <p className="text-label text-ink">Gửi lại liên kết xác thực</p>
          <p className="text-body-sm text-ink-secondary">
            Chưa nhận được thư hoặc liên kết cũ đã hết hạn?
          </p>
        </div>
        <Button disabled={cooling} loading={sending} onClick={onResend} variant="outline">
          {cooling
            ? `Gửi lại sau ${formatCountdown(resend.secondsLeft)}`
            : sending
              ? 'Đang gửi lại'
              : 'Gửi lại email xác thực'}
        </Button>
      </div>

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

function headerInfo(mode: VerifyEmailMode) {
  switch (mode) {
    case 'verified':
      return {
        badge: <StatusBadge tone="success">Đã xác thực</StatusBadge>,
        hanzi: '验证',
        pinyin: 'yànzhèng',
        subtitle: 'Tài khoản của bạn đã được xác thực. Bạn có thể tiếp tục học.',
        title: 'Email đã được xác thực',
      }
    case 'invalid':
      return {
        badge: <StatusBadge tone="warning">Liên kết không dùng được</StatusBadge>,
        hanzi: '邮箱',
        pinyin: 'yǒuxiāng',
        subtitle:
          'Mỗi liên kết xác thực chỉ dùng được một lần và có hiệu lực trong 24 giờ. Hãy gửi lại để nhận liên kết mới.',
        title: 'Liên kết đã hết hạn hoặc đã được dùng',
      }
    case 'guide':
      return {
        badge: <StatusBadge tone="info">Xác thực email</StatusBadge>,
        hanzi: '邮箱',
        pinyin: 'yǒuxiāng',
        subtitle: 'Email đã xác thực giúp khôi phục mật khẩu và bật thông báo học tập khi cần.',
        title: 'Kiểm tra hộp thư của bạn',
      }
    case 'error':
      return {
        badge: <StatusBadge tone="danger">Chưa kiểm tra được</StatusBadge>,
        hanzi: '邮箱',
        pinyin: 'yǒuxiāng',
        subtitle: 'Đã có sự cố khi kiểm tra liên kết xác thực email.',
        title: 'Xác thực email',
      }
    default:
      return {
        badge: <StatusBadge tone="neutral">Xác thực email</StatusBadge>,
        hanzi: '邮箱',
        pinyin: 'yǒuxiāng',
        subtitle: 'Đang kiểm tra trạng thái xác thực email của tài khoản.',
        title: 'Xác thực email',
      }
  }
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
  const header = headerInfo(mode)

  return (
    <AuthInkWashStage variant="004">
      <div className="space-y-5">
        <div className="border-b border-line pb-4">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
            {header.badge}
            <span className="text-caption text-brand-700">Bảo mật tài khoản CNHSK</span>
          </div>
          <PageHeader className="mb-0" subtitle={header.subtitle} title={header.title} />
        </div>

        {mode === 'loading' ? <Skeleton variant="card" /> : null}

        {mode === 'verifying' ? (
          <p className="flex items-center gap-3 py-2 text-body text-ink" role="status">
            <LoaderCircle aria-hidden="true" className="size-5 motion-safe:animate-spin" />
            <span>Đang kiểm tra liên kết xác thực…</span>
          </p>
        ) : null}

        {mode === 'verified' ? (
          <div className="space-y-5">
            <div className="flex items-start gap-3 rounded-md border border-success bg-success-bg p-4 text-body-sm text-success">
              <CircleCheck aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
              <p>
                Địa chỉ email của bạn đã được xác nhận. Bạn có thể sử dụng đầy đủ tính năng khôi
                phục mật khẩu và nhận nhắc lịch học qua email.
              </p>
            </div>
            <div className="flex flex-wrap items-center gap-3 border-t border-line pt-4">
              {authenticated ? (
                <ContinueButton target={continueTarget}>Tiếp tục</ContinueButton>
              ) : (
                <Button to={loginTo}>Đăng nhập để tiếp tục</Button>
              )}
            </div>
          </div>
        ) : null}

        {mode === 'guide' ? (
          <div className="space-y-5">
            <div className="space-y-3 rounded-md border border-brand-100 bg-brand-50 p-4">
              <div className="flex items-center gap-2.5">
                <span
                  aria-hidden="true"
                  className="flex size-8 shrink-0 items-center justify-center rounded-pill bg-surface text-brand-700 shadow-sm"
                >
                  <MailCheck className="size-4" />
                </span>
                <p className="text-label text-brand-800">Thư xác thực đã được gửi</p>
              </div>
              <p className="min-w-0 break-words text-body text-ink">
                {email ? (
                  <>
                    Liên kết xác thực đã gửi tới <strong>{email}</strong>. Mở email và bấm vào liên
                    kết để xác thực tài khoản.
                  </>
                ) : (
                  'Mở email chúng tôi đã gửi và bấm vào liên kết để xác thực tài khoản.'
                )}
              </p>
              <p className="text-body-sm text-ink-secondary">
                Không thấy thư? Hãy kiểm tra thêm mục spam hoặc quảng cáo.
              </p>
            </div>

            <ResendSection {...props} />

            <div className="flex flex-wrap items-center justify-between gap-4 border-t border-line pt-4">
              <div>
                {authenticated ? (
                  <ContinueButton target={continueTarget}>Bỏ qua, vào học</ContinueButton>
                ) : (
                  <Button to={loginTo} variant="outline">
                    Bỏ qua, vào học
                  </Button>
                )}
              </div>

              <p className="flex flex-wrap items-center gap-1 text-body-sm text-ink-secondary">
                <span>Sai địa chỉ email?</span>
                <Button size="sm" to={registerTo} variant="ghost">
                  Đăng ký lại
                </Button>
              </p>
            </div>
          </div>
        ) : null}

        {mode === 'invalid' ? <ResendSection {...props} /> : null}

        {mode === 'error' ? (
          <div
            className="flex items-start gap-3 rounded-md border border-danger bg-danger-bg p-4 text-body-sm text-danger"
            role="alert"
          >
            <CircleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
            <div className="min-w-0">
              <p className="font-semibold">Chưa kiểm tra được liên kết</p>
              <p className="mt-1">Đã có lỗi khi xác thực email. Vui lòng thử lại.</p>
              {errorRequestId ? (
                <p className="mt-1 text-caption">Mã lỗi: {errorRequestId}</p>
              ) : null}
              <Button className="mt-4" onClick={onRetryVerify} size="sm" variant="outline">
                Thử lại
              </Button>
            </div>
          </div>
        ) : null}
      </div>
    </AuthInkWashStage>
  )
}
