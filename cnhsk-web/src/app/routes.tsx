import type { RouteObject } from 'react-router-dom'
import { createBrowserRouter } from 'react-router-dom'

import { HomePage } from '@/app/HomePage'
import { NotFoundPage } from '@/app/NotFoundPage'

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

export const appRoutes: RouteObject[] = [
  { element: <HomePage />, path: '/' },
  ...previewRoutes,
  { element: <NotFoundPage />, path: '*' },
]

export const router = createBrowserRouter(appRoutes)
