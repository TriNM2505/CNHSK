import { CircleAlert } from 'lucide-react'
import type { ReactNode } from 'react'

import { cn } from '@/shared/lib/cn'
import { Button } from '@/shared/ui/Button'

export interface ErrorStateProps {
  className?: string
  message: ReactNode
  onRetry?: () => void
  requestId?: string
  title?: ReactNode
}

export function ErrorState({
  className,
  message,
  onRetry,
  requestId,
  title = 'Không thể tải dữ liệu',
}: ErrorStateProps) {
  return (
    <section
      className={cn('rounded-md border border-danger bg-danger-bg p-4 text-danger', className)}
    >
      <div className="flex items-start gap-3">
        <CircleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
        <div className="min-w-0">
          <h2 className="text-label">{title}</h2>
          <div className="mt-1 text-body-sm">{message}</div>
          {requestId ? <p className="mt-2 text-caption">Mã lỗi: {requestId}</p> : null}
          {onRetry ? (
            <Button className="mt-3" onClick={onRetry} size="sm" variant="outline">
              Thử lại
            </Button>
          ) : null}
        </div>
      </div>
    </section>
  )
}
