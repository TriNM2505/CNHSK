import { Clock3, Lock } from 'lucide-react'

import { cn } from '@/shared/lib/cn'

import { useClosed } from '../use-closed'

/** "2 giờ 05 phút" left until a moment; whole minutes, rounded up so it never shows 0 while still open. */
function remainingText(ms: number): string {
  const minutes = Math.ceil(ms / 60_000)
  const days = Math.floor(minutes / 1440)
  const hours = Math.floor((minutes % 1440) / 60)
  const rest = minutes % 60
  if (days > 0) return `${days} ngày ${hours} giờ`
  if (hours > 0) return `${hours} giờ ${String(rest).padStart(2, '0')} phút`
  return `${rest} phút`
}

/** Countdown for a comment contest, then "Đã đóng bình luận" once time is up. */
export function CommentsCountdown({ className, closeAt }: { className?: string; closeAt: string }) {
  const closed = useClosed(closeAt)
  const left = new Date(closeAt).getTime() - Date.now()
  const at = new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    month: '2-digit',
    timeZone: 'Asia/Ho_Chi_Minh',
  }).format(new Date(closeAt))
  return (
    <p
      className={cn(
        'inline-flex items-center gap-2 rounded-pill px-3 py-1 text-label',
        closed ? 'bg-canvas text-ink-secondary' : 'bg-warning-bg text-accent-700',
        className,
      )}
      role="status"
    >
      {closed ? <Lock aria-hidden="true" className="size-4" /> : <Clock3 aria-hidden="true" className="size-4" />}
      {closed ? `Đã đóng bình luận lúc ${at}` : `Còn ${remainingText(left)} · đóng lúc ${at}`}
    </p>
  )
}
