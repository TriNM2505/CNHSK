import { lazy } from 'react'
import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-032': lazy(() => import('./pages/Scr032GradingRequestSubmissionPage')),
  'SCR-033': lazy(() => import('./pages/Scr033MyGradingRequestsPage')),
  'SCR-034': lazy(() => import('./pages/Scr034GradingRequestDetailPage')),
} satisfies ScreenCatalog
