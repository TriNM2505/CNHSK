import type { ReactNode } from 'react'
import { useLocation } from 'react-router-dom'

import { AppShell } from '@/shared/layout/AppShell'
import { NavigationLink } from '@/shared/layout/NavigationLink'
import { adminNavigation, isNavigationItemActive } from '@/shared/layout/navigation'
import { cn } from '@/shared/lib/cn'
import { useCurrentUser } from '@/shared/auth'

type AdminLayoutProps = {
  children: ReactNode
}

export function AdminLayout({ children }: AdminLayoutProps) {
  const location = useLocation()
  const { user } = useCurrentUser()
  const items = adminNavigation.filter(({ role }) => role && user?.roles.includes(role))

  const sidebar = (
    <aside className="hidden shrink-0 border-r border-line bg-surface sm:block sm:w-16 lg:w-60">
      <nav aria-label="Quản trị" className="sticky top-16 space-y-1 p-2 lg:p-3">
        {items.map((item) => {
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
      </nav>
    </aside>
  )

  const bottomNavigation = items.length ? (
    <nav
      aria-label="Quản trị trên thiết bị di động"
      className="fixed inset-x-0 bottom-0 z-20 grid border-t border-line bg-surface pb-[env(safe-area-inset-bottom)] sm:hidden"
      style={{ gridTemplateColumns: `repeat(${items.length}, minmax(0, 1fr))` }}
    >
      {items.map((item) => {
        const Icon = item.icon
        const active = isNavigationItemActive(location.pathname, item)
        return (
          <NavigationLink
            active={active}
            className={cn(
              'flex min-h-16 flex-col items-center justify-center gap-1 rounded-none px-2 text-caption',
              active && 'border-t-2 border-brand-600 bg-brand-50',
            )}
            key={item.to}
            to={item.to}
          >
            <Icon aria-hidden="true" className="size-5" />
            <span>{item.label}</span>
          </NavigationLink>
        )
      })}
    </nav>
  ) : undefined

  return (
    <AppShell bottomNavigation={bottomNavigation} headerTone="brand" sidebar={sidebar}>
      {children}
    </AppShell>
  )
}
