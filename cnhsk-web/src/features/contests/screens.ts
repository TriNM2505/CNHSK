import { lazy } from 'react'
import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-048': lazy(() => import('./pages/Scr048ContestListPage')),
  'SCR-049': lazy(() => import('./pages/Scr049ContestDetailPage')),
  'SCR-050': lazy(() => import('./pages/Scr050ContestAttemptPage')),
  'SCR-051': lazy(() => import('./pages/Scr051ContestResultsPage')),
} satisfies ScreenCatalog
