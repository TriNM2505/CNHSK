import type { ComponentType, LazyExoticComponent } from 'react'

import { screens as accountScreens } from '@/features/account/screens'
import { screens as adminUserScreens } from '@/features/admin-users/screens'
import { screens as authScreens } from '@/features/auth/screens'
import { screens as contentImportScreens } from '@/features/content-import/screens'
import { screens as grammarScreens } from '@/features/grammar/screens'
import { screens as hanziScreens } from '@/features/hanzi/screens'
import { screens as pronunciationScreens } from '@/features/pronunciation/screens'
import { screens as vocabScreens } from '@/features/vocab/screens'

export type ScreenId = `SCR-${string}`
export type ScreenComponent = LazyExoticComponent<ComponentType>
export type ScreenCatalog = Readonly<Partial<Record<ScreenId, ScreenComponent>>>

export interface FeatureScreenCatalog {
  feature: string
  screens: ScreenCatalog
}

export function createScreenRegistry(catalogs: readonly FeatureScreenCatalog[]): ScreenCatalog {
  const registry: Partial<Record<ScreenId, ScreenComponent>> = {}
  const owners = new Map<ScreenId, string>()

  for (const { feature, screens } of catalogs) {
    for (const [screenId, component] of Object.entries(screens) as [ScreenId, ScreenComponent][]) {
      const existingOwner = owners.get(screenId)
      if (owners.has(screenId)) {
        throw new Error(
          `Trùng mã màn hình ${screenId} giữa feature "${existingOwner}" và "${feature}".`,
        )
      }

      owners.set(screenId, feature)
      registry[screenId] = component
    }
  }

  return Object.freeze(registry)
}

export const screenRegistry = createScreenRegistry([
  { feature: 'auth', screens: authScreens },
  { feature: 'account', screens: accountScreens },
  { feature: 'admin-users', screens: adminUserScreens },
  { feature: 'content-import', screens: contentImportScreens },
  { feature: 'vocab', screens: vocabScreens },
  { feature: 'grammar', screens: grammarScreens },
  { feature: 'pronunciation', screens: pronunciationScreens },
  { feature: 'hanzi', screens: hanziScreens },
])
