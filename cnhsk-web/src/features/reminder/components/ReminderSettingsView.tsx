import { Bell, Clock } from 'lucide-react'
import type { FormEvent } from 'react'

import type { ApiQueryState } from '@/shared/api'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  FormField,
  PageBanner,
  Select,
  Skeleton,
  StatusBadge,
} from '@/shared/ui'

import type { ReminderUpdate } from '../types'

const HOURS = Array.from({ length: 24 }, (_, hour) => `${String(hour).padStart(2, '0')}:00`)

export interface ReminderSettingsViewProps {
  configured: boolean
  emailError?: string
  emailVerified: boolean
  failure?: { message: string; requestId?: string }
  onCancel: () => void
  onChange: <K extends keyof ReminderUpdate>(field: K, value: ReminderUpdate[K]) => void
  onRetryQuery: () => void
  onSubmit: () => void
  queryError?: { message: string; requestId?: string }
  queryState: ApiQueryState
  saving: boolean
  success?: string
  timeError?: string
  values?: ReminderUpdate
}

/** Giao diện trình bày SCR-009 (Cài đặt nhắc học) đồng bộ phong cách SCR-010 và SCR-007/008. */
export function ReminderSettingsView({
  configured,
  emailError,
  emailVerified,
  failure,
  onCancel,
  onChange,
  onRetryQuery,
  onSubmit,
  queryError,
  queryState,
  saving,
  success,
  timeError,
  values,
}: ReminderSettingsViewProps) {
  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onSubmit()
  }

  return (
    <main className="mx-auto w-full min-w-0 max-w-4xl space-y-6 px-4 py-6 pb-16 sm:px-6">
      <PageBanner
        subtitle="Chọn giờ và kênh nhắc để quay lại ôn tập đều đặn."
        title="Cài đặt nhắc học"
      />

      {queryState === 'loading' ? <Skeleton variant="card" /> : null}

      {queryState === 'error' ? (
        <ErrorState
          message={queryError?.message ?? 'Không tải được cài đặt nhắc học.'}
          onRetry={onRetryQuery}
          requestId={queryError?.requestId}
        />
      ) : null}

      {queryState === 'empty' ? (
        <EmptyState
          action={<Button onClick={onRetryQuery}>Tải lại</Button>}
          description="Vui lòng tải lại để chọn giờ và kênh nhắc."
          title="Chưa tải được cài đặt nhắc học"
        />
      ) : null}

      {queryState === 'ready' && values ? (
        <div className="space-y-6">
          <Card className="flex flex-wrap items-center justify-between gap-4 border-brand-100 bg-surface-subtle p-5">
            <div className="flex min-w-0 items-center gap-3.5">
              <span
                aria-hidden="true"
                className="flex size-11 shrink-0 items-center justify-center rounded-pill bg-brand-50 text-brand-600"
              >
                <Clock className="size-5" />
              </span>
              <div className="min-w-0 space-y-0.5">
                <p className="text-label text-ink">Múi giờ: Giờ Việt Nam (UTC+7)</p>
                <p className="text-body-sm text-ink-secondary">
                  {!configured
                    ? 'Bạn chưa cài đặt nhắc học. Giờ nhắc mặc định là 20:00, bật nhắc trên Web.'
                    : 'Hệ thống kiểm tra thẻ ôn đến hạn theo mốc giờ tròn mỗi ngày.'}
                </p>
              </div>
            </div>
            {configured ? (
              <StatusBadge tone="success">Đã thiết lập lịch nhắc</StatusBadge>
            ) : (
              <StatusBadge tone="warning">Chưa thiết lập</StatusBadge>
            )}
          </Card>

          <Card className="p-5 shadow-sm sm:p-7">
            <div className="mb-5 flex items-center gap-2.5 border-b border-line pb-4">
              <Bell aria-hidden="true" className="size-5 shrink-0 text-brand-600" />
              <h2 className="text-h3 text-ink">Lịch nhắc &amp; kênh thông báo</h2>
            </div>

            <form className="space-y-5" noValidate onSubmit={handleSubmit}>
              <div className="lg:max-w-xs">
                <FormField
                  className="mb-0"
                  error={timeError}
                  htmlFor="reminder-time"
                  label="Giờ nhắc"
                  required
                >
                  <Select
                    disabled={saving}
                    onChange={(event) => onChange('reminderTime', event.target.value)}
                    value={values.reminderTime}
                  >
                    {HOURS.map((hour) => (
                      <option key={hour} value={hour}>
                        {hour}
                      </option>
                    ))}
                  </Select>
                </FormField>
              </div>

              <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                <div className="rounded-md border border-line bg-canvas p-4">
                  <FormField
                    className="mb-0"
                    hint="Hiện biểu ngữ trên trang học từ giờ nhắc nếu còn thẻ cần ôn."
                    htmlFor="reminder-web"
                    label="Nhắc trên Web"
                  >
                    <Button
                      aria-checked={values.web}
                      aria-label="Web"
                      disabled={saving}
                      onClick={() => onChange('web', !values.web)}
                      role="switch"
                      variant={values.web ? 'secondary' : 'outline'}
                    >
                      {values.web ? 'Đang bật' : 'Đang tắt'}
                    </Button>
                  </FormField>
                </div>

                <div className="rounded-md border border-line bg-canvas p-4">
                  <FormField
                    className="mb-0"
                    error={emailError}
                    hint="Gửi email nhắc khi bạn có thẻ ôn tập đến hạn vào giờ đã chọn."
                    htmlFor="reminder-email"
                    label="Nhắc qua Email"
                  >
                    <Button
                      aria-checked={values.email}
                      aria-label="Email"
                      disabled={saving || !emailVerified}
                      onClick={() => onChange('email', !values.email)}
                      role="switch"
                      variant={values.email ? 'secondary' : 'outline'}
                    >
                      {values.email ? 'Đang bật' : 'Đang tắt'}
                    </Button>
                  </FormField>
                </div>
              </div>

              {!emailVerified ? (
                <div className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-warning bg-warning-bg px-4 py-3 text-body-sm text-ink">
                  <p>Xác thực email để nhận nhắc học qua email.</p>
                  <Button size="sm" to="/verify-email?redirect=%2Fme%2Freminders" variant="outline">
                    Xác thực email
                  </Button>
                </div>
              ) : null}

              {success ? (
                <p
                  className="rounded-md border border-success bg-success-bg px-4 py-3 text-body-sm text-success"
                  role="status"
                >
                  {success}
                </p>
              ) : null}

              {failure ? (
                <div role="alert">
                  <ErrorState
                    message={failure.message}
                    requestId={failure.requestId}
                    title="Chưa lưu được cài đặt"
                  />
                </div>
              ) : null}

              <div className="flex flex-col gap-3 border-t border-line pt-5 sm:flex-row">
                <Button loading={saving} type="submit">
                  Lưu
                </Button>
                <Button disabled={saving} onClick={onCancel} variant="outline">
                  Huỷ
                </Button>
              </div>
            </form>
          </Card>
        </div>
      ) : null}
    </main>
  )
}
