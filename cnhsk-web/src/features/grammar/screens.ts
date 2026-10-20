import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-017': lazy(() => import('./pages/Scr017GrammarListPage')),
  'SCR-018': lazy(() => import('./pages/Scr018GrammarLessonPage')),
} satisfies ScreenCatalog
