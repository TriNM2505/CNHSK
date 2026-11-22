import type { FormEvent } from 'react'

import { Button, FormField, Input } from '@/shared/ui'

import { useFocusFirstInvalid } from '../useFocusFirstInvalid'
import { AuthHeading } from './AuthHeading'
import { AuthInkWashStage } from './AuthInkWashStage'
import { AuthLink } from './AuthLink'
import { AuthNotice } from './AuthNotice'

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
  const { form, markSubmitted } = useFocusFirstInvalid(emailError, submitting)
  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    markSubmitted()
    onSubmit()
  }

  if (state === 'sent') {
    return (
      <AuthInkWashStage variant="005">
        <AuthHeading seal="找回" title="Kiểm tra hộp thư của bạn" />
        <div className="space-y-2 text-body text-ink" role="status">
          <p className="break-words">
            Nếu <strong className="font-semibold">{sentTo}</strong> có tài khoản trong hệ thống,
            hướng dẫn đặt lại mật khẩu sẽ được gửi tới đó trong vài phút.
          </p>
          <p className="text-body-sm text-ink-secondary">
            Liên kết có hiệu lực 1 giờ. Chưa thấy thư? Xem mục thư rác.
          </p>
        </div>
        <div className="mt-6 flex flex-col gap-2">
          <Button to={loginTo}>Quay lại đăng nhập</Button>
          <Button onClick={onSendAgain} variant="ghost">
            Gửi lại với email khác
          </Button>
        </div>
      </AuthInkWashStage>
    )
  }

  return (
    <AuthInkWashStage variant="005">
      <AuthHeading seal="找回" title="Quên mật khẩu">
        Nhập email đã đăng ký, bạn sẽ nhận được liên kết đặt mật khẩu mới.
      </AuthHeading>

      {state === 'error' && error ? (
        <AuthNotice requestId={error.requestId}>{error.message}</AuthNotice>
      ) : null}

      <form className="space-y-4" noValidate onSubmit={handleSubmit} ref={form}>
        <FormField className="mb-0" error={emailError} htmlFor="forgot-email" label="Email">
          <Input
            aria-required
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

      <p className="mt-4 text-body-sm text-ink-secondary">
        Nhớ ra mật khẩu rồi? <AuthLink to={loginTo}>Quay lại đăng nhập</AuthLink>
      </p>
    </AuthInkWashStage>
  )
}
