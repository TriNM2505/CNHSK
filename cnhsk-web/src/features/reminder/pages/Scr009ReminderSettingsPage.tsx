import { useState } from 'react'

import { toApiError, useApiMutation, useApiQuery, type ApiQueryState } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'

import { getReminderSettings, updateReminderSettings } from '../api/reminder'
import { ReminderSettingsView } from '../components/ReminderSettingsView'
import type { ReminderSettings, ReminderUpdate } from '../types'

function formValues(settings: ReminderSettings): ReminderUpdate {
  return { reminderTime: settings.reminderTime, ...settings.channels }
}

function SettingsForm({
  initial,
  onRetryQuery,
  queryState,
}: {
  initial: ReminderSettings
  onRetryQuery: () => void
  queryState: ApiQueryState
}) {
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

  async function submit() {
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

  function cancel() {
    setValues(formValues(saved))
    clearNotice()
  }

  return (
    <ReminderSettingsView
      configured={saved.configured}
      emailError={emailError}
      emailVerified={saved.emailVerified}
      failure={failure}
      onCancel={cancel}
      onChange={change}
      onRetryQuery={onRetryQuery}
      onSubmit={() => void submit()}
      queryState={queryState}
      saving={mutation.pending}
      success={success}
      timeError={timeError}
      values={values}
    />
  )
}

function SettingsContent() {
  const query = useApiQuery(getReminderSettings, [], { isEmpty: (settings) => !settings })

  if (query.state === 'ready' && query.data) {
    return (
      <SettingsForm
        initial={query.data}
        onRetryQuery={query.reload}
        queryState={query.state}
      />
    )
  }

  return (
    <ReminderSettingsView
      configured={false}
      emailVerified={false}
      onCancel={() => {}}
      onChange={() => {}}
      onRetryQuery={query.reload}
      onSubmit={() => {}}
      queryError={query.error}
      queryState={query.state}
      saving={false}
    />
  )
}

export default function Scr009ReminderSettingsPage() {
  const { status, user } = useCurrentUser()
  if (status !== 'authenticated' || !user?.roles.includes('LEARNER')) return null
  return <SettingsContent key={user.id} />
}
