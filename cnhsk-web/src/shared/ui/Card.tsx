import { forwardRef, type HTMLAttributes } from 'react'

import { cn } from '@/shared/lib/cn'

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  variant?: 'default' | 'interactive' | 'selected'
}

const variantClasses: Record<NonNullable<CardProps['variant']>, string> = {
  default: '',
  interactive:
    'cursor-pointer transition-shadow hover:border-brand-600 hover:shadow-sm focus-within:border-brand-600 focus-within:shadow-sm',
  selected: 'border-brand-600 bg-brand-50',
}

export const Card = forwardRef<HTMLDivElement, CardProps>(function Card(
  { className, variant = 'default', ...props },
  ref,
) {
  return (
    <div
      {...props}
      ref={ref}
      className={cn(
        'rounded-md border border-line bg-surface p-4',
        variantClasses[variant],
        className,
      )}
    />
  )
})
