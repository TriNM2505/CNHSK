import { Bell, ClipboardList } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'

import { formatRelative } from '@/features/community/relative-time'
import { cn } from '@/shared/lib/cn'

import {
  getNotificationSummary,
  listNotifications,
  markNotificationsSeen,
  type NotificationPage,
  type NotificationSummary,
} from './api'
import { describeNotification, describeTask } from './notification-text'

/** The bell refreshes this often while the tab is visible. */
const POLL_MS = 30_000

/**
 * Bell for every signed-in role. The badge adds unread notifications to what staff still have to handle; opening
 * the panel loads the latest items and marks them read.
 */
export function NotificationBell({ inverse = false }: { inverse?: boolean }) {
  const [summary, setSummary] = useState<NotificationSummary>()
  const [page, setPage] = useState<NotificationPage>()
  const [open, setOpen] = useState(false)
  const [failed, setFailed] = useState(false)
  const root = useRef<HTMLDivElement>(null)

  const refresh = useCallback(() => {
    if (document.visibilityState !== 'visible') return
    getNotificationSummary().then(setSummary).catch(() => undefined)
  }, [])

  useEffect(() => {
    refresh()
    const timer = window.setInterval(refresh, POLL_MS)
    document.addEventListener('visibilitychange', refresh)
    return () => {
      window.clearInterval(timer)
      document.removeEventListener('visibilitychange', refresh)
    }
  }, [refresh])

  useEffect(() => {
    if (!open) return
    const close = (event: MouseEvent | KeyboardEvent) => {
      if (event instanceof KeyboardEvent ? event.key === 'Escape' : !root.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', close)
    document.addEventListener('keydown', close)
    return () => {
      document.removeEventListener('mousedown', close)
      document.removeEventListener('keydown', close)
    }
  }, [open])

  async function toggle() {
    if (open) {
      setOpen(false)
      return
    }
    setOpen(true)
    setFailed(false)
    try {
      const loaded = await listNotifications()
      setPage(loaded)
      if (loaded.items.some((item) => item.unread)) {
        await markNotificationsSeen()
        setSummary((current) => (current ? { ...current, unread: 0 } : current))
      }
    } catch {
      setFailed(true)
    }
  }

  const tasks = page?.tasks ?? summary?.tasks ?? []
  const pending = tasks.reduce((sum, task) => sum + task.count, 0)
  const badge = (summary?.unread ?? 0) + pending
  return (
    <div className="relative" ref={root}>
      <button
        aria-expanded={open}
        aria-haspopup="dialog"
        aria-label={badge > 0 ? `Thông báo, ${badge} mục mới` : 'Thông báo'}
        className={cn(
          'relative inline-flex size-touch items-center justify-center rounded-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
          inverse ? 'text-surface hover:bg-brand-700' : 'text-ink hover:text-brand-600',
        )}
        onClick={() => void toggle()}
        type="button"
      >
        {/* The count sits on the bell's shoulder, outlined so the bell stays readable behind it. */}
        <span className="relative inline-flex">
          <Bell aria-hidden="true" className="size-6" />
          {badge > 0 ? (
            <span
              aria-hidden="true"
              className={cn(
                'absolute -right-2.5 -top-2 flex h-[18px] min-w-[18px] items-center justify-center rounded-pill bg-danger px-1 text-[11px] font-bold leading-none text-surface ring-2',
                inverse ? 'ring-brand-800' : 'ring-surface',
              )}
            >
              {badge > 99 ? '99+' : badge}
            </span>
          ) : null}
        </span>
      </button>

      {open ? (
        <div
          aria-label="Thông báo"
          className="absolute right-0 top-full z-30 mt-2 w-[min(24rem,calc(100vw-2rem))] overflow-hidden rounded-md border border-line bg-surface shadow-lg"
          role="dialog"
        >
          <p className="border-b border-line px-4 py-3 text-label text-ink">Thông báo</p>
          <div className="max-h-[70vh] overflow-y-auto">
            {tasks.length > 0 ? (
              <section aria-label="Việc chờ xử lý" className="border-b border-line bg-warning-bg/60 px-2 py-2">
                <p className="px-2 pb-1 text-caption font-semibold uppercase tracking-wide text-accent-700">Việc chờ xử lý</p>
                {tasks.map((task) => {
                  const { text, to } = describeTask(task)
                  return (
                    <Link
                      className="flex items-center gap-3 rounded-sm px-2 py-2 text-body-sm text-ink no-underline hover:bg-surface"
                      key={task.key}
                      onClick={() => setOpen(false)}
                      to={to}
                    >
                      <ClipboardList aria-hidden="true" className="size-5 shrink-0 text-accent-700" />
                      {text}
                    </Link>
                  )
                })}
              </section>
            ) : null}

            {failed ? (
              <p className="px-4 py-6 text-center text-body-sm text-danger" role="alert">Chưa tải được thông báo.</p>
            ) : !page ? (
              <p className="px-4 py-6 text-center text-body-sm text-ink-secondary" role="status">Đang tải…</p>
            ) : page.items.length === 0 ? (
              <p className="px-4 py-6 text-center text-body-sm text-ink-secondary">Chưa có thông báo nào.</p>
            ) : (
              <ul>
                {page.items.map((item) => {
                  const { detail, text, to } = describeNotification(item)
                  return (
                    <li key={item.key}>
                      <Link
                        className={cn(
                          'flex gap-3 px-4 py-3 text-ink no-underline hover:bg-canvas',
                          item.unread && 'bg-brand-50/60',
                        )}
                        onClick={() => setOpen(false)}
                        to={to}
                      >
                        <span
                          aria-hidden="true"
                          className="flex size-9 shrink-0 items-center justify-center rounded-full bg-brand-50 text-label text-brand-700"
                        >
                          {(item.actorName ?? 'H').trim().charAt(0).toLocaleUpperCase('vi')}
                        </span>
                        <span className="min-w-0 flex-1">
                          <span className="block text-body-sm">{text}</span>
                          {detail ? <span className="mt-0.5 line-clamp-2 block text-caption text-ink-secondary">{detail}</span> : null}
                          <span className="mt-1 block text-caption text-ink-muted">{formatRelative(item.at)}</span>
                        </span>
                        {item.unread ? <span aria-label="Chưa đọc" className="mt-2 size-2.5 shrink-0 rounded-full bg-brand-600" /> : null}
                      </Link>
                    </li>
                  )
                })}
              </ul>
            )}
          </div>
        </div>
      ) : null}
    </div>
  )
}
