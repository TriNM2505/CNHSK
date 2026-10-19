import { CircleAlert } from 'lucide-react'
import type { ReactNode } from 'react'
import { useLocation } from 'react-router-dom'

import { AppShell } from '@/shared/layout/AppShell'
import { NavigationLink } from '@/shared/layout/NavigationLink'
import {
  gameNavigationItem,
  isNavigationItemActive,
  learnerNavigation,
} from '@/shared/layout/navigation'
import { cn } from '@/shared/lib/cn'
import { useCurrentUser } from '@/shared/auth'
import { Button } from '@/shared/ui'

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

  const sidebar = (
    <aside className="hidden shrink-0 border-r border-line bg-surface sm:block sm:w-16 lg:w-60">
      <nav aria-label="Học tập" className="sticky top-16 space-y-1 p-2 lg:p-3">
        {learnerNavigation.map((item) => {
          const Icon = item.icon
          return (
            <NavigationLink
              active={isNavigationItemActive(location.pathname, item)}
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
    </aside>
  )

  const bottomNavigation = (
    <nav
      aria-label="Học tập trên thiết bị di động"
      className="fixed inset-x-0 bottom-0 z-20 grid grid-cols-5 border-t border-line bg-surface px-1 pb-[env(safe-area-inset-bottom)] sm:hidden"
    >
      {learnerNavigation.map((item) => {
        const Icon = item.icon
        const active = isNavigationItemActive(location.pathname, item)
        return (
          <NavigationLink
            active={active}
            className={cn(
              'flex min-h-16 flex-col items-center justify-center gap-1 px-1 text-caption',
              active && 'rounded-none border-t-2 border-brand-600 bg-brand-50',
            )}
            key={item.to}
            to={item.to}
          >
            <Icon aria-hidden="true" className="size-5" />
            <span>{item.label}</span>
          </NavigationLink>
        )
      })}
      {gameOrigin ? (
        <a
          className="flex min-h-16 flex-col items-center justify-center gap-1 rounded-sm px-1 text-caption text-ink-secondary no-underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
          href={gameOrigin}
        >
          <gameNavigationItem.icon aria-hidden="true" className="size-5" />
          <span>{gameNavigationItem.label}</span>
        </a>
      ) : (
        <span
          aria-disabled="true"
          className="flex min-h-16 flex-col items-center justify-center gap-1 px-1 text-caption text-ink-muted"
        >
          <gameNavigationItem.icon aria-hidden="true" className="size-5" />
          <span>{gameNavigationItem.label}</span>
        </span>
      )}
    </nav>
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
      {children}
    </AppShell>
  )
}
