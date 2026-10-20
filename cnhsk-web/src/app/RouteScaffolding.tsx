import { lazy, Suspense, type ReactNode } from 'react'
import { Outlet } from 'react-router-dom'

import { isRoleAccess, type RouteAccess, type RouteLayout } from '@/app/routeAccess'
import { screenRegistry, type ScreenId } from '@/app/screenRegistry'
import { AuthProvider, RequireAuthenticated, RequireGuest, RequireRole } from '@/shared/auth'
import { AdminLayout, LearnerLayout, PublicLayout, RoleLayout } from '@/shared/layout'
import { Skeleton } from '@/shared/ui'

const ScreenUnderConstructionPage = lazy(async () => {
  const module = await import('@/app/ScreenUnderConstructionPage')
  return { default: module.ScreenUnderConstructionPage }
})

type ScreenRouteProps = {
  access: RouteAccess
  layout: RouteLayout
  scr: ScreenId
}

function ScreenFallback() {
  return (
    <main className="mx-auto min-h-[calc(100vh-4rem)] max-w-content bg-canvas p-4 sm:p-6">
      <Skeleton className="mx-auto max-w-reading" variant="card" />
    </main>
  )
}

function ScreenLayout({ children, layout }: { children: ReactNode; layout: RouteLayout }) {
  switch (layout) {
    case 'learner':
      return <LearnerLayout>{children}</LearnerLayout>
    case 'admin':
      return <AdminLayout>{children}</AdminLayout>
    case 'by-role':
      return <RoleLayout>{children}</RoleLayout>
    case 'public':
      return <PublicLayout>{children}</PublicLayout>
  }
}

export function ScreenRoute({ access, layout, scr }: ScreenRouteProps) {
  const RegisteredScreen = screenRegistry[scr]
  const content = (
    <ScreenLayout layout={layout}>
      <Suspense fallback={<ScreenFallback />}>
        {RegisteredScreen ? (
          <RegisteredScreen />
        ) : (
          <ScreenUnderConstructionPage scr={scr} showAuthNotice={scr === 'SCR-002'} />
        )}
      </Suspense>
    </ScreenLayout>
  )

  if (access === 'guest') return <RequireGuest>{content}</RequireGuest>
  if (access === 'authenticated') return <RequireAuthenticated>{content}</RequireAuthenticated>
  if (isRoleAccess(access)) return <RequireRole roles={access}>{content}</RequireRole>
  return content
}

export function AuthenticatedApplication() {
  return (
    <AuthProvider>
      <Outlet />
    </AuthProvider>
  )
}
