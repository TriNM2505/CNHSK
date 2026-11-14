import type { MouseEventHandler, ReactNode } from 'react'
import { Link } from 'react-router-dom'

import { cn } from '@/shared/lib/cn'

type NavigationLinkProps = {
  active: boolean
  children: ReactNode
  className?: string
  to: string
  ariaLabel?: string
  onClick?: MouseEventHandler<HTMLAnchorElement>
}

export function NavigationLink({
  active,
  children,
  className,
  to,
  ariaLabel,
  onClick,
}: NavigationLinkProps) {
  return (
    <Link
      aria-label={ariaLabel}
      aria-current={active ? 'page' : undefined}
      onClick={onClick}
      className={cn(
        'rounded-sm no-underline transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
        active ? 'bg-brand-50 text-brand-700' : 'text-ink-secondary hover:bg-surface-subtle',
        className,
      )}
      to={to}
    >
      {children}
    </Link>
  )
}
