import { matchPath } from 'react-router-dom'

import type { CurrentUser, RoleCode } from '@/features/auth/types'

export type RouteAccess = 'public' | 'guest' | 'authenticated' | readonly RoleCode[]
export type RouteLayout = 'public' | 'learner' | 'admin' | 'by-role'

type ScreenRouteDefinition = {
  access: RouteAccess
  kind: 'screen'
  layout: RouteLayout
  path: string
  scr: `SCR-${string}`
}

type NotFoundRouteDefinition = {
  access: 'public'
  kind: 'not-found'
  layout: 'public'
  path: '*'
}

export type AppRouteDefinition = ScreenRouteDefinition | NotFoundRouteDefinition

export const appRouteDefinitions = [
  { access: 'public', kind: 'screen', layout: 'public', path: '/', scr: 'SCR-001' },
  { access: 'guest', kind: 'screen', layout: 'public', path: '/login', scr: 'SCR-002' },
  { access: 'guest', kind: 'screen', layout: 'public', path: '/register', scr: 'SCR-003' },
  {
    access: 'public',
    kind: 'screen',
    layout: 'public',
    path: '/verify-email',
    scr: 'SCR-004',
  },
  {
    access: 'guest',
    kind: 'screen',
    layout: 'public',
    path: '/forgot-password',
    scr: 'SCR-005',
  },
  {
    access: 'public',
    kind: 'screen',
    layout: 'public',
    path: '/reset-password',
    scr: 'SCR-006',
  },
  {
    access: ['LEARNER'],
    kind: 'screen',
    layout: 'learner',
    path: '/me/profile',
    scr: 'SCR-007',
  },
  {
    access: 'authenticated',
    kind: 'screen',
    layout: 'by-role',
    path: '/me/password',
    scr: 'SCR-008',
  },
  {
    access: ['ADMIN'],
    kind: 'screen',
    layout: 'admin',
    path: '/admin/users',
    scr: 'SCR-076',
  },
  {
    access: ['ADMIN'],
    kind: 'screen',
    layout: 'admin',
    path: '/admin/users/:id',
    scr: 'SCR-077',
  },
  { access: 'public', kind: 'screen', layout: 'public', path: '/403', scr: 'SCR-079' },
  {
    access: ['CONTENT_EDITOR'],
    kind: 'screen',
    layout: 'admin',
    path: '/content/imports',
    scr: 'SCR-068',
  },
  {
    access: ['CONTENT_EDITOR'],
    kind: 'screen',
    layout: 'admin',
    path: '/content/imports/:id',
    scr: 'SCR-069',
  },
  {
    access: ['LEARNER'],
    kind: 'screen',
    layout: 'learner',
    path: '/learn/topics',
    scr: 'SCR-011',
  },
  {
    access: ['LEARNER'],
    kind: 'screen',
    layout: 'learner',
    path: '/learn/topics/:id/*',
    scr: 'SCR-012',
  },
  {
    access: ['LEARNER'],
    kind: 'screen',
    layout: 'learner',
    path: '/learn/grammar',
    scr: 'SCR-017',
  },
  {
    access: ['LEARNER'],
    kind: 'screen',
    layout: 'learner',
    path: '/learn/grammar/:id',
    scr: 'SCR-018',
  },
  {
    access: ['LEARNER'],
    kind: 'screen',
    layout: 'learner',
    path: '/learn/pronunciation',
    scr: 'SCR-016',
  },
  {
    access: ['LEARNER'],
    kind: 'screen',
    layout: 'learner',
    path: '/learn/pronunciation/stages/:stageIndex',
    scr: 'SCR-016',
  },
  {
    access: ['LEARNER'],
    kind: 'screen',
    layout: 'learner',
    path: '/learn/characters/recognition',
    scr: 'SCR-015',
  },
  {
    access: ['LEARNER'],
    kind: 'screen',
    layout: 'learner',
    path: '/learn/characters/writing',
    scr: 'SCR-014',
  },
  {
    access: ['LEARNER'],
    kind: 'screen',
    layout: 'learner',
    path: '/learn/characters/writing/challenge',
    scr: 'SCR-014',
  },
  {
    access: ['LEARNER'],
    kind: 'screen',
    layout: 'learner',
    path: '/learn/characters/writing/:lexemeId',
    scr: 'SCR-014',
  },
  {
    access: ['LEARNER'],
    kind: 'screen',
    layout: 'learner',
    path: '/learn/exams',
    scr: 'SCR-020',
  },
  {
    access: ['LEARNER'],
    kind: 'screen',
    layout: 'learner',
    path: '/learn/exams/sessions/:sessionId',
    scr: 'SCR-021',
  },
  {
    access: ['LEARNER'],
    kind: 'screen',
    layout: 'learner',
    path: '/learn/exams/results/:sessionId',
    scr: 'SCR-022',
  },
  {
    access: ['LEARNER'],
    kind: 'screen',
    layout: 'learner',
    path: '/learn/placement/:runId',
    scr: 'SCR-022',
  },
  {
    access: ['LEARNER'],
    kind: 'screen',
    layout: 'learner',
    path: '/learn/practice/targeted',
    scr: 'SCR-023',
  },
  { access: 'public', kind: 'screen', layout: 'public', path: '/dictionary', scr: 'SCR-024' },
  { access: 'public', kind: 'screen', layout: 'public', path: '/dictionary/:id', scr: 'SCR-025' },
  {
    access: ['LEARNER'],
    kind: 'screen',
    layout: 'learner',
    path: '/translate',
    scr: 'SCR-026',
  },
  {
    access: ['LEARNER'],
    kind: 'screen',
    layout: 'learner',
    path: '/library/notes',
    scr: 'SCR-027',
  },
  {
    access: ['LEARNER'],
    kind: 'screen',
    layout: 'learner',
    path: '/library/decks',
    scr: 'SCR-028',
  },
  {
    access: ['LEARNER'],
    kind: 'screen',
    layout: 'learner',
    path: '/library/decks/:id',
    scr: 'SCR-029',
  },
  { access: 'public', kind: 'screen', layout: 'public', path: '/community', scr: 'SCR-039' },
  {
    access: ['LEARNER'],
    kind: 'screen',
    layout: 'learner',
    path: '/community/posts/new',
    scr: 'SCR-041',
  },
  {
    access: ['LEARNER'],
    kind: 'screen',
    layout: 'learner',
    path: '/community/posts/:id/edit',
    scr: 'SCR-041',
  },
  {
    access: 'public',
    kind: 'screen',
    layout: 'public',
    path: '/community/posts/:id',
    scr: 'SCR-040',
  },
  {
    access: ['MODERATOR'],
    kind: 'screen',
    layout: 'admin',
    path: '/moderation/posts',
    scr: 'SCR-060',
  },
  {
    access: ['MODERATOR'],
    kind: 'screen',
    layout: 'admin',
    path: '/moderation/posts/:id',
    scr: 'SCR-061',
  },
  {
    access: ['MODERATOR'],
    kind: 'screen',
    layout: 'admin',
    path: '/moderation/reports',
    scr: 'SCR-062',
  },
  {
    access: ['MODERATOR'],
    kind: 'screen',
    layout: 'admin',
    path: '/moderation/reports/:id',
    scr: 'SCR-063',
  },
  { access: 'public', kind: 'not-found', layout: 'public', path: '*' },
] as const satisfies readonly AppRouteDefinition[]

export function isRoleAccess(access: RouteAccess): access is readonly RoleCode[] {
  return Array.isArray(access)
}

export function routeRequiresAuthentication(access: RouteAccess): boolean {
  return access === 'authenticated' || isRoleAccess(access)
}

export function canUserAccessRoute(user: CurrentUser, access: RouteAccess): boolean {
  if (access === 'public' || access === 'authenticated') return true
  if (access === 'guest') return false
  return access.some((role) => user.roles.includes(role))
}

export function findKnownRoute(pathname: string): AppRouteDefinition | undefined {
  return appRouteDefinitions.find(
    (route) =>
      route.kind !== 'not-found' && matchPath({ end: true, path: route.path }, pathname) !== null,
  )
}
