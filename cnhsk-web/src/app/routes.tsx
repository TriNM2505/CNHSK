import type { RouteObject } from 'react-router-dom'
import { createBrowserRouter } from 'react-router-dom'

import { NotFoundPage } from '@/app/NotFoundPage'
import { appRouteDefinitions, routeRequiresAuthentication } from '@/app/routeAccess'
import { AuthenticatedApplication, ScreenRoute } from '@/app/RouteScaffolding'
import { PublicLayout } from '@/shared/layout'

const previewRoutes: RouteObject[] = import.meta.env.DEV
  ? [
      {
        path: '/__preview/*',
        lazy: async () => {
          const { PreviewRoutes } = await import('@/app/preview/PreviewRoutes')

          return { Component: PreviewRoutes }
        },
      },
    ]
  : []

const applicationRoutes: RouteObject[] = appRouteDefinitions.map((route) => {
  if (route.kind === 'not-found') {
    return {
      element: (
        <PublicLayout>
          <NotFoundPage />
        </PublicLayout>
      ),
      path: route.path,
    }
  }

  return {
    element: <ScreenRoute access={route.access} layout={route.layout} scr={route.scr} />,
    handle: { requiresAuthentication: routeRequiresAuthentication(route.access) },
    path: route.path,
  }
})

export const appRoutes: RouteObject[] = [
  ...previewRoutes,
  {
    element: <AuthenticatedApplication />,
    children: applicationRoutes,
  },
]

export const router = createBrowserRouter(appRoutes)
