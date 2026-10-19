import { canUserAccessRoute, findKnownRoute } from '@/app/routeAccess'
import type { CurrentUser, RoleCode } from '@/features/auth/types'

export const AUTHENTICATED_ROLES: readonly RoleCode[] = [
  'LEARNER',
  'TEACHER',
  'MODERATOR',
  'CONTENT_EDITOR',
  'ACCOUNTANT',
  'ADMIN',
]

export type AuthDestination = { type: 'internal'; to: string } | { type: 'external'; href: string }

function canAccessInternalPath(user: CurrentUser, pathname: string): boolean {
  const route = findKnownRoute(pathname)
  return route ? canUserAccessRoute(user, route.access) : false
}

function resolveRedirect(user: CurrentUser, redirect: string): AuthDestination | undefined {
  const localOrigin = window.location.origin

  if (redirect.startsWith('/') && !redirect.startsWith('//') && !redirect.startsWith('/\\')) {
    const url = new URL(redirect, localOrigin)
    if (url.origin === localOrigin && canAccessInternalPath(user, url.pathname)) {
      return { type: 'internal', to: `${url.pathname}${url.search}${url.hash}` }
    }
    return undefined
  }

  const gameOrigin = import.meta.env.VITE_GAME_ORIGIN
  if (!gameOrigin) return undefined

  try {
    const url = new URL(redirect)
    const allowedOrigin = new URL(gameOrigin).origin
    if (url.origin === allowedOrigin && ['http:', 'https:'].includes(url.protocol)) {
      return { type: 'external', href: url.href }
    }
  } catch {
    return undefined
  }

  return undefined
}

function getDefaultDestination(user: CurrentUser): AuthDestination {
  const uniqueRoles = [...new Set(user.roles)]
  if (uniqueRoles.length !== 1) return { type: 'internal', to: '/' }

  switch (uniqueRoles[0]) {
    case 'LEARNER':
      return { type: 'internal', to: '/learn/topics' }
    case 'CONTENT_EDITOR':
      return { type: 'internal', to: '/content/imports' }
    case 'ADMIN':
      return { type: 'internal', to: '/admin/users' }
    case 'TEACHER':
    case 'MODERATOR':
    case 'ACCOUNTANT':
      return { type: 'internal', to: '/403' }
    default:
      return { type: 'internal', to: '/' }
  }
}

export function getAuthenticatedDestination(
  user: CurrentUser,
  redirect?: string | null,
): AuthDestination {
  if (redirect) {
    const destination = resolveRedirect(user, redirect)
    if (destination) return destination
  }

  return getDefaultDestination(user)
}
