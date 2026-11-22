import { createContext } from 'react'

import type { CurrentUser } from '@/features/auth/types'
import type { ApiError } from '@/shared/api'

export type AuthStatus = 'loading' | 'guest' | 'authenticated' | 'error'

export type AuthContextValue = {
  error: ApiError | null
  reloadCurrentUser: () => Promise<void>
  setAuthenticatedUser: (user: CurrentUser) => void
  /** Rejects, and keeps the user signed in, when the server could not end the session. */
  signOut: () => Promise<void>
  status: AuthStatus
  user: CurrentUser | null
}

export const AuthContext = createContext<AuthContextValue | null>(null)
