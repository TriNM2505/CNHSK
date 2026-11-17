import { lazy } from 'react'
import { describe, expect, it } from 'vitest'

import { appRouteDefinitions } from '@/app/routeAccess'
import { createScreenRegistry, screenRegistry, type ScreenCatalog } from '@/app/screenRegistry'
import { screens as aiReviewScreens } from '@/features/ai-review/screens'
import { screens as questionBankScreens } from '@/features/question-bank/screens'

const TestScreen = lazy(async () => ({ default: () => null }))

describe('screen registry', () => {
  it('registers the review queue once and retains the existing editor owner', () => {
    expect(Object.keys(aiReviewScreens)).toEqual(['SCR-058'])
    expect(screenRegistry['SCR-058']).toBe(aiReviewScreens['SCR-058'])
    expect(screenRegistry['SCR-059']).toBe(questionBankScreens['SCR-059'])
  })
  it('registers exam management without replacing contests or learner exams', () => {
    for (const id of ['SCR-065', 'SCR-066', 'SCR-070', 'SCR-071', 'SCR-020'] as const)
      expect(screenRegistry[id]).toBeDefined()
  })
  it('registers system settings', () => {
    expect(screenRegistry['SCR-078']).toBeDefined()
  })
  it('registers the topic prerequisite screen', () => {
    expect(screenRegistry['SCR-067']).toBeDefined()
  })
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
