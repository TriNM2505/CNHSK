import { CircleAlert, Link2Off } from 'lucide-react'
import type { FormEvent } from 'react'

import { Button, FormField, PasswordInput } from '@/shared/ui'

import type {
  ResetPasswordFieldErrors,
  ResetPasswordNotice,
  ResetPasswordValues,
} from '../passwordResetForm'
import { NEW_PASSWORD_REQUIREMENT } from '../passwordValidation'
import { AuthSplitLayout } from './AuthSplitLayout'

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

const ASIDE = {
  body: 'Sau khi đặt lại, mọi phiên đăng nhập cũ trên các thiết bị sẽ bị đăng xuất để bảo vệ tài khoản.',
  title: 'Đặt mật khẩu mới an toàn',
}

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
  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onSubmit()
  }

  if (!linkUsable || notice?.kind === 'invalid-token') {
    return (
      <AuthSplitLayout aside={ASIDE}>
        <p className="mb-2 flex items-center gap-2 text-label text-warning">
          <Link2Off aria-hidden="true" className="size-5" />
          <span>Liên kết không dùng được</span>
        </p>
        <h1 className="text-h1-sm text-ink sm:text-h1">Liên kết đặt lại không hợp lệ</h1>
        <p className="mb-6 mt-2 text-body text-ink-secondary" role="alert">
          Liên kết đặt lại mật khẩu không hợp lệ, đã hết hạn hoặc đã được dùng. Liên kết chỉ có hiệu
          lực 1 giờ và dùng được một lần.
        </p>
        <Button to={forgotTo}>Gửi liên kết mới</Button>
      </AuthSplitLayout>
    )
  }

  return (
    <AuthSplitLayout aside={ASIDE}>
      <h1 className="text-h1-sm text-ink sm:text-h1">Đặt lại mật khẩu</h1>
      <p className="mb-6 mt-2 text-body text-ink-secondary">
        Nhập mật khẩu mới cho tài khoản của bạn.
      </p>

      {notice ? (
        <div
          className="mb-5 flex items-start gap-3 rounded-md border border-danger bg-danger-bg p-4 text-body-sm text-danger"
          role="alert"
        >
          <CircleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          <div className="min-w-0">
            <p className="font-semibold">Chưa đặt lại được mật khẩu</p>
            <p className="mt-1">
              {notice.kind === 'generic'
                ? notice.message
                : 'Vui lòng kiểm tra lại các trường được đánh dấu.'}
            </p>
            {notice.kind === 'generic' && notice.requestId ? (
              <p className="mt-1 text-caption">Mã lỗi: {notice.requestId}</p>
            ) : null}
          </div>
        </div>
      ) : null}

      <form noValidate onSubmit={handleSubmit}>
        <FormField
          error={fieldErrors.newPassword}
          hint={NEW_PASSWORD_REQUIREMENT}
          htmlFor="reset-new-password"
          label="Mật khẩu mới"
          required
        >
          <PasswordInput
            autoComplete="new-password"
            disabled={submitting}
            maxLength={73}
            name="newPassword"
            onChange={(event) => onNewPasswordChange(event.target.value)}
            value={values.newPassword}
          />
        </FormField>
        <FormField
          error={fieldErrors.confirmPassword}
          htmlFor="reset-confirm-password"
          label="Nhập lại mật khẩu mới"
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
        <Button className="w-full" loading={submitting} type="submit">
          {submitting ? 'Đang đặt lại' : 'Đặt lại mật khẩu'}
        </Button>
      </form>
    </AuthSplitLayout>
  )
}
