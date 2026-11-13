import { useState, type FormEvent } from 'react'

import { toApiError, useApiMutation, useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  FormField,
  PageHeader,
  Select,
  Skeleton,
} from '@/shared/ui'

import { getReminderSettings, updateReminderSettings } from '../api/reminder'
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
    <Card className="p-4 sm:p-6">
      {!saved.configured ? (
        <p className="mb-4 text-body-sm text-ink-secondary">
          Bạn chưa cài đặt nhắc học. Giờ nhắc mặc định là 20:00, bật nhắc trên Web.
        </p>
      ) : null}
      <p className="mb-6 text-body-sm text-ink-secondary">Múi giờ: Giờ Việt Nam (UTC+7)</p>
      <form noValidate onSubmit={(event) => void submit(event)}>
        <FormField error={timeError} htmlFor="reminder-time" label="Giờ nhắc" required>
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
        <FormField htmlFor="reminder-web" label="Nhắc trên Web">
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
        <FormField error={emailError} htmlFor="reminder-email" label="Nhắc qua Email">
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
        {!saved.emailVerified ? (
          <div className="mb-4 text-body-sm text-ink-secondary">
            <p>Xác thực email để nhận nhắc học qua email.</p>
            <Button to="/verify-email?redirect=%2Fme%2Freminders" variant="ghost">
              Xác thực email
            </Button>
          </div>
        ) : null}
        {success ? (
          <p className="mb-4 text-body-sm text-success" role="status">
            {success}
          </p>
        ) : null}
        {failure ? (
          <div className="mb-4" role="alert">
            <ErrorState
              message={failure.message}
              requestId={failure.requestId}
              title="Chưa lưu được cài đặt"
            />
          </div>
        ) : null}
        <div className="flex flex-col gap-3 sm:flex-row">
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
  )
}

function SettingsContent() {
  const query = useApiQuery(getReminderSettings, [], { isEmpty: (settings) => !settings })
  return (
    <main className="mx-auto w-full max-w-reading px-4 py-6 sm:px-6">
      <PageHeader
        title="Cài đặt nhắc học"
        subtitle="Chọn giờ và kênh nhắc để quay lại ôn tập đều đặn."
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
