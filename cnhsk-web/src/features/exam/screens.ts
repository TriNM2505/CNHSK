import { lazy } from 'react'
import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-020': lazy(() => import('./pages/Scr020ExamListPage')),
} satisfies ScreenCatalog
