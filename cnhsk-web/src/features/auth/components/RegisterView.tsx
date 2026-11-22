import { CircleAlert } from 'lucide-react'
import type { FormEvent } from 'react'

import { Button, FormField, Input, PasswordInput } from '@/shared/ui'

import { NEW_PASSWORD_REQUIREMENT } from '../passwordValidation'
import type { RegisterFieldErrors, RegisterNotice } from '../registerForm'
import type { RegisterRequest } from '../types'
import { useFocusFirstInvalid } from '../useFocusFirstInvalid'
import { AuthHeading } from './AuthHeading'
import { AuthInkWashStage } from './AuthInkWashStage'
import { AuthLink } from './AuthLink'
import { AuthNotice } from './AuthNotice'
import { PasswordRules } from './PasswordRules'

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

const RULES_ID = 'register-password-rules'

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
  const { form, markSubmitted } = useFocusFirstInvalid(fieldErrors, submitting)
  const hasFieldError = Object.values(fieldErrors).some(Boolean)
  const rulesBroken = fieldErrors.password === NEW_PASSWORD_REQUIREMENT
  const termsErrorId = fieldErrors.acceptTerms ? 'register-accept-terms-error' : undefined
  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    markSubmitted()
    onSubmit()
  }

  return (
    <AuthInkWashStage variant="003">
      <AuthHeading seal="起步" title="Đăng ký">
        Đã có tài khoản? <AuthLink to={loginTo}>Đăng nhập</AuthLink>
      </AuthHeading>

      {notice?.kind === 'generic' ? (
        <AuthNotice requestId={notice.requestId}>{notice.message}</AuthNotice>
      ) : null}
      {notice?.kind === 'validation' && !hasFieldError ? (
        <AuthNotice>Vui lòng kiểm tra lại thông tin đã nhập.</AuthNotice>
      ) : null}

      <form className="space-y-3" noValidate onSubmit={handleSubmit} ref={form}>
        <FormField
          className="mb-0"
          error={fieldErrors.displayName}
          htmlFor="register-display-name"
          label="Tên hiển thị"
        >
          <Input
            aria-required
            autoComplete="name"
            disabled={submitting}
            maxLength={101}
            name="displayName"
            onChange={(event) => onDisplayNameChange(event.target.value)}
            value={values.displayName}
          />
        </FormField>

        <FormField className="mb-0" error={fieldErrors.email} htmlFor="register-email" label="Email">
          <Input
            aria-required
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

        <div className="grid gap-3 sm:grid-cols-2">
          <FormField
            className="mb-0"
            error={rulesBroken ? undefined : fieldErrors.password}
            htmlFor="register-password"
            label="Mật khẩu"
          >
            <PasswordInput
              aria-describedby={RULES_ID}
              aria-invalid={rulesBroken || undefined}
              aria-required
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
          >
            <PasswordInput
              aria-required
              autoComplete="new-password"
              disabled={submitting}
              maxLength={73}
              name="confirmPassword"
              onChange={(event) => onConfirmPasswordChange(event.target.value)}
              value={values.confirmPassword}
            />
          </FormField>
        </div>
        <PasswordRules id={RULES_ID} invalid={rulesBroken} password={values.password} />

        <div>
          <label
            className="flex min-h-touch cursor-pointer items-start gap-2.5 pt-2 text-body-sm text-ink"
            htmlFor="register-accept-terms"
          >
            <input
              aria-describedby={termsErrorId}
              aria-invalid={fieldErrors.acceptTerms ? true : undefined}
              aria-required="true"
              checked={values.acceptTerms}
              className="size-5 shrink-0 accent-brand-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
              disabled={submitting}
              id="register-accept-terms"
              name="acceptTerms"
              onChange={(event) => onAcceptTermsChange(event.target.checked)}
              type="checkbox"
            />
            <span>
              Tôi đồng ý với{' '}
              <AuthLink rel="noopener" target="_blank" to="/terms">
                điều khoản sử dụng và chính sách dữ liệu
              </AuthLink>
              .
            </span>
          </label>
          {fieldErrors.acceptTerms ? (
            <p
              className="flex items-start gap-1 text-body-sm font-semibold text-danger"
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
    </AuthInkWashStage>
  )
}
