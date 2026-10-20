import { Inbox } from 'lucide-react'
import type { ReactNode } from 'react'

import { cn } from '@/shared/lib/cn'

export interface EmptyStateProps {
  action?: ReactNode
  className?: string
  description: ReactNode
  icon?: ReactNode
  title: ReactNode
}

export function EmptyState({ action, className, description, icon, title }: EmptyStateProps) {
  return (
    <section
      className={cn(
        'rounded-md border border-dashed border-line bg-surface px-4 py-12 text-center',
        className,
      )}
    >
      <div className="mx-auto mb-3 flex size-touch items-center justify-center rounded-pill bg-brand-50 text-brand-700">
        {icon ?? <Inbox aria-hidden="true" className="size-5" />}
      </div>
      <h2 className="text-h3 text-ink">{title}</h2>
      <div className="mx-auto mt-2 max-w-reading text-body-sm text-ink-secondary">
        {description}
      </div>
      {action ? <div className="mt-4 flex justify-center">{action}</div> : null}
    </section>
  )
}
