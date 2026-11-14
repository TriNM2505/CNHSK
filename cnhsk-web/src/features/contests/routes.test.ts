import { describe, expect, it } from 'vitest'
import { appRouteDefinitions } from '@/app/routeAccess'
import { screenRegistry } from '@/app/screenRegistry'

describe('contest routes', () => {
  it('FR015_FR016_keeps the four canonical screens and their access boundaries', () => {
    const expected = [
      { path: '/contests', scr: 'SCR-048', access: 'public', layout: 'by-role' },
      { path: '/contests/:id', scr: 'SCR-049', access: 'public', layout: 'by-role' },
      { path: '/contests/:id/attempt', scr: 'SCR-050', access: ['LEARNER'], layout: 'learner' },
      { path: '/contests/:id/results', scr: 'SCR-051', access: ['LEARNER'], layout: 'learner' },
    ]
    for (const route of expected) {
      expect(appRouteDefinitions.find(({ path }) => path === route.path)).toEqual({
        ...route,
        kind: 'screen',
      })
      expect(screenRegistry[route.scr as keyof typeof screenRegistry]).toBeDefined()
    }
  })
})
