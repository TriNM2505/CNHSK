import { CircleCheck, CircleX, Info, X, type LucideIcon } from 'lucide-react'
import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'

import { cn } from '@/shared/lib/cn'
import { IconButton } from '@/shared/ui/IconButton'
import { ToastContext, type ShowToastOptions, type ToastTone } from '@/shared/ui/ToastContext'

export type { ToastTone } from '@/shared/ui/ToastContext'

export interface ToastProps {
  duration?: number
  message: ReactNode
  onClose: () => void
  tone: ToastTone
}

const toneClasses: Record<ToastTone, string> = {
  success: 'border-success bg-success-bg text-success',
  error: 'border-danger bg-danger-bg text-danger',
  info: 'border-info bg-info-bg text-info',
}

const toneIcons: Record<ToastTone, LucideIcon> = {
  success: CircleCheck,
  error: CircleX,
  info: Info,
}

export function Toast({ duration = 4000, message, onClose, tone }: ToastProps) {
  const Icon = toneIcons[tone]

  useEffect(() => {
    const timeoutId = window.setTimeout(onClose, duration)

    return () => window.clearTimeout(timeoutId)
  }, [duration, onClose])

  return (
    <div
      aria-live={tone === 'error' ? 'assertive' : 'polite'}
      className={cn('flex items-start gap-2 rounded-sm border p-3 shadow-md', toneClasses[tone])}
      role={tone === 'error' ? 'alert' : 'status'}
    >
      <Icon aria-hidden="true" className="mt-1 size-5 shrink-0" />
      <div className="min-w-0 flex-1 text-body-sm font-semibold">{message}</div>
      <IconButton className="shrink-0 bg-transparent" label="Đóng thông báo" onClick={onClose}>
        <X aria-hidden="true" className="size-4" />
      </IconButton>
    </div>
  )
}

interface ToastItem {
  id: number
  message: ReactNode
  tone: ToastTone
}

export interface ToastProviderProps {
  children: ReactNode
}

export function ToastProvider({ children }: ToastProviderProps) {
  const [toasts, setToasts] = useState<ToastItem[]>([])
  const nextId = useRef(1)

  const dismissToast = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id))
  }, [])

  const showToast = useCallback(({ message, tone }: ShowToastOptions) => {
    const id = nextId.current
    nextId.current += 1
    setToasts((current) => [...current, { id, message, tone }])

    return id
  }, [])

  return (
    <ToastContext.Provider value={{ dismissToast, showToast }}>
      {children}
      <div className="fixed inset-x-4 top-4 z-toast flex flex-col gap-3 sm:left-auto sm:w-96">
        {toasts.map((toast) => (
          <Toast
            key={toast.id}
            message={toast.message}
            onClose={() => dismissToast(toast.id)}
            tone={toast.tone}
          />
        ))}
      </div>
    </ToastContext.Provider>
  )
}
