import type { ReactNode } from 'react'

import { cn } from '@/shared/lib/cn'

export interface StatTileProps {
  className?: string
  label: ReactNode
  value: ReactNode
}

export function StatTile({ className, label, value }: StatTileProps) {
  return (
    <div className={cn('rounded-md border border-line bg-surface p-4', className)}>
      <p className="mb-1 text-body-sm font-semibold text-ink-secondary">{label}</p>
      <p className="text-h1 tabular-nums text-ink">{value}</p>
    </div>
  )
}
