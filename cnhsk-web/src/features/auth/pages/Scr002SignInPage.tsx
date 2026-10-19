import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'

import { useApiMutation } from '@/shared/api'
import { type AuthNavigationState, useCurrentUser } from '@/shared/auth'

import { signIn } from '../api/signIn'
import { SignInView } from '../components/SignInView'
import { navigateAfterSignIn } from '../navigateAfterSignIn'
import {
  getSignInFailure,
  normalizeLoginRequest,
  type SignInFieldErrors,
  type SignInNotice,
  validateLogin,
} from '../signInForm'
import type { LoginRequest } from '../types'

const EMPTY_VALUES: LoginRequest = { email: '', password: '' }

function noticeFromNavigationState(state: unknown): SignInNotice | undefined {
  const navigationState = state as AuthNavigationState | null
  const authNotice = navigationState?.authNotice
  if (authNotice?.reason !== 'ACCOUNT_BANNED') return undefined

  return { banReason: authNotice.banReason, kind: 'banned' }
}

function registerPath(redirect: string | null): string {
  if (!redirect) return '/register'
  return `/register?redirect=${encodeURIComponent(redirect)}`
}

export default function Scr002SignInPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const redirect = searchParams.get('redirect')
  const { setAuthenticatedUser } = useCurrentUser()
  const mutation = useApiMutation(signIn)
  const [values, setValues] = useState<LoginRequest>(EMPTY_VALUES)
  const [fieldErrors, setFieldErrors] = useState<SignInFieldErrors>({})
  const [notice, setNotice] = useState<SignInNotice | undefined>(() =>
    noticeFromNavigationState(location.state),
  )

  useEffect(() => {
    if (notice?.kind !== 'blocked') return undefined
    const delay = Date.parse(notice.blockedUntil) - Date.now()
    if (!Number.isFinite(delay) || delay <= 0) return undefined

    const timeoutId = window.setTimeout(() => setNotice(undefined), delay + 50)
    return () => window.clearTimeout(timeoutId)
  }, [notice])

  const sourceBlocked = notice?.kind === 'blocked' && Date.parse(notice.blockedUntil) > Date.now()
  const state = mutation.pending
    ? ('submitting' as const)
    : notice || Object.keys(fieldErrors).length
      ? ('error' as const)
      : ('ready' as const)
  const destinationToRegister = useMemo(() => registerPath(redirect), [redirect])

  const updateField = (field: keyof LoginRequest, value: string) => {
    setValues((current) => ({ ...current, [field]: value }))
    setFieldErrors((current) => ({ ...current, [field]: undefined }))
    if (notice?.kind === 'validation' || notice?.kind === 'invalid-credentials') {
      setNotice(undefined)
    }
  }

  const submit = async () => {
    if (mutation.pending || sourceBlocked) return

    const validationErrors = validateLogin(values)
    if (Object.keys(validationErrors).length) {
      setFieldErrors(validationErrors)
      setNotice({ kind: 'validation' })
      return
    }

    setFieldErrors({})
    setNotice(undefined)

    try {
      const response = await mutation.run(normalizeLoginRequest(values))
      navigateAfterSignIn({
        navigate,
        redirect,
        setAuthenticatedUser,
        user: response.user,
      })
    } catch (error) {
      const failure = getSignInFailure(error)
      setFieldErrors(failure.fieldErrors)
      setNotice(failure.notice)
    }
  }

  return (
    <SignInView
      fieldErrors={fieldErrors}
      notice={notice}
      onEmailChange={(value) => updateField('email', value)}
      onPasswordChange={(value) => updateField('password', value)}
      onSubmit={() => void submit()}
      registerTo={destinationToRegister}
      state={state}
      submitDisabled={sourceBlocked}
      values={values}
    />
  )
}
