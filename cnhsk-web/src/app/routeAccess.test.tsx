import { describe, expect, it } from 'vitest'

import { appRouteDefinitions, findKnownRoute } from '@/app/routeAccess'
import { appRoutes } from '@/app/routes'

describe('route access table', () => {
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
