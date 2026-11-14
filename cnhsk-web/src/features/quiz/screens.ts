import { lazy } from 'react'
import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-043': lazy(() => import('./pages/Scr043QuizListPage')),
  'SCR-044': lazy(() => import('./pages/Scr044QuizAttemptPlaceholderPage')),
} satisfies ScreenCatalog
