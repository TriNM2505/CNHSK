import type { HTMLAttributes } from 'react'

import { cn } from '@/shared/lib/cn'

export interface ProgressProps extends Omit<HTMLAttributes<HTMLDivElement>, 'role'> {
  label?: string
  max?: number
  tone?: 'brand' | 'success' | 'accent'
  value: number
}

const toneClasses: Record<NonNullable<ProgressProps['tone']>, string> = {
  brand: 'bg-brand-600',
  success: 'bg-success',
  accent: 'bg-accent-500',
}

export function Progress({
  className,
  label,
  max = 100,
  tone = 'brand',
  value,
  ...props
}: ProgressProps) {
  const safeMax = max > 0 ? max : 100
  const safeValue = Math.min(Math.max(value, 0), safeMax)
  const percentage = Math.round((safeValue / safeMax) * 100)
  const valueText = label ?? `${percentage}%`

  return (
    <div
      {...props}
      aria-valuemax={safeMax}
      aria-valuemin={0}
      aria-valuenow={safeValue}
      aria-valuetext={valueText}
      className={cn('flex items-center gap-2', className)}
      role="progressbar"
    >
      <span aria-hidden="true" className="h-2 min-w-16 flex-1 overflow-hidden rounded-pill bg-line">
        <span
          className={cn('block h-full rounded-pill', toneClasses[tone])}
          style={{ width: `${percentage}%` }}
        />
      </span>
      <span className="whitespace-nowrap text-label tabular-nums text-ink-secondary">
        {valueText}
      </span>
    </div>
  )
}
