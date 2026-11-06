import { useEffect, useId, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

import type { ApiError } from '@/shared/api'
import { Button, ErrorState } from '@/shared/ui'

import type { LexemeDetail } from '../types'

interface TranslationLexemePopupProps {
  state: 'loading' | 'ready' | 'error'
  entry?: LexemeDetail
  error?: ApiError
  onClose: () => void
  onRetry: () => void
  saveActions?: ReactNode
}

const focusable =
  'button:not([disabled]),a[href],input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])'

export function TranslationLexemePopup({
  state,
  entry,
  error,
  onClose,
  onRetry,
  saveActions,
}: TranslationLexemePopupProps) {
  const titleId = useId()
  const panel = useRef<HTMLDivElement>(null)
  const close = useRef(onClose)
  close.current = onClose
  useEffect(() => {
    const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    panel.current?.querySelector<HTMLButtonElement>('button')?.focus()
    const keydown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        close.current()
        return
      }
      if (event.key !== 'Tab') return
      const elements = Array.from(panel.current?.querySelectorAll<HTMLElement>(focusable) ?? [])
      const first = elements[0]
      const last = elements[elements.length - 1]
      if (!first || !last) {
        event.preventDefault()
        return
      }
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (
        (!event.shiftKey && document.activeElement === last) ||
        !panel.current?.contains(document.activeElement)
      ) {
        event.preventDefault()
        first.focus()
      }
    }
    document.addEventListener('keydown', keydown)
    return () => {
      document.removeEventListener('keydown', keydown)
      document.body.style.overflow = overflow
      if (trigger?.isConnected) trigger.focus()
    }
  }, [])
  return createPortal(
    <div className="fixed inset-0 z-overlay flex items-end justify-center bg-ink/50 p-0 sm:items-center sm:p-4">
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="max-h-[90vh] w-full space-y-4 overflow-y-auto rounded-t-lg border border-line bg-surface p-6 shadow-overlay sm:max-w-lg sm:rounded-lg"
      >
        <div className="flex items-center justify-between gap-3">
          <h2 id={titleId} className="text-h3 text-ink">
            Tra từ
          </h2>
          <Button onClick={onClose} variant="ghost">
            Đóng tra từ
          </Button>
        </div>
        {state === 'loading' ? <p role="status">Đang tải mục từ…</p> : null}
        {state === 'error' ? (
          <ErrorState
            title={error?.status === 404 ? 'Không tìm thấy mục từ' : 'Không thể tải mục từ'}
            message={error?.message ?? 'Vui lòng thử lại.'}
            requestId={error?.requestId}
            onRetry={onRetry}
          />
        ) : null}
        {state === 'ready' && entry ? (
          <>
            <h3 className="font-hanzi text-h2 text-ink" lang="zh-CN">
              {entry.text}
            </h3>
            {entry.pinyin ? <p className="text-body text-brand-700">{entry.pinyin}</p> : null}
            <p className="whitespace-pre-wrap text-body text-ink">{entry.meaningVi}</p>
            {saveActions}
          </>
        ) : null}
      </div>
    </div>,
    document.body,
  )
}
