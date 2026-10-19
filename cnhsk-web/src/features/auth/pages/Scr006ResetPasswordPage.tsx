import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import { useApiMutation } from '@/shared/api'

import { resetPassword } from '../api/passwordReset'
import { ResetPasswordView, type ResetPasswordViewState } from '../components/ResetPasswordView'
import {
  getResetPasswordFailure,
  type ResetPasswordFieldErrors,
  type ResetPasswordNotice,
  type ResetPasswordValues,
  validateResetPassword,
} from '../passwordResetForm'
import type { PasswordResetNavigationState } from '../signInForm'

const EMPTY_VALUES: ResetPasswordValues = { confirmPassword: '', newPassword: '' }

export default function Scr006ResetPasswordPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  // Token chỉ đọc lần đầu rồi xoá khỏi thanh địa chỉ để không nằm lại trong lịch sử trình duyệt.
  const tokenRef = useRef(searchParams.get('token'))
  const mutation = useApiMutation(resetPassword)
  const [values, setValues] = useState<ResetPasswordValues>(EMPTY_VALUES)
  const [fieldErrors, setFieldErrors] = useState<ResetPasswordFieldErrors>({})
  const [notice, setNotice] = useState<ResetPasswordNotice>()

  useEffect(() => {
    if (!searchParams.has('token')) return
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current)
        next.delete('token')
        return next
      },
      { replace: true },
    )
  }, [searchParams, setSearchParams])

  const state: ResetPasswordViewState = mutation.pending
    ? 'submitting'
    : notice || Object.keys(fieldErrors).length
      ? 'error'
      : 'ready'

  const updateField = (field: keyof ResetPasswordValues, value: string) => {
    setValues((current) => ({ ...current, [field]: value }))
    setFieldErrors((current) => ({ ...current, [field]: undefined }))
    if (notice?.kind !== 'invalid-token') setNotice(undefined)
  }

  const submit = async () => {
    const token = tokenRef.current
    if (mutation.pending || !token) return

    const validationErrors = validateResetPassword(values)
    if (Object.keys(validationErrors).length) {
      setFieldErrors(validationErrors)
      setNotice({ kind: 'validation' })
      return
    }

    setFieldErrors({})
    setNotice(undefined)
    try {
      await mutation.run({ ...values, token })
      const state: PasswordResetNavigationState = { passwordReset: true }
      navigate('/login', { replace: true, state })
    } catch (error) {
      const failure = getResetPasswordFailure(error)
      setFieldErrors(failure.fieldErrors)
      setNotice(failure.notice)
    }
  }

  return (
    <ResetPasswordView
      fieldErrors={fieldErrors}
      forgotTo="/forgot-password"
      linkUsable={Boolean(tokenRef.current)}
      notice={notice}
      onConfirmPasswordChange={(value) => updateField('confirmPassword', value)}
      onNewPasswordChange={(value) => updateField('newPassword', value)}
      onSubmit={() => void submit()}
      state={state}
      values={values}
    />
  )
}
