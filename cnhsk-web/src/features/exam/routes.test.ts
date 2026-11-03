import { describe, expect, it } from 'vitest'

import { appRouteDefinitions } from '@/app/routeAccess'

const examRouteExpectations = [
  ['/learn/exams', 'SCR-020'],
  ['/learn/exams/sessions/:sessionId', 'SCR-021'],
  ['/learn/exams/results/:sessionId', 'SCR-022'],
  ['/learn/placement/:runId', 'SCR-022'],
  ['/learn/practice/targeted', 'SCR-023'],
] as const

describe('exam routes', () => {
  it('FR001_routes: khai báo năm route learner theo §12', () => {
    const routes = examRouteExpectations.map(([path]) =>
      appRouteDefinitions.find((route) => route.path === path),
    )

    expect(routes).toHaveLength(5)
    expect(routes).toEqual(
      examRouteExpectations.map(([path, scr]) =>
        expect.objectContaining({ access: ['LEARNER'], layout: 'learner', path, scr }),
      ),
    )
  })
})
