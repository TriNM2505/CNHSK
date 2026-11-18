import { useEffect, useState } from 'react'

import { toApiError, useApiMutation, useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'

import { getReminderSettings, updateReminderSettings } from '../api/reminder'
import { ReminderSettingsView } from '../components/ReminderSettingsView'
import type { ReminderSettings, ReminderUpdate } from '../types'

function formValues(settings: ReminderSettings): ReminderUpdate {
  return { reminderTime: settings.reminderTime, ...settings.channels }
}

function SettingsContent() {
  const query = useApiQuery(getReminderSettings, [], { isEmpty: (settings) => !settings })
  const mutation = useApiMutation(updateReminderSettings)
  const [saved, setSaved] = useState<ReminderSettings>()
  const [values, setValues] = useState<ReminderUpdate>()
  const [emailError, setEmailError] = useState<string>()
  const [timeError, setTimeError] = useState<string>()
  const [success, setSuccess] = useState<string>()
  const [failure, setFailure] = useState<{ message: string; requestId?: string }>()

  useEffect(() => {
    setSaved(undefined)
    setValues(undefined)
  }, [query.data])

  const currentSaved = saved ?? query.data
  const currentValues = values ?? (currentSaved ? formValues(currentSaved) : undefined)

  function clearNotice() {
    setEmailError(undefined)
    setTimeError(undefined)
    setSuccess(undefined)
    setFailure(undefined)
    mutation.reset()
  }

  function change<K extends keyof ReminderUpdate>(field: K, value: ReminderUpdate[K]) {
    if (!currentSaved) return
    setValues((current) => ({ ...(current ?? formValues(currentSaved)), [field]: value }))
    clearNotice()
  }

  async function submit() {
    if (mutation.pending || !currentValues) return
    clearNotice()
    try {
      const result = await mutation.run(currentValues)
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
        setSaved((current) => {
          const base = current ?? currentSaved
          return base ? { ...base, emailVerified: false } : undefined
        })
        setValues((current) => {
          const base = current ?? currentValues
          return base ? { ...base, email: false } : undefined
        })
      } else if (apiError.errorCode === 'VALIDATION_FAILED') {
        setTimeError('Vui lòng chọn giờ tròn từ 00:00 đến 23:00.')
      } else {
        setFailure({ message: apiError.message, requestId: apiError.requestId })
      }
    }
  }

  function cancel() {
    if (!currentSaved) return
    setValues(formValues(currentSaved))
    clearNotice()
  }

  return (
    <ReminderSettingsView
      configured={currentSaved?.configured ?? false}
      emailError={emailError}
      emailVerified={currentSaved?.emailVerified ?? false}
      failure={failure}
      onCancel={cancel}
      onChange={change}
      onRetryQuery={query.reload}
      onSubmit={() => void submit()}
      queryError={query.error}
      queryState={query.state}
      saving={mutation.pending}
      success={success}
      timeError={timeError}
      values={currentValues}
    />
  )
}

export default function Scr009ReminderSettingsPage() {
  const { status, user } = useCurrentUser()
  if (status !== 'authenticated' || !user?.roles.includes('LEARNER')) return null
  return <SettingsContent key={user.id} />
}
