import { lazy } from 'react'
import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-070': lazy(() => import('./pages/Scr070ContestListPage')),
  'SCR-071': lazy(() => import('./pages/Scr071ContestEditorPage')),
} satisfies ScreenCatalog
