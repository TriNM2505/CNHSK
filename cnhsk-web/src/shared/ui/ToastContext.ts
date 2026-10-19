import { createContext, type ReactNode } from 'react'

export type ToastTone = 'success' | 'error' | 'info'

export interface ShowToastOptions {
  message: ReactNode
  tone: ToastTone
}

export interface ToastContextValue {
  dismissToast: (id: number) => void
  showToast: (options: ShowToastOptions) => number
}

export const ToastContext = createContext<ToastContextValue | null>(null)
