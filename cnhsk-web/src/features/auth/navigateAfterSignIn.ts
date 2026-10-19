import type { NavigateFunction } from 'react-router-dom'

import { getAuthenticatedDestination } from '@/shared/auth/navigation'

import type { CurrentUser } from './types'

interface NavigateAfterSignInOptions {
  assignExternal?: (href: string) => void
  navigate: NavigateFunction
  redirect?: string | null
  setAuthenticatedUser: (user: CurrentUser) => void
  user: CurrentUser
}

export function navigateAfterSignIn({
  assignExternal = (href) => window.location.assign(href),
  navigate,
  redirect,
  setAuthenticatedUser,
  user,
}: NavigateAfterSignInOptions): void {
  setAuthenticatedUser(user)
  const destination = getAuthenticatedDestination(user, redirect)

  if (destination.type === 'internal') {
    navigate(destination.to, { replace: true })
    return
  }

  assignExternal(destination.href)
}
