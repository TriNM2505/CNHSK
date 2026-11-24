import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-084': lazy(() => import('./pages/Scr084ReadingPracticePage')),
} satisfies ScreenCatalog
