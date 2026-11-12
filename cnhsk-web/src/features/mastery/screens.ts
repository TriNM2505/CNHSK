import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-010': lazy(() => import('./pages/Scr010LearningDashboardPage')),
  'SCR-019': lazy(() => import('./pages/Scr019LearningProgressPage')),
} satisfies ScreenCatalog
