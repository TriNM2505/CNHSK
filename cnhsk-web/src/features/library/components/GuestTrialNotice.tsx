import { Info, LogIn } from 'lucide-react'

import { cn } from '@/shared/lib/cn'
import { Button } from '@/shared/ui'

import type { GuestTrial } from '../types'

type GuestTrialNoticeProps = {
  exhausted: boolean
  loginTo: string
  registerTo: string
  trial?: GuestTrial
}

export function GuestTrialNotice({ exhausted, loginTo, registerTo, trial }: GuestTrialNoticeProps) {
  if (exhausted) {
    return (
      <section
        aria-labelledby="guest-trial-title"
        className="flex flex-col gap-4 rounded-md border border-brand-600 bg-brand-50 p-5 sm:flex-row sm:items-center sm:justify-between sm:p-6"
      >
        <div className="min-w-0 space-y-1" role="status">
          <h2 className="text-h3 text-brand-800" id="guest-trial-title">
            Bạn đã dùng hết {trial ? `${trial.limit} ` : ''}lượt tra thử hôm nay
          </h2>
          <p className="text-body text-ink-secondary">
            Đăng nhập để tra tiếp ngay. Lượt tra thử được làm mới vào 0 giờ mỗi ngày.
          </p>
        </div>
        <div className="flex shrink-0 flex-col gap-3 sm:flex-row">
          <Button to={loginTo}>
            <LogIn aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
            Đăng nhập để tra tiếp
          </Button>
          <Button to={registerTo} variant="outline">
            Đăng ký miễn phí
          </Button>
        </div>
      </section>
    )
  }

  const low = trial !== undefined && trial.remaining <= 3
  return (
    <div
      className={cn(
        'flex flex-wrap items-center gap-x-4 gap-y-2 rounded-md border p-4 text-body-sm',
        low
          ? 'border-warning bg-warning-bg text-ink'
          : 'border-brand-100 bg-brand-50 text-ink-secondary',
      )}
    >
      <Info
        aria-hidden="true"
        className={cn('size-4 shrink-0', low ? 'text-warning' : 'text-brand-600')}
      />
      {trial ? (
        <>
          <p aria-live="polite">
            Bạn còn{' '}
            <strong className="text-body font-bold text-ink">
              {trial.remaining}/{trial.limit}
            </strong>{' '}
            lượt tra thử hôm nay.
          </p>
          <span aria-hidden="true" className="flex gap-1">
            {Array.from({ length: trial.limit }, (_, index) => (
              <span
                className={cn(
                  'size-2 rounded-full',
                  index >= trial.remaining ? 'bg-line' : low ? 'bg-warning' : 'bg-brand-600',
                )}
                key={index}
              />
            ))}
          </span>
        </>
      ) : (
        <p>Bạn đang tra cứu với tư cách khách.</p>
      )}
    </div>
  )
}
