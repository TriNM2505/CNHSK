import { CircleAlert, CircleCheck, Info } from 'lucide-react'
import type { ReactNode } from 'react'

import { cn } from '@/shared/lib/cn'

interface AuthNoticeProps {
  children?: ReactNode
  requestId?: string
  title?: string
  tone?: 'danger' | 'info' | 'success'
}

const TONES = {
  danger: { className: 'bg-danger-bg text-danger', icon: CircleAlert },
  info: { className: 'bg-info-bg text-info', icon: Info },
  success: { className: 'bg-success-bg text-success', icon: CircleCheck },
}

export function AuthNotice({ children, requestId, title, tone = 'danger' }: AuthNoticeProps) {
  const { className, icon: Icon } = TONES[tone]
  return (
    <div
      className={cn('mb-4 flex items-start gap-2 rounded-sm px-3 py-2.5 text-body-sm', className)}
      role={tone === 'success' ? 'status' : 'alert'}
    >
      <Icon aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
      <div className="min-w-0 break-words">
        {title ? <p className="font-semibold">{title}</p> : null}
        {children ? <p>{children}</p> : null}
        {requestId ? <p className="text-caption">Mã lỗi: {requestId}</p> : null}
      </div>
    </div>
  )
}
