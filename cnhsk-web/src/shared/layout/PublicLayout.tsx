import type { ReactNode } from 'react'

import { AppShell } from '@/shared/layout/AppShell'
import { PublicHeader } from '@/shared/layout/PublicHeader'
import { useCurrentUser } from '@/shared/auth'

type PublicLayoutProps = {
  children: ReactNode
}

export function PublicLayout({ children }: PublicLayoutProps) {
  const { user } = useCurrentUser()

  if (user) return <AppShell>{children}</AppShell>

  return (
    <div className="flex min-h-screen min-w-0 flex-col bg-canvas">
      <PublicHeader />
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  )
}