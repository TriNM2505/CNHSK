import { CircleAlert, MailCheck } from 'lucide-react'
import type { FormEvent } from 'react'

import { Button, FormField, Input } from '@/shared/ui'

import { AuthSplitLayout } from './AuthSplitLayout'

export type ForgotPasswordState = 'ready' | 'submitting' | 'sent' | 'error'

export interface ForgotPasswordViewProps {
  email: string
  emailError?: string
  error?: { message: string; requestId?: string }
  loginTo: string
  onEmailChange: (value: string) => void
  onSendAgain: () => void
  onSubmit: () => void
  /** Email đã gửi yêu cầu — hiển thị lại trong thông báo chung (FR-038). */
  sentTo?: string
  state: ForgotPasswordState
}

const ASIDE = {
  body: 'Dù tài khoản có tồn tại hay không, bạn đều nhận cùng một thông báo. Cách này giúp người khác không dò được email nào đã đăng ký.',
  title: 'Phản hồi giống nhau cho mọi trường hợp',
}

export function ForgotPasswordView({
  email,
  emailError,
  error,
  loginTo,
  onEmailChange,
  onSendAgain,
  onSubmit,
  sentTo,
  state,
}: ForgotPasswordViewProps) {
  const submitting = state === 'submitting'
  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onSubmit()
  }

  if (state === 'sent') {
    return (
      <AuthSplitLayout aside={ASIDE}>
        <p className="mb-2 flex items-center gap-2 text-label text-success">
          <MailCheck aria-hidden="true" className="size-5" />
          <span>Đã nhận yêu cầu</span>
        </p>
        <h1 className="text-h1-sm text-ink sm:text-h1">Kiểm tra hộp thư của bạn</h1>
        <p className="mt-2 break-words text-body text-ink-secondary" role="status">
          Nếu <strong className="text-ink">{sentTo}</strong> có tài khoản trong hệ thống, hướng dẫn
          đặt lại mật khẩu sẽ được gửi tới đó trong vài phút. Liên kết có hiệu lực 1 giờ.
        </p>
        <div className="mt-6 flex flex-wrap gap-3">
          <Button to={loginTo}>Quay lại đăng nhập</Button>
          <Button onClick={onSendAgain} variant="ghost">
            Gửi lại với email khác
          </Button>
        </div>
      </AuthSplitLayout>
    )
  }

  return (
    <AuthSplitLayout aside={ASIDE}>
      <h1 className="text-h1-sm text-ink sm:text-h1">Quên mật khẩu</h1>
      <p className="mb-6 mt-2 text-body text-ink-secondary">
        Nhập email của tài khoản. Nếu email có trong hệ thống, chúng tôi gửi hướng dẫn đặt lại mật
        khẩu.
      </p>

      {state === 'error' && error ? (
        <div
          className="mb-5 flex items-start gap-3 rounded-md border border-danger bg-danger-bg p-4 text-body-sm text-danger"
          role="alert"
        >
          <CircleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          <div className="min-w-0">
            <p className="font-semibold">Chưa gửi được yêu cầu</p>
            <p className="mt-1">{error.message}</p>
            {error.requestId ? (
              <p className="mt-1 text-caption">Mã lỗi: {error.requestId}</p>
            ) : null}
          </div>
        </div>
      ) : null}

      <form noValidate onSubmit={handleSubmit}>
        <FormField error={emailError} htmlFor="forgot-email" label="Email" required>
          <Input
            autoComplete="email"
            disabled={submitting}
            inputMode="email"
            maxLength={256}
            name="email"
            onChange={(event) => onEmailChange(event.target.value)}
            type="email"
            value={email}
          />
        </FormField>
        <Button className="w-full" loading={submitting} type="submit">
          {submitting ? 'Đang gửi' : 'Gửi hướng dẫn đặt lại mật khẩu'}
        </Button>
      </form>

      <p className="mt-6 flex flex-wrap items-center justify-center gap-1 text-body-sm text-ink-secondary">
        <span>Nhớ ra mật khẩu rồi?</span>
        <Button size="sm" to={loginTo} variant="ghost">
          Quay lại đăng nhập
        </Button>
      </p>
    </AuthSplitLayout>
  )
}
