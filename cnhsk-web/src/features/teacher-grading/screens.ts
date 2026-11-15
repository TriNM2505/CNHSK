import { lazy } from 'react'
import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-056': lazy(() => import('./pages/Scr056TeacherGradingQueuePage')),
  'SCR-057': lazy(() => import('./pages/Scr057TeacherGradingWorkspacePage')),
} satisfies ScreenCatalog
