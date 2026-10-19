import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { useLocation, useMatches, useNavigate } from 'react-router-dom'

import { getCurrentUser } from '@/features/auth/api/getCurrentUser'
import { signOutCurrentUser } from '@/features/auth/api/signOutCurrentUser'
import type { CurrentUser } from '@/features/auth/types'
import { ApiError, AUTH_EXPIRED_EVENT, type AuthExpiredDetail, toApiError } from '@/shared/api'

import { AuthContext, type AuthContextValue, type AuthStatus } from './AuthContext'

type AuthProviderProps = {
  children: ReactNode
}

type AuthRouteHandle = {
  requiresAuthentication?: boolean
}

export type AuthNavigationState = {
  authNotice?: {
    banReason?: string
    reason: 'ACCOUNT_BANNED'
  }
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}

function normalizeBanReason(reason: unknown): string | undefined {
  return typeof reason === 'string' && reason.trim() ? reason.trim() : undefined
}

export function AuthProvider({ children }: AuthProviderProps) {
  const location = useLocation()
  const matches = useMatches()
  const navigate = useNavigate()
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [status, setStatus] = useState<AuthStatus>('loading')
  const [error, setError] = useState<ApiError | null>(null)
  const requestVersionRef = useRef(0)
  const requestControllerRef = useRef<AbortController | null>(null)
  const accountBannedRef = useRef(false)

  const requiresAuthentication = matches.some(
    (match) => (match.handle as AuthRouteHandle | undefined)?.requiresAuthentication === true,
  )

  const cancelCurrentRequest = useCallback(() => {
    requestVersionRef.current += 1
    requestControllerRef.current?.abort()
    requestControllerRef.current = null
  }, [])

  const setAuthenticatedUser = useCallback(
    (nextUser: CurrentUser) => {
      cancelCurrentRequest()
      accountBannedRef.current = false
      setUser(nextUser)
      setStatus('authenticated')
      setError(null)
    },
    [cancelCurrentRequest],
  )

  const reloadCurrentUser = useCallback(async () => {
    cancelCurrentRequest()
    const requestVersion = requestVersionRef.current
    const controller = new AbortController()
    requestControllerRef.current = controller
    setUser(null)
    setStatus('loading')
    setError(null)

    try {
      const nextUser = await getCurrentUser(controller.signal)
      if (requestVersionRef.current !== requestVersion) return
      accountBannedRef.current = false
      setUser(nextUser)
      setStatus('authenticated')
    } catch (caughtError) {
      if (requestVersionRef.current !== requestVersion || isAbortError(caughtError)) return

      const apiError = toApiError(caughtError)
      setUser(null)
      if (apiError.status === 401 && apiError.errorCode === 'UNAUTHENTICATED') {
        setStatus('guest')
        setError(null)
      } else {
        setStatus('error')
        setError(apiError)
      }
    } finally {
      if (requestVersionRef.current === requestVersion) requestControllerRef.current = null
    }
  }, [cancelCurrentRequest])

  const signOut = useCallback(async () => {
    try {
      await signOutCurrentUser()
    } catch {
      // Trạng thái phía client vẫn phải được xoá khi máy chủ không thể phản hồi.
    } finally {
      cancelCurrentRequest()
      setUser(null)
      setStatus('guest')
      setError(null)
      if (!accountBannedRef.current) navigate('/', { replace: true })
    }
  }, [cancelCurrentRequest, navigate])

  useEffect(() => {
    void reloadCurrentUser()
    return cancelCurrentRequest
  }, [cancelCurrentRequest, reloadCurrentUser])

  useEffect(() => {
    const handleAuthExpired = (event: Event) => {
      const detail = (event as CustomEvent<AuthExpiredDetail | undefined>).detail
      cancelCurrentRequest()
      setUser(null)
      setStatus('guest')
      setError(null)

      if (detail?.reason === 'ACCOUNT_BANNED') {
        accountBannedRef.current = true
        const state: AuthNavigationState = {
          authNotice: {
            reason: 'ACCOUNT_BANNED',
            banReason: normalizeBanReason(detail.banReason),
          },
        }
        navigate('/login', { replace: true, state })
        return
      }

      if (requiresAuthentication) {
        const redirect = `${location.pathname}${location.search}${location.hash}`
        navigate(`/login?redirect=${encodeURIComponent(redirect)}`, { replace: true })
      }
    }

    window.addEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired)
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired)
  }, [cancelCurrentRequest, location, navigate, requiresAuthentication])

  const value = useMemo<AuthContextValue>(
    () => ({
      error,
      reloadCurrentUser,
      setAuthenticatedUser,
      signOut,
      status,
      user,
    }),
    [error, reloadCurrentUser, setAuthenticatedUser, signOut, status, user],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
