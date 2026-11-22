import type { FormEvent } from 'react'

import { Button, FormField, PasswordInput } from '@/shared/ui'

import type {
  ResetPasswordFieldErrors,
  ResetPasswordNotice,
  ResetPasswordValues,
} from '../passwordResetForm'
import { NEW_PASSWORD_REQUIREMENT } from '../passwordValidation'
import { useFocusFirstInvalid } from '../useFocusFirstInvalid'
import { AuthHeading } from './AuthHeading'
import { AuthInkWashStage } from './AuthInkWashStage'
import { AuthNotice } from './AuthNotice'
import { PasswordRules } from './PasswordRules'

export type ResetPasswordViewState = 'ready' | 'submitting' | 'error'

export interface ResetPasswordViewProps {
  fieldErrors?: ResetPasswordFieldErrors
  forgotTo: string
  /** Không có token trong link, hoặc server báo token hỏng (FR-042). */
  linkUsable: boolean
  notice?: ResetPasswordNotice
  onConfirmPasswordChange: (value: string) => void
  onNewPasswordChange: (value: string) => void
  onSubmit: () => void
  state: ResetPasswordViewState
  values: ResetPasswordValues
}

const RULES_ID = 'reset-password-rules'

export function ResetPasswordView({
  fieldErrors = {},
  forgotTo,
  linkUsable,
  notice,
  onConfirmPasswordChange,
  onNewPasswordChange,
  onSubmit,
  state,
  values,
}: ResetPasswordViewProps) {
  const submitting = state === 'submitting'
  const { form, markSubmitted } = useFocusFirstInvalid(fieldErrors, submitting)
  const hasFieldError = Object.values(fieldErrors).some(Boolean)
  const rulesBroken = fieldErrors.newPassword === NEW_PASSWORD_REQUIREMENT
  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    markSubmitted()
    onSubmit()
  }

  if (!linkUsable || notice?.kind === 'invalid-token') {
    return (
      <AuthInkWashStage variant="006">
        <AuthHeading seal="更新" title="Liên kết đặt lại không hợp lệ" />
        <p className="text-body text-ink" role="alert">
          Liên kết đã hết hạn hoặc đã được dùng. Mỗi liên kết chỉ có hiệu lực 1 giờ và dùng được một
          lần.
        </p>
        <Button className="mt-6 w-full" to={forgotTo}>
          Gửi liên kết mới
        </Button>
      </AuthInkWashStage>
    )
  }

  return (
    <AuthInkWashStage variant="006">
      <AuthHeading seal="更新" title="Đặt lại mật khẩu">
        Sau khi đổi, mọi thiết bị đang đăng nhập sẽ phải đăng nhập lại.
      </AuthHeading>

      {notice?.kind === 'generic' ? (
        <AuthNotice requestId={notice.requestId}>{notice.message}</AuthNotice>
      ) : null}
      {notice?.kind === 'validation' && !hasFieldError ? (
        <AuthNotice>Vui lòng kiểm tra lại mật khẩu đã nhập.</AuthNotice>
      ) : null}

      <form className="space-y-4" noValidate onSubmit={handleSubmit} ref={form}>
        <div className="space-y-2">
          <FormField
            className="mb-0"
            error={rulesBroken ? undefined : fieldErrors.newPassword}
            htmlFor="reset-new-password"
            label="Mật khẩu mới"
          >
            <PasswordInput
              aria-describedby={RULES_ID}
              aria-invalid={rulesBroken || undefined}
              aria-required
              autoComplete="new-password"
              disabled={submitting}
              maxLength={73}
              name="newPassword"
              onChange={(event) => onNewPasswordChange(event.target.value)}
              value={values.newPassword}
            />
          </FormField>
          <PasswordRules id={RULES_ID} invalid={rulesBroken} password={values.newPassword} />
        </div>
        <FormField
          className="mb-0"
          error={fieldErrors.confirmPassword}
          htmlFor="reset-confirm-password"
          label="Nhập lại mật khẩu mới"
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

        <Button className="w-full" loading={submitting} type="submit">
          {submitting ? 'Đang đặt lại' : 'Đặt lại mật khẩu'}
        </Button>
      </form>
    </AuthInkWashStage>
  )
}
