import { useEffect, useRef, type FormEvent } from 'react'

import { Button, FormField, Input } from '@/shared/ui'

import type { RegisterCodeNotice } from '../registerCode'
import { AuthHeading } from './AuthHeading'
import { AuthInkWashStage } from './AuthInkWashStage'
import { AuthNotice } from './AuthNotice'

export interface RegisterCodeViewProps {
  code: string
  email: string
  error?: string
  notice?: RegisterCodeNotice
  onBack: () => void
  onCodeChange: (value: string) => void
  onResend: () => void
  onSubmit: () => void
  resendInSeconds: number
  sending: boolean
  submitting: boolean
}

export function RegisterCodeView({
  code,
  email,
  error,
  notice,
  onBack,
  onCodeChange,
  onResend,
  onSubmit,
  resendInSeconds,
  sending,
  submitting,
}: RegisterCodeViewProps) {
  const form = useRef<HTMLFormElement>(null)
  useEffect(() => form.current?.querySelector('input')?.focus(), [])

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onSubmit()
  }

  return (
    <AuthInkWashStage variant="004">
      <AuthHeading seal="确认" title="Nhập mã xác minh">
        {`Mã 6 số đã được gửi tới ${email}.`}
      </AuthHeading>

      {notice?.kind === 'error' ? (
        <AuthNotice requestId={notice.requestId}>{notice.message}</AuthNotice>
      ) : null}
      <p aria-live="polite" className="text-body-sm text-success">
        {notice?.kind === 'resent' ? (
          <span className="mb-4 block">Đã gửi mã mới. Mã cũ không dùng được nữa.</span>
        ) : null}
      </p>

      <form className="space-y-4" noValidate onSubmit={handleSubmit} ref={form}>
        <FormField
          className="mb-0"
          error={error}
          hint="Mã có hiệu lực 10 phút. Chưa thấy thư? Xem mục thư rác."
          htmlFor="register-code"
          label="Mã xác minh"
        >
          <Input
            aria-required
            autoComplete="one-time-code"
            className="h-14 text-center text-h2 tabular-nums tracking-[0.4em]"
            disabled={submitting}
            inputMode="numeric"
            maxLength={12}
            name="code"
            onChange={(event) => onCodeChange(event.target.value)}
            value={code}
          />
        </FormField>

        <Button className="w-full" loading={submitting} type="submit">
          {submitting ? 'Đang tạo tài khoản' : 'Xác minh và vào học'}
        </Button>
      </form>

      <div className="mt-3 flex flex-wrap items-center justify-between gap-x-3">
        <Button
          className="-ml-4"
          disabled={submitting || resendInSeconds > 0}
          loading={sending}
          onClick={onResend}
          variant="ghost"
        >
          {resendInSeconds > 0 ? `Gửi lại mã sau ${resendInSeconds} giây` : 'Gửi lại mã'}
        </Button>
        <Button className="-mr-4" disabled={submitting} onClick={onBack} variant="ghost">
          Sửa thông tin
        </Button>
      </div>
    </AuthInkWashStage>
  )
}
