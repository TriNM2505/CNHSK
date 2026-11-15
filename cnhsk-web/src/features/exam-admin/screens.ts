import { lazy } from 'react'
import type { ScreenCatalog } from '@/app/screenRegistry'
export const screens = {
  'SCR-065': lazy(() => import('./pages/Scr065ExamListPage')),
  'SCR-066': lazy(() => import('./pages/Scr066ExamEditorPage')),
} satisfies ScreenCatalog
