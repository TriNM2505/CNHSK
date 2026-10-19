import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-068': lazy(() => import('./pages/Scr068LearningDataImportPage')),
  'SCR-069': lazy(() => import('./pages/Scr069ImportReportPage')),
} satisfies ScreenCatalog
