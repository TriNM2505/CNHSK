import { describe, expect, it } from 'vitest'

import { appRouteDefinitions, canUserAccessRoute, findKnownRoute } from '@/app/routeAccess'
import { appRoutes } from '@/app/routes'

describe('route access table', () => {
  it('assigns all exam management paths only to CONTENT_EDITOR and keeps new static', () => {
    for (const path of ['/content/exams', '/content/exams/new', '/content/exams/2301']) {
      const route = findKnownRoute(path)!
      expect(route).toMatchObject({ access: ['CONTENT_EDITOR'], layout: 'admin' })
      expect(route.kind === 'screen' && route.scr).toBe(
        path === '/content/exams' ? 'SCR-065' : 'SCR-066',
      )
    }
    expect(findKnownRoute('/content/exams/new')?.path).toBe('/content/exams/new')
  })
  it('requires ADMIN for SCR-078 in the admin layout', () => {
    const route = findKnownRoute('/admin/settings')!
    expect(route).toMatchObject({ scr: 'SCR-078', access: ['ADMIN'], layout: 'admin' })
    const user = { id: 1, displayName: 'Test', email: 'test@example.invalid', emailVerified: true }
    expect(canUserAccessRoute({ ...user, roles: ['ADMIN'] }, route.access)).toBe(true)
    for (const role of ['CONTENT_EDITOR', 'LEARNER', 'TEACHER', 'MODERATOR', 'ACCOUNTANT'] as const)
      expect(canUserAccessRoute({ ...user, roles: [role] }, route.access)).toBe(false)
  })
  it('requires the exact CONTENT_EDITOR role for SCR-067 in the admin layout', () => {
    const route = findKnownRoute('/content/topics/prerequisites')!
    expect(route).toMatchObject({ scr: 'SCR-067', access: ['CONTENT_EDITOR'], layout: 'admin' })
    const user = { id: 1, displayName: 'Test', email: 'test@example.invalid', emailVerified: true }
    expect(canUserAccessRoute({ ...user, roles: ['CONTENT_EDITOR'] }, route.access)).toBe(true)
    for (const role of ['ADMIN', 'LEARNER', 'TEACHER', 'MODERATOR', 'ACCOUNTANT'] as const) {
      expect(canUserAccessRoute({ ...user, roles: [role] }, route.access)).toBe(false)
    }
  })
  it('defines every production route rendered by routes.tsx exactly once', () => {
    const productionRoot = appRoutes.find((route) => route.children)
    const configuredPaths = productionRoot?.children?.map(({ path }) => path)
    const accessPaths = appRouteDefinitions.map(({ path }) => path)

    expect(configuredPaths).toEqual(accessPaths)
    expect(new Set(accessPaths).size).toBe(accessPaths.length)
  })

  it('matches concrete child paths without treating the 404 route as access', () => {
    const topicRoute = findKnownRoute('/learn/topics/topic-1/study')
    expect(topicRoute?.kind).toBe('screen')
    expect(topicRoute?.kind === 'screen' ? topicRoute.scr : undefined).toBe('SCR-012')
    expect(findKnownRoute('/khong-ton-tai')).toBeUndefined()
  })

  it('marks the password route as authenticated with a role-selected layout', () => {
    const passwordRoute = appRouteDefinitions.find(({ path }) => path === '/me/password')

    expect(passwordRoute).toMatchObject({ access: 'authenticated', layout: 'by-role' })
  })
})
