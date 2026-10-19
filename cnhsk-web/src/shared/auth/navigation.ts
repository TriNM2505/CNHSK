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

function hasRole(user: CurrentUser, role: RoleCode): boolean {
  return user.roles.includes(role)
}

function canAccessInternalPath(user: CurrentUser, pathname: string): boolean {
  if (['/', '/verify-email', '/reset-password', '/403'].includes(pathname)) return true
  if (pathname === '/me/password') return true
  if (pathname === '/me/profile') return hasRole(user, 'LEARNER')
  if (pathname === '/admin/users' || pathname.startsWith('/admin/users/')) {
    return hasRole(user, 'ADMIN')
  }
  if (pathname === '/content/imports' || pathname.startsWith('/content/imports/')) {
    return hasRole(user, 'CONTENT_EDITOR')
  }
  if (pathname === '/learn/topics' || pathname.startsWith('/learn/topics/')) {
    return hasRole(user, 'LEARNER')
  }
  if (pathname === '/learn/grammar' || pathname.startsWith('/learn/grammar/')) {
    return hasRole(user, 'LEARNER')
  }
  if (pathname === '/learn/pronunciation' || pathname.startsWith('/learn/pronunciation/')) {
    return hasRole(user, 'LEARNER')
  }
  if (pathname.startsWith('/learn/characters/')) return hasRole(user, 'LEARNER')

  return false
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
      return { type: 'internal', to: '/me/profile' }
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
