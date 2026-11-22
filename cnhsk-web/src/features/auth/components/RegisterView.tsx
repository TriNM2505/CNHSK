import { CircleAlert } from 'lucide-react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'

import { Button, FormField, Input, PageHeader, PasswordInput, StatusBadge } from '@/shared/ui'

import { NEW_PASSWORD_REQUIREMENT } from '../passwordValidation'
import type { RegisterFieldErrors, RegisterNotice } from '../registerForm'
import type { RegisterRequest } from '../types'
import { AuthSplitLayout } from './AuthSplitLayout'

export type RegisterViewState = 'ready' | 'submitting' | 'error'

export interface RegisterViewProps {
  fieldErrors?: RegisterFieldErrors
  loginTo: string
  notice?: RegisterNotice
  onAcceptTermsChange: (value: boolean) => void
  onConfirmPasswordChange: (value: string) => void
  onDisplayNameChange: (value: string) => void
  onEmailChange: (value: string) => void
  onPasswordChange: (value: string) => void
  onSubmit: () => void
  state: RegisterViewState
  values: RegisterRequest
}

const REGISTER_ASIDE = {
  badge: 'Bắt đầu miễn phí',
  body: 'Khởi tạo hồ sơ học tập để lưu tiến độ từ vựng, chữ Hán, luyện đề và xếp hạng trên web chính cùng trang game.',
  hanzi: '起步',
  meaning: 'Khởi đầu hành trình Hán ngữ',
  pinyin: 'qǐbù',
  title: 'Một tài khoản cho hành trình học của bạn',
}

function NoticeBanner({ notice }: { notice: Exclude<RegisterNotice, { kind: 'email-exists' }> }) {
  const content =
    notice.kind === 'validation'
      ? 'Vui lòng kiểm tra lại các trường được đánh dấu.'
      : notice.message

  return (
    <div
      className="mb-5 flex items-start gap-3 rounded-md border border-danger bg-danger-bg p-4 text-body-sm text-danger"
      role="alert"
    >
      <CircleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
      <div className="min-w-0">
        <p className="font-semibold">Chưa thể đăng ký</p>
        <p className="mt-1">{content}</p>
        {notice.kind === 'generic' && notice.requestId ? (
          <p className="mt-1 text-caption">Mã lỗi: {notice.requestId}</p>
        ) : null}
      </div>
    </div>
  )
}

export function RegisterView({
  fieldErrors = {},
  loginTo,
  notice,
  onAcceptTermsChange,
  onConfirmPasswordChange,
  onDisplayNameChange,
  onEmailChange,
  onPasswordChange,
  onSubmit,
  state,
  values,
}: RegisterViewProps) {
  const submitting = state === 'submitting'
  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onSubmit()
  }
  const termsErrorId = fieldErrors.acceptTerms ? 'register-accept-terms-error' : undefined

  return (
    <AuthSplitLayout aside={REGISTER_ASIDE} backgroundVariant="003" compact hideMobileBanner>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2 border-b border-line pb-4">
          <p className="text-label text-brand-700">Khởi tạo hồ sơ học viên</p>
          <StatusBadge tone="neutral">Tạo tài khoản CNHSK</StatusBadge>
        </div>
        <PageHeader
          subtitle="Tạo tài khoản để học tập, luyện thi và chơi game trên web chính cùng trang game."
          title="Đăng ký"
        />

        {notice && notice.kind !== 'email-exists' ? <NoticeBanner notice={notice} /> : null}

        <form className="space-y-4" noValidate onSubmit={handleSubmit}>
          <div className="rounded-md border border-line bg-canvas p-4">
            <p className="mb-3 text-label text-ink-secondary">1. Thông tin định danh</p>
            <FormField
              error={fieldErrors.displayName}
              htmlFor="register-display-name"
              label="Tên hiển thị"
              required
            >
              <Input
                autoComplete="name"
                disabled={submitting}
                maxLength={101}
                name="displayName"
                onChange={(event) => onDisplayNameChange(event.target.value)}
                value={values.displayName}
              />
            </FormField>

            <FormField
              className="mb-0"
              error={fieldErrors.email}
              htmlFor="register-email"
              label="Email"
              required
            >
              <Input
                autoComplete="email"
                disabled={submitting}
                inputMode="email"
                maxLength={256}
                name="email"
                onChange={(event) => onEmailChange(event.target.value)}
                type="email"
                value={values.email}
              />
            </FormField>
            {notice?.kind === 'email-exists' ? (
              <div className="mt-3 flex flex-wrap items-center justify-between gap-2 rounded-sm border border-brand-100 bg-surface-subtle px-3 py-2 text-body-sm text-ink-secondary">
                <span>Bạn đã có tài khoản?</span>
                <Button size="sm" to={loginTo} variant="ghost">
                  Đăng nhập
                </Button>
              </div>
            ) : null}
          </div>

          <div className="rounded-md border border-line bg-canvas p-4">
            <p className="mb-3 text-label text-ink-secondary">2. Thiết lập mật khẩu</p>
            <FormField
              error={fieldErrors.password}
              hint={NEW_PASSWORD_REQUIREMENT}
              htmlFor="register-password"
              label="Mật khẩu"
              required
            >
              <PasswordInput
                autoComplete="new-password"
                disabled={submitting}
                maxLength={73}
                name="password"
                onChange={(event) => onPasswordChange(event.target.value)}
                value={values.password}
              />
            </FormField>

            <FormField
              className="mb-0"
              error={fieldErrors.confirmPassword}
              htmlFor="register-confirm-password"
              label="Nhập lại mật khẩu"
              required
            >
              <PasswordInput
                autoComplete="new-password"
                disabled={submitting}
                maxLength={73}
                name="confirmPassword"
                onChange={(event) => onConfirmPasswordChange(event.target.value)}
                value={values.confirmPassword}
              />
            </FormField>
          </div>

          <div className="rounded-md border border-brand-100 bg-surface-subtle px-3.5 py-2.5">
            <label
              className="flex min-h-touch cursor-pointer items-start gap-3 rounded-sm py-2 text-body-sm text-ink focus-within:ring-2 focus-within:ring-focus focus-within:ring-offset-2"
              htmlFor="register-accept-terms"
            >
              <input
                aria-describedby={termsErrorId}
                aria-invalid={fieldErrors.acceptTerms ? true : undefined}
                aria-required="true"
                checked={values.acceptTerms}
                className="mt-0.5 size-5 shrink-0 accent-brand-600"
                disabled={submitting}
                id="register-accept-terms"
                name="acceptTerms"
                onChange={(event) => onAcceptTermsChange(event.target.checked)}
                type="checkbox"
              />
              <span>
                Tôi đồng ý với{' '}
                <Link
                  className="font-semibold text-brand-700 underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
                  rel="noopener"
                  target="_blank"
                  to="/terms"
                >
                  điều khoản sử dụng và chính sách dữ liệu
                </Link>
                .
              </span>
            </label>
            {fieldErrors.acceptTerms ? (
              <p
                className="mt-1 flex items-start gap-1 text-body-sm font-semibold text-danger"
                id={termsErrorId}
              >
                <CircleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
                <span>{fieldErrors.acceptTerms}</span>
              </p>
            ) : null}
          </div>

          <Button className="w-full" loading={submitting} type="submit">
            {submitting ? 'Đang gửi mã xác minh' : 'Đăng ký'}
          </Button>
        </form>

        <div className="mt-6 flex flex-wrap items-center justify-between gap-3 rounded-md border border-brand-100 bg-surface-subtle p-4 text-body-sm text-ink-secondary">
          <span>Đã có tài khoản?</span>
          <Button size="sm" to={loginTo} variant="outline">
            Đăng nhập
          </Button>
        </div>
      </AuthSplitLayout>
  )
}
