import { type ReactNode } from 'react'

import { cn } from '@/shared/lib/cn'

export interface PageHeaderProps {
  actions?: ReactNode
  className?: string
  subtitle?: ReactNode
  title: ReactNode
}

export function PageHeader({ actions, className, subtitle, title }: PageHeaderProps) {
  return (
    <header className={cn('mb-6 flex flex-wrap items-start justify-between gap-4', className)}>
      <div className="min-w-0">
        <h1 className="text-h1-sm text-ink sm:text-h1">{title}</h1>
        {subtitle ? <p className="mt-2 text-body text-ink-secondary">{subtitle}</p> : null}
      </div>
      {actions ? <div className="flex flex-wrap gap-2">{actions}</div> : null}
    </header>
  )
}
