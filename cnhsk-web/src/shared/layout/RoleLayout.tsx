import type { ReactNode } from 'react'

import { AdminLayout } from '@/shared/layout/AdminLayout'
import { LearnerLayout } from '@/shared/layout/LearnerLayout'
import { PublicLayout } from '@/shared/layout/PublicLayout'
import { useCurrentUser } from '@/shared/auth'

type RoleLayoutProps = {
  children: ReactNode
}

export function RoleLayout({ children }: RoleLayoutProps) {
  const { user } = useCurrentUser()

  if (user?.roles.includes('LEARNER')) return <LearnerLayout>{children}</LearnerLayout>
  if (user?.roles.some((role) => role === 'CONTENT_EDITOR' || role === 'ADMIN')) {
    return <AdminLayout>{children}</AdminLayout>
  }
  return <PublicLayout>{children}</PublicLayout>
}
