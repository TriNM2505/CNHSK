import { lazy, Suspense } from 'react'
import { Outlet } from 'react-router-dom'

import type { RoleCode } from '@/features/auth/types'
import { AuthProvider, RequireAuthenticated, RequireGuest, RequireRole } from '@/shared/auth'
import { Skeleton } from '@/shared/ui'

const ScreenUnderConstructionPage = lazy(async () => {
  const module = await import('@/app/ScreenUnderConstructionPage')
  return { default: module.ScreenUnderConstructionPage }
})

type ScreenRouteProps = {
  guard?: 'authenticated' | 'guest' | 'role'
  roles?: readonly RoleCode[]
  scr: `SCR-${string}`
}

function ScreenFallback() {
  return (
    <main className="mx-auto min-h-screen max-w-content bg-canvas p-4 sm:p-6">
      <Skeleton className="mx-auto max-w-reading" variant="card" />
    </main>
  )
}

export function ScreenRoute({ guard, roles = [], scr }: ScreenRouteProps) {
  const content = (
    <Suspense fallback={<ScreenFallback />}>
      <ScreenUnderConstructionPage scr={scr} showAuthNotice={scr === 'SCR-002'} />
    </Suspense>
  )

  if (guard === 'guest') return <RequireGuest>{content}</RequireGuest>
  if (guard === 'authenticated') return <RequireAuthenticated>{content}</RequireAuthenticated>
  if (guard === 'role') return <RequireRole roles={roles}>{content}</RequireRole>
  return content
}

export function AuthenticatedApplication() {
  return (
    <AuthProvider>
      <Outlet />
    </AuthProvider>
  )
}
