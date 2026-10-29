import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-014': lazy(() => import('./pages/Scr014CharacterWritingPage')),
  'SCR-015': lazy(() => import('./pages/Scr015CharacterRecognitionPage')),
} satisfies ScreenCatalog
