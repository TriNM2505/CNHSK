import type { FormEvent } from 'react'

import { NEW_PASSWORD_REQUIREMENT } from '@/features/auth/passwordValidation'
import { Button, Card, FormField, PageHeader, PasswordInput, StatusBadge } from '@/shared/ui'

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
    <div className="mx-auto w-full min-w-0 max-w-reading space-y-6 pb-16">
      <PageHeader
        subtitle="Nhập mật khẩu hiện tại để xác nhận, rồi đặt mật khẩu mới cho tài khoản."
        title="Đổi mật khẩu"
      />

      <Card className="bg-surface-subtle p-4 sm:px-6">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <StatusBadge tone="info">Bảo mật phiên đăng nhập</StatusBadge>
        </div>
        <p className="mt-2 text-body-sm text-ink-secondary">
          Đổi mật khẩu thành công sẽ đăng xuất các thiết bị khác; phiên đang dùng vẫn giữ nguyên.
        </p>
      </Card>

      <Card className="p-4 sm:p-6">
        {notice ? <FormNotice notice={notice} /> : null}

        <form noValidate onSubmit={handleSubmit}>
          <FormField
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
          <FormField
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
