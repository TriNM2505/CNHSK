import { BellRing, X } from 'lucide-react'
import { useEffect, useState } from 'react'

import { useApiQuery } from '@/shared/api'
import { Button, Card, IconButton } from '@/shared/ui'

import { getTodayReminder } from '../api/reminder'

export const REMINDER_DISMISSED_KEY = 'cnhsk.reminderBanner.dismissed'

function vietnamDay() {
  const parts = new Intl.DateTimeFormat('en', {
    timeZone: 'Asia/Ho_Chi_Minh',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date())
  return ['year', 'month', 'day']
    .map((type) => parts.find((part) => part.type === type)?.value)
    .join('-')
}

function readDismissedDay() {
  try {
    return window.localStorage.getItem(REMINDER_DISMISSED_KEY)
  } catch {
    return null
  }
}

export function ReminderBanner() {
  const query = useApiQuery(getTodayReminder, [])
  const { reload } = query
  const [day, setDay] = useState(vietnamDay)
  const [dismissedDay, setDismissedDay] = useState(readDismissedDay)

  useEffect(() => {
    const nextMidnight = Date.parse(`${day}T00:00:00+07:00`) + 86_400_000
    const timeout = window.setTimeout(
      () => {
        setDay(vietnamDay())
        reload()
      },
      Math.max(1, nextMidnight - Date.now()),
    )
    return () => window.clearTimeout(timeout)
  }, [day, reload])

  function dismiss() {
    setDismissedDay(day)
    try {
      window.localStorage.setItem(REMINDER_DISMISSED_KEY, day)
    } catch {
      // Keep dismissal working in memory when browser storage is unavailable.
    }
  }

  if (query.state !== 'ready' || !query.data?.show || dismissedDay === day) return null

  return (
    <aside
      aria-label="Nhắc ôn tập"
      className="mx-auto w-full max-w-screen-xl px-4 pt-6 sm:px-6 lg:px-8"
    >
      <Card className="flex flex-col gap-3 border-warning bg-warning-bg sm:flex-row sm:items-center sm:justify-between">
        <p className="flex items-center gap-3 text-body font-semibold text-ink">
          <BellRing aria-hidden="true" className="size-5 shrink-0 text-warning" />
          Đã đến giờ ôn: {query.data.dueCount} thẻ đang chờ
        </p>
        <div className="flex shrink-0 items-center gap-3">
          <Button to="/learn/review">Ôn ngay</Button>
          <IconButton label="Đóng nhắc ôn tập" onClick={dismiss}>
            <X aria-hidden="true" className="size-5" />
          </IconButton>
        </div>
      </Card>
    </aside>
  )
}
