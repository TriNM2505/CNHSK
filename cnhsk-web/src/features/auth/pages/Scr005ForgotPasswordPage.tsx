import { useState } from 'react'

import { useApiMutation } from '@/shared/api'

import { requestPasswordReset } from '../api/passwordReset'
import { ForgotPasswordView, type ForgotPasswordState } from '../components/ForgotPasswordView'
import { normalizeEmail, validateEmail } from '../emailValidation'
import { forgotPasswordErrorMessage } from '../passwordResetForm'

export default function Scr005ForgotPasswordPage() {
  const mutation = useApiMutation(requestPasswordReset)
  const [email, setEmail] = useState('')
  const [emailError, setEmailError] = useState<string>()
  const [sentTo, setSentTo] = useState<string>()
  const [error, setError] = useState<{ message: string; requestId?: string }>()

  const state: ForgotPasswordState = mutation.pending
    ? 'submitting'
    : sentTo
      ? 'sent'
      : error
        ? 'error'
        : 'ready'

  const submit = async () => {
    if (mutation.pending) return
    const validationError = validateEmail(email)
    if (validationError) {
      setEmailError(validationError)
      return
    }

    setEmailError(undefined)
    setError(undefined)
    const normalized = normalizeEmail(email)
    try {
      await mutation.run(normalized)
      // FR-038: cùng một thông báo cho mọi email — không phân biệt có tài khoản hay không.
      setSentTo(normalized)
    } catch (caught) {
      setError(forgotPasswordErrorMessage(caught))
    }
  }

  return (
    <ForgotPasswordView
      email={email}
      emailError={emailError}
      error={error}
      loginTo="/login"
      onEmailChange={(value) => {
        setEmail(value)
        setEmailError(undefined)
        setError(undefined)
      }}
      onSendAgain={() => setSentTo(undefined)}
      onSubmit={() => void submit()}
      sentTo={sentTo}
      state={state}
    />
  )
}
