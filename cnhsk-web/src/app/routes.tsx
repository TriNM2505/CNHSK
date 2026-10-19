import type { RouteObject } from 'react-router-dom'
import { createBrowserRouter } from 'react-router-dom'

import { NotFoundPage } from '@/app/NotFoundPage'
import { AuthenticatedApplication, ScreenRoute } from '@/app/RouteScaffolding'

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
  ...previewRoutes,
  {
    element: <AuthenticatedApplication />,
    children: [
      { element: <ScreenRoute scr="SCR-001" />, path: '/' },
      { element: <ScreenRoute guard="guest" scr="SCR-002" />, path: '/login' },
      { element: <ScreenRoute guard="guest" scr="SCR-003" />, path: '/register' },
      { element: <ScreenRoute scr="SCR-004" />, path: '/verify-email' },
      { element: <ScreenRoute guard="guest" scr="SCR-005" />, path: '/forgot-password' },
      { element: <ScreenRoute scr="SCR-006" />, path: '/reset-password' },
      {
        element: <ScreenRoute guard="role" roles={['LEARNER']} scr="SCR-007" />,
        handle: { requiresAuthentication: true },
        path: '/me/profile',
      },
      {
        element: <ScreenRoute guard="authenticated" scr="SCR-008" />,
        handle: { requiresAuthentication: true },
        path: '/me/password',
      },
      {
        element: <ScreenRoute guard="role" roles={['ADMIN']} scr="SCR-076" />,
        handle: { requiresAuthentication: true },
        path: '/admin/users',
      },
      {
        element: <ScreenRoute guard="role" roles={['ADMIN']} scr="SCR-077" />,
        handle: { requiresAuthentication: true },
        path: '/admin/users/:id',
      },
      { element: <ScreenRoute scr="SCR-079" />, path: '/403' },
      {
        element: <ScreenRoute guard="role" roles={['CONTENT_EDITOR']} scr="SCR-068" />,
        handle: { requiresAuthentication: true },
        path: '/content/imports',
      },
      {
        element: <ScreenRoute guard="role" roles={['CONTENT_EDITOR']} scr="SCR-069" />,
        handle: { requiresAuthentication: true },
        path: '/content/imports/:id',
      },
      {
        element: <ScreenRoute guard="role" roles={['LEARNER']} scr="SCR-011" />,
        handle: { requiresAuthentication: true },
        path: '/learn/topics',
      },
      {
        element: <ScreenRoute guard="role" roles={['LEARNER']} scr="SCR-012" />,
        handle: { requiresAuthentication: true },
        path: '/learn/topics/:id/*',
      },
      {
        element: <ScreenRoute guard="role" roles={['LEARNER']} scr="SCR-017" />,
        handle: { requiresAuthentication: true },
        path: '/learn/grammar',
      },
      {
        element: <ScreenRoute guard="role" roles={['LEARNER']} scr="SCR-018" />,
        handle: { requiresAuthentication: true },
        path: '/learn/grammar/:id',
      },
      {
        element: <ScreenRoute guard="role" roles={['LEARNER']} scr="SCR-016" />,
        handle: { requiresAuthentication: true },
        path: '/learn/pronunciation',
      },
      {
        element: <ScreenRoute guard="role" roles={['LEARNER']} scr="SCR-016" />,
        handle: { requiresAuthentication: true },
        path: '/learn/pronunciation/stages/:stageIndex',
      },
      {
        element: <ScreenRoute guard="role" roles={['LEARNER']} scr="SCR-015" />,
        handle: { requiresAuthentication: true },
        path: '/learn/characters/recognition',
      },
      {
        element: <ScreenRoute guard="role" roles={['LEARNER']} scr="SCR-014" />,
        handle: { requiresAuthentication: true },
        path: '/learn/characters/writing',
      },
      {
        element: <ScreenRoute guard="role" roles={['LEARNER']} scr="SCR-014" />,
        handle: { requiresAuthentication: true },
        path: '/learn/characters/writing/challenge',
      },
      {
        element: <ScreenRoute guard="role" roles={['LEARNER']} scr="SCR-014" />,
        handle: { requiresAuthentication: true },
        path: '/learn/characters/writing/:lexemeId',
      },
      { element: <NotFoundPage />, path: '*' },
    ],
  },
]

export const router = createBrowserRouter(appRoutes)
