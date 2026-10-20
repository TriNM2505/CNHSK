import { CircleAlert, CircleCheck } from 'lucide-react'

import { cn } from '@/shared/lib/cn'

export type FormNoticeValue =
  | { kind: 'success'; title: string; message: string }
  | { kind: 'error'; title: string; message: string; requestId?: string }

/** Thông báo kết quả gửi form, hiển thị ngay trên form (không dùng toast). */
export function FormNotice({ notice }: { notice: FormNoticeValue }) {
  const success = notice.kind === 'success'
  const Icon = success ? CircleCheck : CircleAlert
  return (
    <div
      className={cn(
        'mb-5 flex items-start gap-3 rounded-md border p-4 text-body-sm',
        success
          ? 'border-success bg-success-bg text-success'
          : 'border-danger bg-danger-bg text-danger',
      )}
      role={success ? 'status' : 'alert'}
    >
      <Icon aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
      <div className="min-w-0">
        <p className="font-semibold">{notice.title}</p>
        <p className="mt-1">{notice.message}</p>
        {notice.kind === 'error' && notice.requestId ? (
          <p className="mt-1 text-caption">Mã lỗi: {notice.requestId}</p>
        ) : null}
      </div>
    </div>
  )
}
