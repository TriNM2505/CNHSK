import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import { useApiMutation } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'

import { register, sendRegisterCode } from '../api/register'
import { signIn } from '../api/signIn'
import { RegisterCodeView } from '../components/RegisterCodeView'
import { RegisterView } from '../components/RegisterView'
import { navigateAfterSignIn } from '../navigateAfterSignIn'
import {
  getRegisterCodeError,
  getRegisterCodeWait,
  normalizeRegisterCode,
  registerCodeWaitMessage,
  type RegisterCodeNotice,
  validateRegisterCode,
} from '../registerCode'
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
const RESEND_DELAY_SECONDS = 60

function loginPath(redirect: string | null): string {
  if (!redirect) return '/login'
  return `/login?redirect=${encodeURIComponent(redirect)}`
}

function useCountdown(): [number, (seconds: number) => void] {
  const [deadline, setDeadline] = useState(0)
  const [now, setNow] = useState(() => Date.now())
  const seconds = Math.max(0, Math.ceil((deadline - now) / 1000))

  useEffect(() => {
    if (seconds <= 0) return
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [seconds])

  return [
    seconds,
    (value) => {
      const current = Date.now()
      setNow(current)
      setDeadline(current + value * 1000)
    },
  ]
}

export default function Scr003RegisterPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const redirect = searchParams.get('redirect')
  const { setAuthenticatedUser } = useCurrentUser()
  const codeMutation = useApiMutation(sendRegisterCode)
  const mutation = useApiMutation(register)
  const [submitting, setSubmitting] = useState(false)
  const [values, setValues] = useState<RegisterRequest>(EMPTY_VALUES)
  const [fieldErrors, setFieldErrors] = useState<RegisterFieldErrors>({})
  const [notice, setNotice] = useState<RegisterNotice>()
  const [step, setStep] = useState<'form' | 'code'>('form')
  const [codeEmail, setCodeEmail] = useState('')
  const [code, setCode] = useState('')
  const [codeError, setCodeError] = useState<string>()
  const [codeNotice, setCodeNotice] = useState<RegisterCodeNotice>()
  const [resendIn, startResendCountdown] = useCountdown()
  const destinationToLogin = useMemo(() => loginPath(redirect), [redirect])
  const request = normalizeRegisterRequest(values)
  const sending = codeMutation.pending
  const state = sending
    ? ('submitting' as const)
    : notice || Object.values(fieldErrors).some(Boolean)
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

  const showFormFailure = (error: unknown) => {
    const failure = getRegisterFailure(error)
    setFieldErrors(failure.fieldErrors)
    setNotice(failure.notice)
    setStep('form')
  }

  const openCodeStep = (waitSeconds: number) => {
    setCodeEmail(request.email)
    setCode('')
    setCodeError(undefined)
    setCodeNotice(undefined)
    startResendCountdown(waitSeconds)
    setStep('code')
  }

  const submitForm = async () => {
    if (sending) return

    const validationErrors = validateRegister(values)
    if (Object.keys(validationErrors).length) {
      setFieldErrors(validationErrors)
      setNotice({ kind: 'validation' })
      return
    }

    setFieldErrors({})
    setNotice(undefined)
    try {
      const sent = await codeMutation.run(request)
      openCodeStep(sent.resendAfterSeconds)
    } catch (error) {
      const wait = getRegisterCodeWait(error)
      if (wait === undefined) {
        showFormFailure(error)
      } else if (wait <= RESEND_DELAY_SECONDS) {
        // Mã vừa gửi chưa quá một phút nên còn dùng được.
        openCodeStep(wait)
      } else {
        setNotice({ kind: 'generic', message: registerCodeWaitMessage(wait) })
      }
    }
  }

  const resend = async () => {
    if (sending || submitting || resendIn > 0) return
    setCodeError(undefined)
    setCodeNotice(undefined)
    try {
      const sent = await codeMutation.run(request)
      setCode('')
      setCodeNotice({ kind: 'resent' })
      startResendCountdown(sent.resendAfterSeconds)
    } catch (error) {
      const wait = getRegisterCodeWait(error)
      if (wait !== undefined) {
        startResendCountdown(wait)
        if (wait > RESEND_DELAY_SECONDS) {
          setCodeNotice({ kind: 'error', message: registerCodeWaitMessage(wait) })
        }
        return
      }
      const failure = getRegisterFailure(error)
      if (failure.notice.kind === 'generic') setCodeNotice({ ...failure.notice, kind: 'error' })
      else showFormFailure(error)
    }
  }

  const submitCode = async () => {
    if (submitting || sending) return
    const invalid = validateRegisterCode(code)
    setCodeError(invalid)
    setCodeNotice(undefined)
    if (invalid) return

    setSubmitting(true)
    try {
      await mutation.run({ ...request, code })
    } catch (error) {
      setSubmitting(false)
      const rejected = getRegisterCodeError(error)
      if (rejected) {
        setCodeError(rejected)
        return
      }
      const failure = getRegisterFailure(error)
      if (failure.notice.kind === 'generic') setCodeNotice({ ...failure.notice, kind: 'error' })
      else showFormFailure(error)
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

  if (step === 'code') {
    return (
      <RegisterCodeView
        code={code}
        email={codeEmail}
        error={codeError}
        notice={codeNotice}
        onBack={() => setStep('form')}
        onCodeChange={(value) => {
          setCode(normalizeRegisterCode(value))
          setCodeError(undefined)
        }}
        onResend={() => void resend()}
        onSubmit={() => void submitCode()}
        resendInSeconds={resendIn}
        sending={sending}
        submitting={submitting}
      />
    )
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
      onSubmit={() => void submitForm()}
      state={state}
      values={values}
    />
  )
}
