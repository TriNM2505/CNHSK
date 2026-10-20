import type { ReactNode } from 'react'

export type PageStateValue = 'loading' | 'empty' | 'error' | 'ready'

export interface PageStateProps {
  children: ReactNode
  empty: ReactNode
  error: ReactNode
  loading: ReactNode
  state: PageStateValue
}

export function PageState({ children, empty, error, loading, state }: PageStateProps) {
  if (state === 'loading') {
    return loading
  }

  if (state === 'empty') {
    return empty
  }

  if (state === 'error') {
    return error
  }

  return children
}
