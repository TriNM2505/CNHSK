import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-030': lazy(() => import('./pages/Scr030PlanCreditsPage')),
  'SCR-031': lazy(() => import('./pages/Scr031TransactionHistoryPage')),
  'SCR-073': lazy(() => import('./pages/Scr073CardBatchPage')),
} satisfies ScreenCatalog
