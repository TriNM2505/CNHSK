import { lazy } from 'react'
import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-020': lazy(() => import('./pages/Scr020ExamListPage')),
  'SCR-021': lazy(() => import('./pages/Scr021ExamAttemptPage')),
} satisfies ScreenCatalog
