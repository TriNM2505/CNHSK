import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import { useApiMutation } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'

import { register } from '../api/register'
import { signIn } from '../api/signIn'
import { RegisterView } from '../components/RegisterView'
import { navigateAfterSignIn } from '../navigateAfterSignIn'
import {
  getRegisterFailure,
  normalizeRegisterRequest,
  type RegisterFieldErrors,
  type RegisterNotice,
  validateRegister,
} from '../registerForm'
import type { SignInNavigationState } from '../signInForm'
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
  const { setAuthenticatedUser } = useCurrentUser()
  const mutation = useApiMutation(register)
  const [signingIn, setSigningIn] = useState(false)
  const [values, setValues] = useState<RegisterRequest>(EMPTY_VALUES)
  const [fieldErrors, setFieldErrors] = useState<RegisterFieldErrors>({})
  const [notice, setNotice] = useState<RegisterNotice>()
  const destinationToLogin = useMemo(() => loginPath(redirect), [redirect])
  const submitting = mutation.pending || signingIn
  const state = submitting
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
    if (submitting) return

    const validationErrors = validateRegister(values)
    if (Object.keys(validationErrors).length) {
      setFieldErrors(validationErrors)
      setNotice({ kind: 'validation' })
      return
    }

    setFieldErrors({})
    setNotice(undefined)

    const request = normalizeRegisterRequest(values)
    setSigningIn(true)
    try {
      await mutation.run(request)
    } catch (error) {
      const failure = getRegisterFailure(error)
      setFieldErrors(failure.fieldErrors)
      setNotice(failure.notice)
      setSigningIn(false)
      return
    }

    try {
      const { user } = await signIn({ email: request.email, password: request.password })
      navigateAfterSignIn({ navigate, redirect, setAuthenticatedUser, user })
    } catch {
      // The account exists; only the automatic sign-in failed, so registering again would be wrong.
      const loginState: SignInNavigationState = { registered: true }
      navigate(destinationToLogin, { replace: true, state: loginState })
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
