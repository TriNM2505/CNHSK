import type { FormEvent } from 'react'

import { NEW_PASSWORD_REQUIREMENT } from '@/features/auth/passwordValidation'
import { Button, Card, FormField, PageHeader, PasswordInput } from '@/shared/ui'

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
    <div className="mx-auto w-full max-w-reading">
      <PageHeader
        subtitle="Nhập mật khẩu hiện tại để xác nhận, rồi đặt mật khẩu mới. Các thiết bị khác sẽ bị đăng xuất."
        title="Đổi mật khẩu"
      />
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

          <div className="flex flex-wrap items-center gap-3">
            <Button loading={submitting} type="submit">
              {submitting ? 'Đang đổi mật khẩu' : 'Đổi mật khẩu'}
            </Button>
            <Button to={cancelTo} variant="ghost">
              Huỷ
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
