import { describe, expect, it } from 'vitest'
import { appRouteDefinitions, canUserAccessRoute, findKnownRoute } from '@/app/routeAccess'
import { screenRegistry } from '@/app/screenRegistry'
import type { CurrentUser, RoleCode } from '@/features/auth/types'

describe('contest admin routes', () => {
  const expected = [
    { path: '/content/contests', scr: 'SCR-070' },
    { path: '/content/contests/new', scr: 'SCR-071' },
    { path: '/content/contests/:id', scr: 'SCR-071' },
  ] as const
  for (const route of expected)
    it(`FR025_FR026 registers ${route.path} with exact editor access`, () => {
      const definition = appRouteDefinitions.find(({ path }) => path === route.path)
      expect(definition).toEqual({
        ...route,
        kind: 'screen',
        access: ['CONTENT_EDITOR'],
        layout: 'admin',
      })
      expect(screenRegistry[route.scr]).toBeDefined()
      for (const role of [
        'ADMIN',
        'LEARNER',
        'TEACHER',
        'MODERATOR',
        'ACCOUNTANT',
        'CONTENT_EDITOR',
      ] as RoleCode[]) {
        expect(canUserAccessRoute({ roles: [role] } as CurrentUser, definition!.access)).toBe(
          role === 'CONTENT_EDITOR',
        )
      }
    })
  it('matches new before the parameter route and keeps detail within SCR-071', () => {
    expect(findKnownRoute('/content/contests/new')?.path).toBe('/content/contests/new')
    expect(findKnownRoute('/content/contests/2109')?.path).toBe('/content/contests/:id')
  })
})
