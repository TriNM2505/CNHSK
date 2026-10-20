import { useEffect, useId, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

import { Button } from '@/shared/ui/Button'

export interface ConfirmPanelProps {
  cancelLabel?: string
  confirmLabel: string
  danger?: boolean
  message: ReactNode
  onCancel: () => void
  onConfirm: () => void
  open: boolean
  pending?: boolean
  title: ReactNode
}

const focusableSelector = [
  'button:not([disabled])',
  'a[href]',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

export function ConfirmPanel({
  cancelLabel = 'Huỷ',
  confirmLabel,
  danger = false,
  message,
  onCancel,
  onConfirm,
  open,
  pending = false,
  title,
}: ConfirmPanelProps) {
  const titleId = useId()
  const panelRef = useRef<HTMLDivElement>(null)
  const cancelHandlerRef = useRef(onCancel)
  const pendingRef = useRef(pending)

  cancelHandlerRef.current = onCancel
  pendingRef.current = pending

  useEffect(() => {
    if (!open) {
      return
    }

    const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    panelRef.current?.querySelector<HTMLButtonElement>('button:not([disabled])')?.focus()

    const handleKeyDown = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        if (!pendingRef.current) cancelHandlerRef.current()
        return
      }

      if (event.key !== 'Tab') {
        return
      }

      const focusableElements = Array.from(
        panelRef.current?.querySelectorAll<HTMLElement>(focusableSelector) ?? [],
      )
      const firstElement = focusableElements[0]
      const lastElement = focusableElements[focusableElements.length - 1]

      if (!firstElement || !lastElement) {
        event.preventDefault()
        return
      }

      if (event.shiftKey && document.activeElement === firstElement) {
        event.preventDefault()
        lastElement.focus()
      } else if (!event.shiftKey && document.activeElement === lastElement) {
        event.preventDefault()
        firstElement.focus()
      } else if (!panelRef.current?.contains(document.activeElement)) {
        event.preventDefault()
        firstElement.focus()
      }
    }

    document.addEventListener('keydown', handleKeyDown)

    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.body.style.overflow = previousOverflow
      trigger?.focus()
    }
  }, [open])

  if (!open) {
    return null
  }

  return createPortal(
    <div className="fixed inset-0 z-overlay flex items-end justify-center bg-ink/50 p-0 sm:items-center sm:p-4">
      <div
        aria-labelledby={titleId}
        aria-modal="true"
        className="w-full rounded-t-lg border border-line bg-surface p-6 shadow-overlay sm:max-w-lg sm:rounded-lg"
        ref={panelRef}
        role="dialog"
      >
        <h2 className="text-h3 text-ink" id={titleId}>
          {title}
        </h2>
        <div className="mt-2 text-body text-ink-secondary">{message}</div>
        <div className="mt-6 flex flex-wrap justify-end gap-2">
          <Button disabled={pending} onClick={onCancel} variant="outline">
            {cancelLabel}
          </Button>
          <Button loading={pending} onClick={onConfirm} variant={danger ? 'danger' : 'primary'}>
            {pending ? 'Đang xử lý' : confirmLabel}
          </Button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
