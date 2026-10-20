import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import { useApiMutation } from '@/shared/api'

import { register } from '../api/register'
import { RegisterView } from '../components/RegisterView'
import {
  getRegisterFailure,
  normalizeRegisterRequest,
  type RegisterFieldErrors,
  type RegisterNotice,
  validateRegister,
} from '../registerForm'
import type { RegisterRequest } from '../types'

const EMPTY_VALUES: RegisterRequest = {
  acceptTerms: false,
  confirmPassword: '',
  displayName: '',
  email: '',
  password: '',
}

function loginPath(redirect: string | null): string {
  if (!redirect) return '/login'
  return `/login?redirect=${encodeURIComponent(redirect)}`
}

export default function Scr003RegisterPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const redirect = searchParams.get('redirect')
  const mutation = useApiMutation(register)
  const [values, setValues] = useState<RegisterRequest>(EMPTY_VALUES)
  const [fieldErrors, setFieldErrors] = useState<RegisterFieldErrors>({})
  const [notice, setNotice] = useState<RegisterNotice>()
  const destinationToLogin = useMemo(() => loginPath(redirect), [redirect])
  const state = mutation.pending
    ? ('submitting' as const)
    : notice || Object.keys(fieldErrors).length
      ? ('error' as const)
      : ('ready' as const)

  const updateField = <Field extends keyof RegisterRequest>(
    field: Field,
    value: RegisterRequest[Field],
  ) => {
    setValues((current) => ({ ...current, [field]: value }))
    setFieldErrors((current) => ({ ...current, [field]: undefined }))
    setNotice(undefined)
  }

  const submit = async () => {
    if (mutation.pending) return

    const validationErrors = validateRegister(values)
    if (Object.keys(validationErrors).length) {
      setFieldErrors(validationErrors)
      setNotice({ kind: 'validation' })
      return
    }

    setFieldErrors({})
    setNotice(undefined)

    try {
      const response = await mutation.run(normalizeRegisterRequest(values))
      navigate('/verify-email', {
        replace: true,
        state: { email: response.email, redirect },
      })
    } catch (error) {
      const failure = getRegisterFailure(error)
      setFieldErrors(failure.fieldErrors)
      setNotice(failure.notice)
    }
  }

  return (
    <RegisterView
      fieldErrors={fieldErrors}
      loginTo={destinationToLogin}
      notice={notice}
      onAcceptTermsChange={(value) => updateField('acceptTerms', value)}
      onConfirmPasswordChange={(value) => updateField('confirmPassword', value)}
      onDisplayNameChange={(value) => updateField('displayName', value)}
      onEmailChange={(value) => updateField('email', value)}
      onPasswordChange={(value) => updateField('password', value)}
      onSubmit={() => void submit()}
      state={state}
      values={values}
    />
  )
}
