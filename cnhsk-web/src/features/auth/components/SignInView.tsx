import { CircleAlert, CircleCheck, Clock3, ShieldAlert } from 'lucide-react'
import type { FormEvent } from 'react'

import { cn } from '@/shared/lib/cn'
import { Button, FormField, Input, PageHeader, PasswordInput, StatusBadge } from '@/shared/ui'

import type { SignInFieldErrors, SignInNotice } from '../signInForm'
import type { LoginRequest } from '../types'
import { AuthSplitLayout } from './AuthSplitLayout'

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

const SIGN_IN_ASIDE = {
  badge: 'Một tài khoản CNHSK',
  body: 'Tiếp tục bài học, luyện đề và trò chơi chữ Hán bằng cùng một tài khoản trên web chính và trang game.',
  hanzi: '学习',
  title: 'Học tiếng Trung trong một không gian thống nhất',
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
  if (notice.kind === 'password-reset') {
    return (
      <div
        className="mb-5 flex items-start gap-3 rounded-md border border-success bg-success-bg p-4 text-body-sm text-success"
        role="status"
      >
        <CircleCheck aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
        <div className="min-w-0">
          <p className="font-semibold">Đã đặt lại mật khẩu</p>
          <p className="mt-1">Hãy đăng nhập bằng mật khẩu mới.</p>
        </div>
      </div>
    )
  }

  const isBlocked = notice.kind === 'blocked'
  const isSuspended = notice.kind === 'suspended'
  const isInformational = isBlocked || isSuspended
  const Icon = isBlocked ? Clock3 : notice.kind === 'banned' ? ShieldAlert : CircleAlert
  let title = 'Không đăng nhập được'
  let content = 'Vui lòng kiểm tra lại thông tin đã nhập.'

  switch (notice.kind) {
    case 'invalid-credentials':
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
    <div
      className={cn(
        'mb-5 flex items-start gap-3 rounded-md border p-4 text-body-sm',
        isInformational
          ? 'border-info bg-info-bg text-info'
          : 'border-danger bg-danger-bg text-danger',
      )}
      role="alert"
    >
      <Icon aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
      <div className="min-w-0">
        <p className="font-semibold">{title}</p>
        <p className="mt-1">{content}</p>
        {notice.kind === 'generic' && notice.requestId ? (
          <p className="mt-1 text-caption">Mã lỗi: {notice.requestId}</p>
        ) : null}
      </div>
    </div>
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
  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onSubmit()
  }

  return (
    <AuthSplitLayout aside={SIGN_IN_ASIDE}>
      <div className="mb-3">
        <StatusBadge tone="neutral">Xác thực tài khoản</StatusBadge>
      </div>
      <PageHeader
        subtitle="Dùng chung tài khoản cho web chính và trang game CNHSK."
        title="Đăng nhập"
      />

      {notice ? <NoticeBanner notice={notice} /> : null}

      <form noValidate onSubmit={handleSubmit}>
        <FormField error={fieldErrors.email} htmlFor="sign-in-email" label="Email" required>
          <Input
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

        <FormField
          error={fieldErrors.password}
          htmlFor="sign-in-password"
          label="Mật khẩu"
          required
        >
          <PasswordInput
            autoComplete="current-password"
            disabled={submitting}
            maxLength={73}
            name="password"
            onChange={(event) => onPasswordChange(event.target.value)}
            value={values.password}
          />
        </FormField>

        <div className="mb-5 flex justify-end">
          <Button size="sm" to="/forgot-password" variant="ghost">
            Quên mật khẩu?
          </Button>
        </div>

        <Button className="w-full" disabled={submitDisabled} loading={submitting} type="submit">
          {submitting ? 'Đang đăng nhập' : 'Đăng nhập'}
        </Button>
      </form>

      <div className="mt-6 flex flex-wrap items-center justify-between gap-2 border-t border-line pt-4 text-body-sm text-ink-secondary">
        <span>Chưa có tài khoản?</span>
        <Button size="sm" to={registerTo} variant="outline">
          Đăng ký
        </Button>
      </div>
    </AuthSplitLayout>
  )
}
