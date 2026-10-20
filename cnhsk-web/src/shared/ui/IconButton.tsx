import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react'

import { cn } from '@/shared/lib/cn'

export interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  children: ReactNode
  label: string
  variant?: 'default' | 'danger'
}

export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(function IconButton(
  { children, className, label, type = 'button', variant = 'default', ...props },
  ref,
) {
  return (
    <button
      {...props}
      ref={ref}
      aria-label={label}
      className={cn(
        'inline-flex size-touch items-center justify-center rounded-sm border border-line bg-surface text-ink-secondary transition-colors hover:border-brand-600 hover:text-brand-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50',
        variant === 'danger' && 'hover:border-danger hover:text-danger',
        className,
      )}
      type={type}
    >
      {children}
    </button>
  )
})
