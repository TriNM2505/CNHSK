import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

import { AccountMenu } from '@/shared/layout/AccountMenu'
import { cn } from '@/shared/lib/cn'
import { useCurrentUser } from '@/shared/auth'

type AppShellProps = {
  bottomNavigation?: ReactNode
  children: ReactNode
  headerTone?: 'default' | 'brand'
  sidebar?: ReactNode
}

export function BrandLink({ inverse = false }: { inverse?: boolean }) {
  return (
    <Link
      aria-label="CNHSK - Trang chủ"
      className={cn(
        'inline-flex min-h-touch items-center gap-2 rounded-sm text-h3 no-underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
        inverse ? 'text-surface' : 'text-brand-700',
      )}
      to="/"
    >
      <span aria-hidden="true" className="font-hanzi text-h2">
        汉
      </span>
      <span>CNHSK</span>
    </Link>
  )
}

export function AppShell({
  bottomNavigation,
  children,
  headerTone = 'default',
  sidebar,
}: AppShellProps) {
  const { user } = useCurrentUser()
  const inverse = headerTone === 'brand'

  return (
    <div className="flex min-h-screen min-w-0 flex-col bg-canvas">
      <header
        className={cn(
          'sticky top-0 z-20 border-b px-4',
          inverse ? 'border-brand-700 bg-brand-800' : 'border-line bg-surface',
        )}
      >
        <div className="mx-auto flex h-16 max-w-content items-center justify-between gap-3">
          <BrandLink inverse={inverse} />
          {user ? <AccountMenu inverse={inverse} /> : null}
        </div>
      </header>

      <div className="flex min-w-0 flex-1">
        {sidebar}
        <div className={cn('min-w-0 flex-1', bottomNavigation && 'pb-20 sm:pb-0')}>{children}</div>
      </div>
      {bottomNavigation}
    </div>
  )
}
