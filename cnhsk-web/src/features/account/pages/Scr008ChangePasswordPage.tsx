import { useState } from 'react'

import { useApiMutation } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'

import {
  getPasswordChangeFailure,
  type PasswordFieldErrors,
  validatePasswordChange,
} from '../accountForms'
import { changePassword } from '../api/account'
import { ChangePasswordView, type ChangePasswordViewState } from '../components/ChangePasswordView'
import type { FormNoticeValue } from '../components/FormNotice'
import type { ChangePasswordRequest } from '../types'

const EMPTY_VALUES: ChangePasswordRequest = {
  confirmPassword: '',
  currentPassword: '',
  newPassword: '',
}

export default function Scr008ChangePasswordPage() {
  const { user } = useCurrentUser()
  const mutation = useApiMutation(changePassword)
  const [values, setValues] = useState<ChangePasswordRequest>(EMPTY_VALUES)
  const [fieldErrors, setFieldErrors] = useState<PasswordFieldErrors>({})
  const [notice, setNotice] = useState<FormNoticeValue>()

  const state: ChangePasswordViewState = mutation.pending ? 'submitting' : 'ready'
  const cancelTo = user?.roles.includes('LEARNER') ? '/me/profile' : '/'

  const updateField = (field: keyof ChangePasswordRequest, value: string) => {
    setValues((current) => ({ ...current, [field]: value }))
    setFieldErrors((current) => ({ ...current, [field]: undefined }))
    setNotice(undefined)
  }

  const submit = async () => {
    if (mutation.pending) return

    const validationErrors = validatePasswordChange(values, user?.email.trim().toLowerCase() ?? '')
    if (Object.keys(validationErrors).length) {
      setFieldErrors(validationErrors)
      setNotice(undefined)
      return
    }

    setFieldErrors({})
    setNotice(undefined)
    try {
      await mutation.run(values)
      setValues(EMPTY_VALUES)
      setNotice({
        kind: 'success',
        message:
          'Refresh token trên các thiết bị khác đã bị thu hồi; access token cũ có thể còn hiệu lực tối đa 15 phút.',
        title: 'Đã đổi mật khẩu',
      })
    } catch (error) {
      const failure = getPasswordChangeFailure(error)
      setFieldErrors(failure.fieldErrors)
      if (failure.message) {
        setNotice({
          kind: 'error',
          message: failure.message,
          requestId: failure.requestId,
          title: 'Chưa đổi được mật khẩu',
        })
      }
    }
  }

  return (
    <ChangePasswordView
      cancelTo={cancelTo}
      fieldErrors={fieldErrors}
      notice={notice}
      onChange={updateField}
      onSubmit={() => void submit()}
      state={state}
      values={values}
    />
  )
}
