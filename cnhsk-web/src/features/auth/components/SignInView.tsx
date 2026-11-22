import type { FormEvent } from 'react'

import { Button, FormField, Input, PasswordInput } from '@/shared/ui'

import type { SignInFieldErrors, SignInNotice } from '../signInForm'
import type { LoginRequest } from '../types'
import { useFocusFirstInvalid } from '../useFocusFirstInvalid'
import { AuthHeading } from './AuthHeading'
import { AuthInkWashStage } from './AuthInkWashStage'
import { AuthLink } from './AuthLink'
import { AuthNotice } from './AuthNotice'

export type SignInViewState = 'ready' | 'submitting' | 'error'

export interface SignInViewProps {
  fieldErrors?: SignInFieldErrors
  notice?: SignInNotice
  onEmailChange: (value: string) => void
  onPasswordChange: (value: string) => void
  onSubmit: () => void
  registerTo: string
  state: SignInViewState
  submitDisabled?: boolean
  values: LoginRequest
}

function formatDateTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'thời điểm máy chủ thông báo'

  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(date)
}

function NoticeBanner({ notice }: { notice: SignInNotice }) {
  if (notice.kind === 'password-reset' || notice.kind === 'registered') {
    const registered = notice.kind === 'registered'
    return (
      <AuthNotice title={registered ? 'Đã tạo tài khoản' : 'Đã đặt lại mật khẩu'} tone="success">
        {registered
          ? 'Hãy đăng nhập bằng email và mật khẩu vừa đăng ký.'
          : 'Hãy đăng nhập bằng mật khẩu mới.'}
      </AuthNotice>
    )
  }

  const informational = notice.kind === 'blocked' || notice.kind === 'suspended'
  let title: string | undefined = 'Không đăng nhập được'
  let content = 'Vui lòng kiểm tra lại thông tin đã nhập.'

  switch (notice.kind) {
    case 'invalid-credentials':
      title = undefined
      content = 'Email hoặc mật khẩu không đúng'
      break
    case 'blocked':
      title = 'Tạm thời chưa thể thử lại'
      content = `Bạn có thể đăng nhập lại từ ${formatDateTime(notice.blockedUntil)}.`
      break
    case 'banned':
      title = 'Tài khoản của bạn đã bị cấm.'
      content = notice.banReason
        ? `Lý do: ${notice.banReason}`
        : 'Vui lòng liên hệ quản trị viên để được hỗ trợ.'
      break
    case 'suspended':
      title = 'Tài khoản đang bị tạm đình chỉ'
      content = notice.suspendedUntil
        ? `Bạn có thể đăng nhập lại từ ${formatDateTime(notice.suspendedUntil)}.`
        : 'Vui lòng liên hệ quản trị viên để được hỗ trợ.'
      break
    case 'validation':
      content = 'Vui lòng kiểm tra lại các trường được đánh dấu.'
      break
    case 'generic':
      content = notice.message
      break
  }

  return (
    <AuthNotice
      requestId={notice.kind === 'generic' ? notice.requestId : undefined}
      title={title}
      tone={informational ? 'info' : 'danger'}
    >
      {content}
    </AuthNotice>
  )
}

export function SignInView({
  fieldErrors = {},
  notice,
  onEmailChange,
  onPasswordChange,
  onSubmit,
  registerTo,
  state,
  submitDisabled = false,
  values,
}: SignInViewProps) {
  const submitting = state === 'submitting'
  const { form, markSubmitted } = useFocusFirstInvalid(fieldErrors, submitting)
  const hasFieldError = Object.values(fieldErrors).some(Boolean)
  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    markSubmitted()
    onSubmit()
  }

  return (
    <AuthInkWashStage variant="002">
      <AuthHeading seal="学习" title="Đăng nhập">
        Chưa có tài khoản? <AuthLink to={registerTo}>Đăng ký</AuthLink>
      </AuthHeading>

      {notice && !(notice.kind === 'validation' && hasFieldError) ? (
        <NoticeBanner notice={notice} />
      ) : null}

      <form className="space-y-4" noValidate onSubmit={handleSubmit} ref={form}>
        <FormField className="mb-0" error={fieldErrors.email} htmlFor="sign-in-email" label="Email">
          <Input
            aria-required
            autoComplete="username"
            disabled={submitting}
            inputMode="email"
            maxLength={256}
            name="email"
            onChange={(event) => onEmailChange(event.target.value)}
            type="email"
            value={values.email}
          />
        </FormField>

        <div>
          <FormField
            className="mb-0"
            error={fieldErrors.password}
            htmlFor="sign-in-password"
            label="Mật khẩu"
          >
            <PasswordInput
              aria-required
              autoComplete="current-password"
              disabled={submitting}
              maxLength={73}
              name="password"
              onChange={(event) => onPasswordChange(event.target.value)}
              value={values.password}
            />
          </FormField>
          <div className="flex justify-end">
            <AuthLink
              className="inline-flex min-h-touch items-center text-body-sm"
              to="/forgot-password"
            >
              Quên mật khẩu?
            </AuthLink>
          </div>
        </div>

        <Button className="w-full" disabled={submitDisabled} loading={submitting} type="submit">
          {submitting ? 'Đang đăng nhập' : 'Đăng nhập'}
        </Button>
      </form>
    </AuthInkWashStage>
  )
}
