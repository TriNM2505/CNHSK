import { Link, type LinkProps } from 'react-router-dom'

import { cn } from '@/shared/lib/cn'

export const AUTH_LINK_CLASS =
  'rounded-sm font-semibold text-brand-700 underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2'

export function AuthLink({ className, ...props }: LinkProps) {
  return <Link {...props} className={cn(AUTH_LINK_CLASS, className)} />
}
