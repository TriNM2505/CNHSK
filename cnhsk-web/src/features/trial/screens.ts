import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-083': lazy(() => import('./Scr083TrialLearningPage')),
} satisfies ScreenCatalog
