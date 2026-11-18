import { Bell, Clock } from 'lucide-react'
import { useState, type FormEvent } from 'react'

import { toApiError, useApiMutation, useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  FormField,
  Select,
  Skeleton,
  StatusBadge,
} from '@/shared/ui'

import { getReminderSettings, updateReminderSettings } from '../api/reminder'
import { ReminderPageBanner } from '../components/ReminderPageBanner'
import type { ReminderSettings, ReminderUpdate } from '../types'

const HOURS = Array.from({ length: 24 }, (_, hour) => `${String(hour).padStart(2, '0')}:00`)

function formValues(settings: ReminderSettings): ReminderUpdate {
  return { reminderTime: settings.reminderTime, ...settings.channels }
}

function SettingsForm({ initial }: { initial: ReminderSettings }) {
  const mutation = useApiMutation(updateReminderSettings)
  const [saved, setSaved] = useState(initial)
  const [values, setValues] = useState(() => formValues(initial))
  const [emailError, setEmailError] = useState<string>()
  const [timeError, setTimeError] = useState<string>()
  const [success, setSuccess] = useState<string>()
  const [failure, setFailure] = useState<{ message: string; requestId?: string }>()

  function clearNotice() {
    setEmailError(undefined)
    setTimeError(undefined)
    setSuccess(undefined)
    setFailure(undefined)
    mutation.reset()
  }

  function change<K extends keyof ReminderUpdate>(field: K, value: ReminderUpdate[K]) {
    setValues((current) => ({ ...current, [field]: value }))
    clearNotice()
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (mutation.pending) return
    clearNotice()
    try {
      const result = await mutation.run(values)
      setSaved(result)
      setValues(formValues(result))
      setSuccess(
        result.channels.web || result.channels.email
          ? `Bạn sẽ được nhắc lúc ${result.reminderTime}`
          : 'Bạn đã tắt mọi nhắc học',
      )
    } catch (error) {
      const apiError = toApiError(error)
      if (apiError.errorCode === 'EMAIL_NOT_VERIFIED') {
        setEmailError('Vui lòng xác thực email trước khi bật nhắc qua email.')
        setSaved((current) => ({ ...current, emailVerified: false }))
        setValues((current) => ({ ...current, email: false }))
      } else if (apiError.errorCode === 'VALIDATION_FAILED') {
        setTimeError('Vui lòng chọn giờ tròn từ 00:00 đến 23:00.')
      } else {
        setFailure({ message: apiError.message, requestId: apiError.requestId })
      }
    }
  }

  return (
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
              {!saved.configured
                ? 'Bạn chưa cài đặt nhắc học. Giờ nhắc mặc định là 20:00, bật nhắc trên Web.'
                : 'Hệ thống kiểm tra thẻ ôn đến hạn theo mốc giờ tròn mỗi ngày.'}
            </p>
          </div>
        </div>
        {saved.configured ? (
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

        <form className="space-y-5" noValidate onSubmit={(event) => void submit(event)}>
          <div className="lg:max-w-xs">
            <FormField
              className="mb-0"
              error={timeError}
              htmlFor="reminder-time"
              label="Giờ nhắc"
              required
            >
              <Select
                disabled={mutation.pending}
                onChange={(event) => change('reminderTime', event.target.value)}
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
                hint="Hiện biểu ngữ nhắc ôn tập khi bạn mở CNHSK từ giờ nhắc trở đi."
                htmlFor="reminder-web"
                label="Nhắc trên Web"
              >
                <Button
                  aria-checked={values.web}
                  aria-label="Web"
                  disabled={mutation.pending}
                  onClick={() => change('web', !values.web)}
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
                  disabled={mutation.pending || !saved.emailVerified}
                  onClick={() => change('email', !values.email)}
                  role="switch"
                  variant={values.email ? 'secondary' : 'outline'}
                >
                  {values.email ? 'Đang bật' : 'Đang tắt'}
                </Button>
              </FormField>
            </div>
          </div>

          {!saved.emailVerified ? (
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
            <Button loading={mutation.pending} type="submit">
              Lưu
            </Button>
            <Button
              disabled={mutation.pending}
              onClick={() => {
                setValues(formValues(saved))
                clearNotice()
              }}
              variant="outline"
            >
              Huỷ
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}

function SettingsContent() {
  const query = useApiQuery(getReminderSettings, [], { isEmpty: (settings) => !settings })
  return (
    <main className="mx-auto w-full min-w-0 max-w-4xl space-y-6 px-4 py-6 pb-16 sm:px-6">
      <ReminderPageBanner
        subtitle="Chọn giờ và kênh nhắc để quay lại ôn tập đều đặn."
        title="Cài đặt nhắc học"
      />
      {query.state === 'loading' ? <Skeleton variant="card" /> : null}
      {query.state === 'error' ? (
        <ErrorState
          message={query.error?.message ?? 'Không tải được cài đặt nhắc học.'}
          onRetry={query.reload}
          requestId={query.error?.requestId}
        />
      ) : null}
      {query.state === 'empty' ? (
        <EmptyState
          title="Chưa tải được cài đặt nhắc học"
          description="Vui lòng tải lại để chọn giờ và kênh nhắc."
          action={<Button onClick={query.reload}>Tải lại</Button>}
        />
      ) : null}
      {query.state === 'ready' && query.data ? <SettingsForm initial={query.data} /> : null}
    </main>
  )
}

export default function Scr009ReminderSettingsPage() {
  const { status, user } = useCurrentUser()
  if (status !== 'authenticated' || !user?.roles.includes('LEARNER')) return null
  return <SettingsContent key={user.id} />
}
