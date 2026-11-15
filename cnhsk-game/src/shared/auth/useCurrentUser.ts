import { useEffect, useState } from 'react'

import { ApiError, apiRequest, GAME_SESSION_EXPIRED_EVENT } from '../api/client'

type CurrentUser = { id: number; displayName: string; roles: string[] }

type UserState =
  { status: 'loading' | 'guest' | 'error' } | { status: 'authenticated'; user: CurrentUser }

export function useCurrentUser(): UserState {
  const [state, setState] = useState<UserState>({ status: 'loading' })
  useEffect(() => {
    const controller = new AbortController()
    function markGuest() {
      controller.abort()
      setState({ status: 'guest' })
    }
    window.addEventListener(GAME_SESSION_EXPIRED_EVENT, markGuest)
    void apiRequest<CurrentUser>('/api/auth/me', { signal: controller.signal })
      .then((user) => {
        if (!controller.signal.aborted) setState({ status: 'authenticated', user })
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setState({
            status: error instanceof ApiError && error.status === 401 ? 'guest' : 'error',
          })
        }
      })
    return () => {
      window.removeEventListener(GAME_SESSION_EXPIRED_EVENT, markGuest)
      controller.abort()
    }
  }, [])
  return state
}

export function gameLoginHref(returnTo: string): string {
  const gameOrigin = window.location.origin
  const login = new URL('/login', import.meta.env.VITE_WEB_ORIGIN || 'http://localhost:5173')
  try {
    const redirect = new URL(returnTo, gameOrigin)
    if (redirect.origin !== gameOrigin || redirect.username || redirect.password) {
      throw new Error('Invalid game redirect')
    }
    login.searchParams.set('redirect', redirect.href)
  } catch {
    login.searchParams.set('redirect', new URL('/', gameOrigin).href)
  }
  return login.href
}
