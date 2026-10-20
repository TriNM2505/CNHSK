import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-016': lazy(() => import('./pages/Scr016PronunciationPage')),
} satisfies ScreenCatalog
