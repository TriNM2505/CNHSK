import { CircleAlert, MoreHorizontal, X } from 'lucide-react'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useLocation } from 'react-router-dom'

import { AppShell } from '@/shared/layout/AppShell'
import { NavigationLink } from '@/shared/layout/NavigationLink'
import {
  gameNavigationItem,
  isNavigationItemActive,
  learnerNavigation,
  learnerCommunityNavigation,
  type NavigationItem,
} from '@/shared/layout/navigation'
import { cn } from '@/shared/lib/cn'
import { useCurrentUser } from '@/shared/auth'
import { Button } from '@/shared/ui'
import { AssistantHost } from '@/features/assistant/components/AssistantHost'

type LearnerLayoutProps = {
  children: ReactNode
  gameOrigin?: string
}

export function LearnerLayout({
  children,
  gameOrigin = import.meta.env.VITE_GAME_ORIGIN,
}: LearnerLayoutProps) {
  const location = useLocation()
  const { user } = useCurrentUser()
  const [moreOpen, setMoreOpen] = useState(false)
  const moreButton = useRef<HTMLButtonElement>(null)
  const primaryMobile = learnerNavigation.filter((item) => item.mobilePlacement !== 'more')
  const moreMobile = learnerNavigation.filter((item) => item.mobilePlacement === 'more')

  useEffect(() => {
    setMoreOpen(false)
  }, [location.pathname, location.search, location.hash])
  useEffect(() => {
    if (!moreOpen) return
    function closeWithEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        event.preventDefault()
        setMoreOpen(false)
        moreButton.current?.focus()
      }
    }
    document.addEventListener('keydown', closeWithEscape)
    return () => document.removeEventListener('keydown', closeWithEscape)
  }, [moreOpen])

  function menuLink(item: NavigationItem) {
    const Icon = item.icon
    return (
      <NavigationLink
        key={item.to}
        active={isNavigationItemActive(location.pathname, item)}
        ariaLabel={item.label}
        to={item.to}
        className="flex min-h-touch min-w-0 items-center gap-3 px-3"
        onClick={() => setMoreOpen(false)}
      >
        <Icon aria-hidden="true" className="size-5 shrink-0" />
        <span className="min-w-0 break-words">{item.label}</span>
      </NavigationLink>
    )
  }

  const sidebar = (
    <aside className="hidden shrink-0 border-r border-line bg-surface sm:block sm:w-16 lg:w-60">
      <div className="sticky top-16">
        <nav aria-label="Học tập" className="space-y-1 p-2 lg:p-3">
          {learnerNavigation.map((item) => {
            const Icon = item.icon
            return (
              <NavigationLink
                active={isNavigationItemActive(location.pathname, item)}
                ariaLabel={item.label}
                className="flex min-h-touch items-center justify-center gap-3 px-3 lg:justify-start"
                key={item.to}
                to={item.to}
              >
                <Icon aria-hidden="true" className="size-5 shrink-0" />
                <span className="sr-only lg:not-sr-only">{item.label}</span>
              </NavigationLink>
            )
          })}
          {gameOrigin ? (
            <a
              className="flex min-h-touch items-center justify-center gap-3 rounded-sm px-3 text-ink-secondary no-underline hover:bg-surface-subtle focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus lg:justify-start"
              href={gameOrigin}
            >
              <gameNavigationItem.icon aria-hidden="true" className="size-5 shrink-0" />
              <span className="sr-only lg:not-sr-only">{gameNavigationItem.label}</span>
            </a>
          ) : null}
        </nav>
        <nav aria-label="Cộng đồng" className="space-y-1 border-t border-line p-2 lg:p-3">
          <h2 className="sr-only px-3 py-2 text-caption font-semibold text-ink-muted lg:not-sr-only">
            Cộng đồng
          </h2>
          {learnerCommunityNavigation.map((item) => {
            const Icon = item.icon
            return (
              <NavigationLink
                key={item.to}
                ariaLabel={item.label}
                active={isNavigationItemActive(location.pathname, item)}
                to={item.to}
                className="flex min-h-touch items-center justify-center gap-3 px-3 lg:justify-start"
              >
                <Icon aria-hidden="true" className="size-5 shrink-0" />
                <span className="sr-only lg:not-sr-only">{item.label}</span>
              </NavigationLink>
            )
          })}
        </nav>
      </div>
    </aside>
  )

  const bottomNavigation = (
    <div className="fixed inset-x-0 bottom-0 z-20 border-t border-line bg-surface pb-[env(safe-area-inset-bottom)] sm:hidden">
      {moreOpen ? (
        <section
          id="learner-more-menu"
          className="absolute inset-x-0 bottom-full max-h-[calc(100dvh-6rem)] overflow-y-auto border-y border-line bg-surface p-4 shadow-lg"
        >
          <div className="mb-2 flex items-center justify-between gap-3">
            <h2 className="text-h3 text-ink">Thêm</h2>
            <button
              aria-label="Đóng menu bổ sung"
              className="flex size-touch items-center justify-center rounded-sm text-ink focus-visible:ring-2 focus-visible:ring-focus"
              onClick={() => {
                setMoreOpen(false)
                moreButton.current?.focus()
              }}
              type="button"
            >
              <X aria-hidden="true" className="size-5" />
            </button>
          </div>
          <nav aria-label="Điều hướng bổ sung" className="min-w-0 space-y-1">
            {moreMobile.map(menuLink)}
            {gameOrigin ? (
              <a
                aria-label={gameNavigationItem.label}
                href={gameOrigin}
                className="flex min-h-touch items-center gap-3 rounded-sm px-3 text-ink-secondary no-underline focus-visible:ring-2 focus-visible:ring-focus"
                onClick={() => setMoreOpen(false)}
              >
                <gameNavigationItem.icon aria-hidden="true" className="size-5 shrink-0" />
                <span>{gameNavigationItem.label}</span>
              </a>
            ) : (
              <p
                aria-disabled="true"
                className="flex min-h-touch items-center gap-3 px-3 text-ink-muted"
              >
                <gameNavigationItem.icon aria-hidden="true" className="size-5 shrink-0" />
                <span>{gameNavigationItem.label}</span>
              </p>
            )}
            <h3 className="border-t border-line px-3 pt-3 text-caption font-semibold text-ink-muted">
              Cộng đồng
            </h3>
            {learnerCommunityNavigation.map(menuLink)}
          </nav>
        </section>
      ) : null}
      <nav aria-label="Học tập trên thiết bị di động" className="grid grid-cols-5 px-1">
        {primaryMobile.map((item) => {
          const Icon = item.icon
          const active = isNavigationItemActive(location.pathname, item)
          return (
            <NavigationLink
              active={active}
              ariaLabel={item.label}
              className={cn(
                'flex min-h-16 min-w-0 flex-col items-center justify-center gap-1 px-1 text-center text-caption',
                active && 'rounded-none border-t-2 border-brand-600 bg-brand-50',
              )}
              key={item.to}
              to={item.to}
            >
              <Icon aria-hidden="true" className="size-5" />
              <span>{item.mobileLabel ?? item.label}</span>
            </NavigationLink>
          )
        })}
        <button
          ref={moreButton}
          type="button"
          aria-label="Thêm"
          aria-expanded={moreOpen}
          aria-controls="learner-more-menu"
          onClick={() => setMoreOpen((open) => !open)}
          className={cn(
            'flex min-h-16 min-w-0 flex-col items-center justify-center gap-1 rounded-sm px-1 text-caption text-ink-secondary focus-visible:ring-2 focus-visible:ring-focus',
            [...moreMobile, ...learnerCommunityNavigation].some((item) =>
              isNavigationItemActive(location.pathname, item),
            ) && 'border-t-2 border-brand-600 bg-brand-50 text-brand-700',
          )}
        >
          <MoreHorizontal aria-hidden="true" className="size-5" />
          <span>Thêm</span>
        </button>
      </nav>
    </div>
  )

  return (
    <AppShell bottomNavigation={bottomNavigation} sidebar={sidebar}>
      {user && !user.emailVerified ? (
        <aside className="border-b border-warning bg-warning-bg px-4 py-3" role="status">
          <div className="mx-auto flex max-w-content flex-wrap items-center justify-center gap-3 sm:justify-between">
            <p className="flex items-center gap-2 text-body-sm text-ink">
              <CircleAlert aria-hidden="true" className="size-5 shrink-0 text-warning" />
              Xác minh email để dùng đầy đủ
            </p>
            <Button size="sm" to="/verify-email" variant="outline">
              Xác minh email
            </Button>
          </div>
        </aside>
      ) : null}
      <AssistantHost>{children}</AssistantHost>
    </AppShell>
  )
}
