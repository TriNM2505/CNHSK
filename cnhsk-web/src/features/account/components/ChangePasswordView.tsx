import { KeyRound, ShieldCheck } from 'lucide-react'
import type { FormEvent } from 'react'

import { NEW_PASSWORD_REQUIREMENT } from '@/features/auth/passwordValidation'
import { Button, Card, FormField, PageBanner, PasswordInput, StatusBadge } from '@/shared/ui'

import type { PasswordFieldErrors } from '../accountForms'
import type { ChangePasswordRequest } from '../types'
import { FormNotice, type FormNoticeValue } from './FormNotice'

export type ChangePasswordViewState = 'ready' | 'submitting'

export interface ChangePasswordViewProps {
  cancelTo: string
  fieldErrors?: PasswordFieldErrors
  notice?: FormNoticeValue
  onChange: (field: keyof ChangePasswordRequest, value: string) => void
  onSubmit: () => void
  state: ChangePasswordViewState
  values: ChangePasswordRequest
}

export function ChangePasswordView({
  cancelTo,
  fieldErrors = {},
  notice,
  onChange,
  onSubmit,
  state,
  values,
}: ChangePasswordViewProps) {
  const submitting = state === 'submitting'
  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onSubmit()
  }

  return (
    <div className="mx-auto w-full min-w-0 max-w-4xl space-y-6 pb-16">
      <PageBanner
        subtitle="Nhập mật khẩu hiện tại để xác nhận, rồi đặt mật khẩu mới cho tài khoản."
        title="Đổi mật khẩu"
      />

      <Card className="flex flex-wrap items-center justify-between gap-4 border-brand-100 bg-surface-subtle p-5">
        <div className="flex min-w-0 items-center gap-3.5">
          <span
            aria-hidden="true"
            className="flex size-11 shrink-0 items-center justify-center rounded-pill bg-brand-50 text-brand-600"
          >
            <ShieldCheck className="size-5" />
          </span>
          <div className="min-w-0 space-y-0.5">
            <p className="text-label text-ink">Bảo mật phiên đăng nhập</p>
            <p className="text-body-sm text-ink-secondary">
              Đổi mật khẩu thành công sẽ đăng xuất các thiết bị khác; phiên đang dùng vẫn giữ
              nguyên.
            </p>
          </div>
        </div>
        <StatusBadge tone="info">Khuyến nghị bảo mật</StatusBadge>
      </Card>

      <Card className="p-5 shadow-sm sm:p-7">
        <div className="mb-5 flex items-center gap-2.5 border-b border-line pb-4">
          <KeyRound aria-hidden="true" className="size-5 shrink-0 text-brand-600" />
          <h2 className="text-h3 text-ink">Thiết lập mật khẩu mới</h2>
        </div>

        {notice ? <FormNotice notice={notice} /> : null}

        <form className="space-y-4" noValidate onSubmit={handleSubmit}>
          <FormField
            className="mb-0"
            error={fieldErrors.currentPassword}
            htmlFor="password-current"
            label="Mật khẩu hiện tại"
            required
          >
            <PasswordInput
              autoComplete="current-password"
              disabled={submitting}
              maxLength={72}
              name="currentPassword"
              onChange={(event) => onChange('currentPassword', event.target.value)}
              value={values.currentPassword}
            />
          </FormField>

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <FormField
              className="mb-0"
              error={fieldErrors.newPassword}
              hint={NEW_PASSWORD_REQUIREMENT}
              htmlFor="password-new"
              label="Mật khẩu mới"
              required
            >
              <PasswordInput
                autoComplete="new-password"
                disabled={submitting}
                maxLength={73}
                name="newPassword"
                onChange={(event) => onChange('newPassword', event.target.value)}
                value={values.newPassword}
              />
            </FormField>
            <FormField
              className="mb-0"
              error={fieldErrors.confirmPassword}
              htmlFor="password-confirm"
              label="Nhập lại mật khẩu mới"
              required
            >
              <PasswordInput
                autoComplete="new-password"
                disabled={submitting}
                maxLength={73}
                name="confirmPassword"
                onChange={(event) => onChange('confirmPassword', event.target.value)}
                value={values.confirmPassword}
              />
            </FormField>
          </div>

          <div className="mt-6 flex flex-wrap items-center gap-3 border-t border-line pt-5">
            <Button loading={submitting} type="submit">
              {submitting ? 'Đang đổi mật khẩu' : 'Đổi mật khẩu'}
            </Button>
            <Button to={cancelTo} variant="outline">
              Huỷ
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
