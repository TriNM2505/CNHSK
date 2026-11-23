import type { ReactNode } from 'react'

import { PublicHeader } from '@/shared/layout/PublicHeader'

type PublicLayoutProps = {
  children: ReactNode
}

/** Public pages keep the full header for everyone; signed-in users get their account menu in it. */
export function PublicLayout({ children }: PublicLayoutProps) {
  return (
    <div className="flex min-h-screen min-w-0 flex-col bg-canvas">
      <PublicHeader />
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  )
}
