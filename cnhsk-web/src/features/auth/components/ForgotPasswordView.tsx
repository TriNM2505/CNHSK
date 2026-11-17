import { CircleAlert, Clock3, MailCheck } from 'lucide-react'
import type { FormEvent } from 'react'

import { Button, FormField, Input, PageHeader, StatusBadge } from '@/shared/ui'

import { AuthSplitLayout } from './AuthSplitLayout'
import { CardCornerPandas, LoginBambooBackdrop, RightGiantPandaScene } from './PandaBambooScene'

export type ForgotPasswordState = 'ready' | 'submitting' | 'sent' | 'error'

export interface ForgotPasswordViewProps {
  email: string
  emailError?: string
  error?: { message: string; requestId?: string }
  loginTo: string
  onEmailChange: (value: string) => void
  onSendAgain: () => void
  onSubmit: () => void
  /** Email đã gửi yêu cầu — hiển thị lại trong thông báo chung (FR-038). */
  sentTo?: string
  state: ForgotPasswordState
}

const ASIDE = {
  badge: 'Bảo mật tài khoản',
  body: 'Dù tài khoản có tồn tại hay không, bạn đều nhận cùng một thông báo. Cách này giúp người khác không dò được email nào đã đăng ký.',
  hanzi: '找回',
  meaning: 'Khôi phục quyền truy cập an toàn',
  pinyin: 'zhǎohuí',
  title: 'Phản hồi giống nhau cho mọi trường hợp',
}

export function ForgotPasswordView({
  email,
  emailError,
  error,
  loginTo,
  onEmailChange,
  onSendAgain,
  onSubmit,
  sentTo,
  state,
}: ForgotPasswordViewProps) {
  const submitting = state === 'submitting'
  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onSubmit()
  }

  if (state === 'sent') {
    return (
      <div className="relative isolate overflow-hidden">
        <LoginBambooBackdrop />
        <AuthSplitLayout
          aside={ASIDE}
          cardDecorator={<CardCornerPandas />}
          customAside={<RightGiantPandaScene mood="question" />}
          hideMobileBanner
        >
          <div className="mb-4 flex flex-wrap items-center justify-between gap-2 border-b border-line pb-4">
            <p className="text-label text-brand-700">Khôi phục mật khẩu</p>
            <StatusBadge tone="success">Đã nhận yêu cầu</StatusBadge>
          </div>
          <PageHeader
            subtitle="Hướng dẫn khôi phục sẽ được gửi nếu địa chỉ email đã đăng ký."
            title="Kiểm tra hộp thư của bạn"
          />
          <div
            className="flex items-start gap-3 rounded-md border border-success bg-success-bg p-4 text-body-sm text-ink"
            role="status"
          >
            <MailCheck aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-success" />
            <p className="min-w-0 break-words">
              Nếu <strong className="font-semibold text-ink">{sentTo}</strong> có tài khoản trong hệ
              thống, hướng dẫn đặt lại mật khẩu sẽ được gửi tới đó trong vài phút. Liên kết có hiệu
              lực 1 giờ.
            </p>
          </div>
          <div className="mt-6 flex flex-wrap gap-3 border-t border-line pt-5">
            <Button to={loginTo}>Quay lại đăng nhập</Button>
            <Button onClick={onSendAgain} variant="outline">
              Gửi lại với email khác
            </Button>
          </div>
        </AuthSplitLayout>
      </div>
    )
  }

  return (
    <div className="relative isolate overflow-hidden">
      <LoginBambooBackdrop />
      <AuthSplitLayout
        aside={ASIDE}
        cardDecorator={<CardCornerPandas />}
        customAside={<RightGiantPandaScene mood="question" />}
        hideMobileBanner
      >
        <div className="mb-4 flex flex-wrap items-center justify-between gap-2 border-b border-line pb-4">
          <p className="text-label text-brand-700">Hỗ trợ đăng nhập</p>
          <StatusBadge tone="neutral">Khôi phục mật khẩu</StatusBadge>
        </div>
        <PageHeader
          subtitle="Nhập email của tài khoản. Nếu email có trong hệ thống, chúng tôi gửi hướng dẫn đặt lại mật khẩu."
          title="Quên mật khẩu"
        />

        {state === 'error' && error ? (
          <div
            className="mb-5 flex items-start gap-3 rounded-md border border-danger bg-danger-bg p-4 text-body-sm text-danger"
            role="alert"
          >
            <CircleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
            <div className="min-w-0">
              <p className="font-semibold">Chưa gửi được yêu cầu</p>
              <p className="mt-1">{error.message}</p>
              {error.requestId ? (
                <p className="mt-1 text-caption">Mã lỗi: {error.requestId}</p>
              ) : null}
            </div>
          </div>
        ) : null}

        <form className="space-y-4" noValidate onSubmit={handleSubmit}>
          <div className="rounded-md border border-line bg-canvas p-4">
            <FormField
              className="mb-0"
              error={emailError}
              hint="Sử dụng địa chỉ email bạn đã đăng ký với CNHSK."
              htmlFor="forgot-email"
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
                value={email}
              />
            </FormField>
          </div>

          <div className="flex items-center gap-2 rounded-sm bg-surface-subtle px-3 py-2 text-caption text-ink-secondary">
            <Clock3 aria-hidden="true" className="size-4 shrink-0 text-brand-700" />
            <span>Liên kết đặt lại mật khẩu có hiệu lực trong 1 giờ và chỉ dùng 1 lần.</span>
          </div>

          <Button className="w-full" loading={submitting} type="submit">
            {submitting ? 'Đang gửi' : 'Gửi hướng dẫn đặt lại mật khẩu'}
          </Button>
        </form>

        <div className="mt-6 flex flex-wrap items-center justify-between gap-3 rounded-md border border-brand-100 bg-surface-subtle p-4 text-body-sm text-ink-secondary">
          <span>Nhớ ra mật khẩu rồi?</span>
          <Button size="sm" to={loginTo} variant="outline">
            Quay lại đăng nhập
          </Button>
        </div>
      </AuthSplitLayout>
    </div>
  )
}
