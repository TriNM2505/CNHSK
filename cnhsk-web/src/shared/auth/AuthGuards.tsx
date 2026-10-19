import { useEffect, useMemo } from 'react'
import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'

import type { RoleCode } from '@/features/auth/types'
import { ErrorState, Skeleton } from '@/shared/ui'

import { getAuthenticatedDestination } from './navigation'
import { useCurrentUser } from './useCurrentUser'

type GuardProps = {
  children: ReactNode
}

type RequireRoleProps = GuardProps & {
  roles: readonly RoleCode[]
}

function AuthLoading() {
  return (
    <main className="mx-auto min-h-screen max-w-content bg-canvas p-4 sm:p-6">
      <Skeleton className="mx-auto max-w-reading" variant="card" />
    </main>
  )
}

function AuthError() {
  const { error, reloadCurrentUser } = useCurrentUser()

  return (
    <main className="mx-auto min-h-screen max-w-content bg-canvas p-4 sm:p-6">
      <ErrorState
        className="mx-auto max-w-reading"
        message={error?.message ?? 'Không thể kiểm tra phiên đăng nhập. Vui lòng thử lại.'}
        onRetry={() => void reloadCurrentUser()}
        requestId={error?.requestId}
        title="Không thể kiểm tra phiên đăng nhập"
      />
    </main>
  )
}

function getLoginPath(pathname: string, search: string, hash: string): string {
  const redirect = `${pathname}${search}${hash}`
  return `/login?redirect=${encodeURIComponent(redirect)}`
}

export function RequireAuthenticated({ children }: GuardProps) {
  const location = useLocation()
  const { status } = useCurrentUser()

  if (status === 'loading') return <AuthLoading />
  if (status === 'error') return <AuthError />
  if (status === 'guest') {
    return <Navigate replace to={getLoginPath(location.pathname, location.search, location.hash)} />
  }

  return children
}

export function RequireRole({ children, roles }: RequireRoleProps) {
  const location = useLocation()
  const { status, user } = useCurrentUser()

  if (status === 'loading') return <AuthLoading />
  if (status === 'error') return <AuthError />
  if (status === 'guest') {
    return <Navigate replace to={getLoginPath(location.pathname, location.search, location.hash)} />
  }
  if (!user || !roles.some((role) => user.roles.includes(role))) {
    return <Navigate replace to="/403" />
  }

  return children
}

export function RequireGuest({ children }: GuardProps) {
  const location = useLocation()
  const { status, user } = useCurrentUser()
  const destination = useMemo(() => {
    if (status !== 'authenticated' || !user) return undefined
    const redirect = new URLSearchParams(location.search).get('redirect')
    return getAuthenticatedDestination(user, redirect)
  }, [location.search, status, user])

  useEffect(() => {
    if (destination?.type === 'external') window.location.assign(destination.href)
  }, [destination])

  if (status === 'loading') return <AuthLoading />
  if (status === 'error') return <AuthError />
  if (status === 'authenticated' && destination?.type === 'external') return <AuthLoading />
  if (status === 'authenticated' && destination?.type === 'internal') {
    return <Navigate replace to={destination.to} />
  }

  return children
}
