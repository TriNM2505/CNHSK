import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

import { NotificationBell } from '@/features/notifications/NotificationBell'
import { HocBaLogo } from '@/shared/brand'
import { AccountMenu } from '@/shared/layout/AccountMenu'
import { cn } from '@/shared/lib/cn'
import { useCurrentUser } from '@/shared/auth'

type AppShellProps = {
  bottomNavigation?: ReactNode
  children: ReactNode
  /** Replaces the slim logo bar, e.g. with the full site navigation. */
  header?: ReactNode
  headerTone?: 'default' | 'brand'
  sidebar?: ReactNode
}

export function BrandLink({ inverse = false, logoClassName }: { inverse?: boolean; logoClassName?: string }) {
  const { user } = useCurrentUser()
  // Staff stay in their work area: for them the logo is only a mark, not a way back to the landing page.
  const staffOnly = Boolean(user && user.roles.length > 0 && !user.roles.includes('LEARNER'))
  if (staffOnly) {
    return (
      <span className="inline-flex min-h-touch shrink-0 items-center" data-testid="brand-mark">
        <HocBaLogo className={logoClassName} inverse={inverse} />
      </span>
    )
  }
  return (
    <Link
      aria-label="CNHSK - Trang chủ"
      className={cn(
        'inline-flex min-h-touch shrink-0 items-center rounded-sm no-underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
      )}
      to="/"
    >
      <HocBaLogo alt="" className={logoClassName} inverse={inverse} />
    </Link>
  )
}

export function AppShell({
  bottomNavigation,
  children,
  header,
  headerTone = 'default',
  sidebar,
}: AppShellProps) {
  const { user } = useCurrentUser()
  const inverse = headerTone === 'brand'

  return (
    <div className="flex min-h-screen min-w-0 flex-col bg-canvas">
      {header ?? <header
        className={cn(
          'sticky top-0 z-20 border-b px-4',
          inverse ? 'border-brand-700 bg-brand-800' : 'border-line bg-surface',
        )}
      >
        <div className="mx-auto flex h-16 max-w-content items-center justify-between gap-3">
          <BrandLink inverse={inverse} />
          {user ? (
            <div className="flex items-center gap-4">
              <NotificationBell inverse={inverse} />
              <AccountMenu inverse={inverse} />
            </div>
          ) : null}
        </div>
      </header>}

      <div className="flex min-w-0 flex-1">
        {sidebar}
        <div className={cn('min-w-0 flex-1', bottomNavigation && 'pb-20 sm:pb-0')}>{children}</div>
      </div>
      {bottomNavigation}
    </div>
  )
}
