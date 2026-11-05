import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-030': lazy(() => import('./pages/Scr030PlanCreditsPage')),
  'SCR-031': lazy(() => import('./pages/Scr031TransactionHistoryPage')),
  'SCR-073': lazy(() => import('./pages/Scr073CardBatchPage')),
  'SCR-074': lazy(() => import('./pages/Scr074PlanManagementPage')),
  'SCR-075': lazy(() => import('./pages/Scr075CreditLedgerPage')),
} satisfies ScreenCatalog
