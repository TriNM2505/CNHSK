import { useContext } from 'react'

import { AuthContext } from './AuthContext'

export function useCurrentUser() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useCurrentUser phải được dùng bên trong AuthProvider.')
  return context
}
