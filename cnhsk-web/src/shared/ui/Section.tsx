import { useId, type ReactNode } from 'react'

import { cn } from '@/shared/lib/cn'

export interface SectionProps {
  actions?: ReactNode
  children: ReactNode
  className?: string
  subtitle?: ReactNode
  title: ReactNode
}

export function Section({ actions, children, className, subtitle, title }: SectionProps) {
  const titleId = useId()

  return (
    <section aria-labelledby={titleId} className={cn('mb-8', className)}>
      <div className="mb-3 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-h3 text-ink" id={titleId}>
            {title}
          </h2>
          {subtitle ? <p className="mt-1 text-body-sm text-ink-secondary">{subtitle}</p> : null}
        </div>
        {actions ? <div className="flex flex-wrap gap-2">{actions}</div> : null}
      </div>
      {children}
    </section>
  )
}
