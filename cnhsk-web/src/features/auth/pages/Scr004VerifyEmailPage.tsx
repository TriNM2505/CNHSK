import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useSearchParams } from 'react-router-dom'

import { getAuthenticatedDestination, useCurrentUser } from '@/shared/auth'

import { resendVerificationEmail, verifyEmail } from '../api/verifyEmail'
import {
  type ContinueTarget,
  type ResendView,
  VerifyEmailView,
  type VerifyEmailMode,
} from '../components/VerifyEmailView'
import {
  getResendFailure,
  getVerifyLinkFailure,
  type ResendState,
  secondsUntil,
  type VerifyLinkStatus,
} from '../verifyEmailFlow'

/** State do màn đăng ký (FE-15) gửi sang khi chuyển tới đây. */
interface VerifyEmailNavigationState {
  email?: unknown
  redirect?: unknown
}

function readNavigationState(state: unknown): { email?: string; redirect?: string } {
  const value = (state ?? {}) as VerifyEmailNavigationState
  return {
    email: typeof value.email === 'string' ? value.email : undefined,
    redirect: typeof value.redirect === 'string' ? value.redirect : undefined,
  }
}

function loginPath(redirect: string | null | undefined): string {
  return redirect ? `/login?redirect=${encodeURIComponent(redirect)}` : '/login'
}

export default function Scr004VerifyEmailPage() {
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()
  const { reloadCurrentUser, status, user } = useCurrentUser()
  const navigationState = readNavigationState(location.state)
  const redirect = navigationState.redirect ?? searchParams.get('redirect')

  // Token chỉ đọc lần đầu: sau khi gửi đi sẽ bị xoá khỏi thanh địa chỉ (không lưu trong lịch sử).
  const tokenRef = useRef(searchParams.get('token'))
  const startedRef = useRef(false)
  const [linkStatus, setLinkStatus] = useState<VerifyLinkStatus | null>(
    tokenRef.current ? 'verifying' : null,
  )
  const [linkRequestId, setLinkRequestId] = useState<string>()
  const [resend, setResend] = useState<ResendState>({ kind: 'idle' })
  const [now, setNow] = useState(() => Date.now())

  const runVerify = useCallback(async () => {
    const token = tokenRef.current
    if (!token) return

    setLinkStatus('verifying')
    setLinkRequestId(undefined)
    try {
      await verifyEmail(token)
      setLinkStatus('verified')
    } catch (error) {
      const failure = getVerifyLinkFailure(error)
      setLinkStatus(failure.status)
      setLinkRequestId(failure.requestId)
    }
  }, [])

  // Sau khi xác thực bằng link, làm mới user đang đăng nhập để banner "chưa xác thực" biến mất.
  // Chờ AuthProvider tải xong vì lúc mở link có thể chưa biết người dùng là ai.
  const reloadedAfterVerifyRef = useRef(false)
  useEffect(() => {
    if (linkStatus !== 'verified' || reloadedAfterVerifyRef.current) return
    if (status !== 'authenticated' || !user || user.emailVerified) return
    reloadedAfterVerifyRef.current = true
    void reloadCurrentUser()
  }, [linkStatus, reloadCurrentUser, status, user])

  // FR-088: tự xác thực đúng một lần khi mở link (kể cả StrictMode chạy effect hai lần).
  useEffect(() => {
    if (!tokenRef.current || startedRef.current) return
    startedRef.current = true
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current)
        next.delete('token')
        return next
      },
      { replace: true },
    )
    void runVerify()
  }, [runVerify, setSearchParams])

  // Đếm ngược khi bị giới hạn gửi lại (429).
  useEffect(() => {
    if (resend.kind !== 'cooldown') return undefined
    const intervalId = window.setInterval(() => {
      const current = Date.now()
      setNow(current)
      if (secondsUntil(resend.retryUntil, current) === 0) setResend({ kind: 'idle' })
    }, 1000)
    return () => window.clearInterval(intervalId)
  }, [resend])

  const handleResend = async () => {
    if (resend.kind === 'sending' || resend.kind === 'cooldown') return
    setResend({ kind: 'sending' })
    try {
      await resendVerificationEmail()
      setResend({ kind: 'sent' })
    } catch (error) {
      const startedAt = Date.now()
      const failure = getResendFailure(error, startedAt)
      setNow(startedAt)
      if (failure.kind === 'already-verified') {
        setResend({ kind: 'idle' })
        void reloadCurrentUser()
        return
      }
      setResend(failure)
    }
  }

  const mode: VerifyEmailMode =
    linkStatus ?? (status === 'loading' ? 'loading' : user?.emailVerified ? 'verified' : 'guide')

  const continueTarget: ContinueTarget = (() => {
    if (!user) return { to: '/' }
    const destination = getAuthenticatedDestination(user, redirect)
    return destination.type === 'internal' ? { to: destination.to } : { href: destination.href }
  })()

  const resendView: ResendView =
    resend.kind === 'cooldown'
      ? { kind: 'cooldown', secondsLeft: secondsUntil(resend.retryUntil, now) }
      : resend.kind === 'already-verified'
        ? { kind: 'idle' }
        : resend

  return (
    <VerifyEmailView
      authenticated={status === 'authenticated' && Boolean(user)}
      continueTarget={continueTarget}
      email={navigationState.email ?? user?.email}
      errorRequestId={linkRequestId}
      loginTo={loginPath(redirect)}
      loginToResend={loginPath('/verify-email')}
      mode={mode}
      onResend={() => void handleResend()}
      onRetryVerify={() => void runVerify()}
      registerTo="/register"
      resend={resendView}
    />
  )
}
