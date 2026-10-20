import { lazy } from 'react'
import { describe, expect, it } from 'vitest'

import { appRouteDefinitions } from '@/app/routeAccess'
import { createScreenRegistry, screenRegistry, type ScreenCatalog } from '@/app/screenRegistry'

const TestScreen = lazy(async () => ({ default: () => null }))

describe('screen registry', () => {
  it('rejects duplicate SCR ids across feature catalogs', () => {
    const first = { 'SCR-001': TestScreen } satisfies ScreenCatalog
    const second = { 'SCR-001': TestScreen } satisfies ScreenCatalog

    expect(() =>
      createScreenRegistry([
        { feature: 'auth', screens: first },
        { feature: 'vocab', screens: second },
      ]),
    ).toThrow('Trùng mã màn hình SCR-001 giữa feature "auth" và "vocab".')
  })

  it('registers only SCR ids declared by routeAccess', () => {
    const routedScreenIds = new Set<string>(
      appRouteDefinitions.flatMap((route) => (route.kind === 'screen' ? [route.scr] : [])),
    )
    const missingRoutes = Object.keys(screenRegistry).filter(
      (screenId) => !routedScreenIds.has(screenId),
    )

    expect(missingRoutes).toEqual([])
  })
})
