import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-019': lazy(() => import('./pages/Scr019LearningProgressPage')),
} satisfies ScreenCatalog
