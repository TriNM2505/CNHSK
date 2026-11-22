import { CircleAlert } from 'lucide-react'
import { useEffect, useRef, type FormEvent } from 'react'

import { Button, FormField, Input, PageHeader } from '@/shared/ui'

import type { RegisterCodeNotice } from '../registerCode'
import { AuthSplitLayout } from './AuthSplitLayout'

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

const CODE_ASIDE = {
  badge: 'Còn một bước',
  body: 'Mã xác minh giúp chắc chắn email là của bạn, để lấy lại mật khẩu và nhận nhắc học đúng địa chỉ.',
  hanzi: '确认',
  meaning: 'Xác nhận',
  pinyin: 'quèrèn',
  title: 'Xác minh email của bạn',
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
    <AuthSplitLayout aside={CODE_ASIDE} backgroundVariant="003" compact hideMobileBanner>
      <PageHeader subtitle={`Mã 6 số đã được gửi tới ${email}.`} title="Nhập mã xác minh" />

      {notice?.kind === 'error' ? (
        <div
          className="mb-5 flex items-start gap-3 rounded-md border border-danger bg-danger-bg p-4 text-body-sm text-danger"
          role="alert"
        >
          <CircleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          <div className="min-w-0">
            <p>{notice.message}</p>
            {notice.requestId ? (
              <p className="mt-1 text-caption">Mã lỗi: {notice.requestId}</p>
            ) : null}
          </div>
        </div>
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
          hint="Mã có hiệu lực 10 phút. Chưa thấy thư, hãy xem cả mục thư rác."
          htmlFor="register-code"
          label="Mã xác minh"
          required
        >
          <Input
            autoComplete="one-time-code"
            className="text-h3 tracking-widest"
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

      <div className="mt-5 flex flex-col-reverse gap-3 sm:flex-row sm:items-center sm:justify-between">
        <Button disabled={submitting} onClick={onBack} variant="ghost">
          Sửa thông tin
        </Button>
        <Button
          disabled={submitting || resendInSeconds > 0}
          loading={sending}
          onClick={onResend}
          variant="outline"
        >
          {resendInSeconds > 0 ? `Gửi lại mã sau ${resendInSeconds} giây` : 'Gửi lại mã'}
        </Button>
      </div>
    </AuthSplitLayout>
  )
}
