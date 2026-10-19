import { CircleAlert } from 'lucide-react'
import type { FormEvent } from 'react'

import { Button, FormField, Input, PasswordInput } from '@/shared/ui'

import { NEW_PASSWORD_REQUIREMENT } from '../passwordValidation'
import type { RegisterFieldErrors, RegisterNotice } from '../registerForm'
import type { RegisterRequest } from '../types'

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
    <main className="grid min-h-[calc(100vh-4rem)] min-w-0 bg-surface lg:grid-cols-[minmax(0,3fr)_minmax(20rem,2fr)]">
      <section className="flex min-w-0 items-center justify-center px-4 py-10 sm:px-6 lg:px-10">
        <div className="w-full max-w-md">
          <p className="mb-2 text-label text-brand-700">Tạo tài khoản CNHSK</p>
          <h1 className="text-h1-sm text-ink sm:text-h1">Đăng ký</h1>
          <p className="mb-6 mt-2 text-body text-ink-secondary">
            Tạo tài khoản để học và luyện tập trên các thiết bị của bạn.
          </p>

          {notice && notice.kind !== 'email-exists' ? <NoticeBanner notice={notice} /> : null}

          <form noValidate onSubmit={handleSubmit}>
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

            <FormField error={fieldErrors.email} htmlFor="register-email" label="Email" required>
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
              <p className="-mt-2 mb-4 flex flex-wrap items-center gap-1 text-body-sm text-ink-secondary">
                <span>Bạn đã có tài khoản?</span>
                <Button size="sm" to={loginTo} variant="ghost">
                  Đăng nhập
                </Button>
              </p>
            ) : null}

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

            <div className="mb-5">
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
                <span>Tôi đồng ý với điều khoản sử dụng và chính sách dữ liệu.</span>
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
              {submitting ? 'Đang đăng ký' : 'Đăng ký'}
            </Button>
          </form>

          <p className="mt-6 flex flex-wrap items-center justify-center gap-1 text-body-sm text-ink-secondary">
            <span>Đã có tài khoản?</span>
            <Button size="sm" to={loginTo} variant="ghost">
              Đăng nhập
            </Button>
          </p>
        </div>
      </section>

      <aside className="hidden min-w-0 items-center bg-brand-800 px-10 py-12 text-surface lg:flex">
        <div className="mx-auto max-w-md">
          <h2 className="text-h2">Một tài khoản cho hành trình học của bạn</h2>
          <p className="mt-4 text-body">
            Tiếp tục bài học, luyện tập và trò chơi bằng cùng một tài khoản trên các thiết bị.
          </p>
        </div>
      </aside>
    </main>
  )
}
