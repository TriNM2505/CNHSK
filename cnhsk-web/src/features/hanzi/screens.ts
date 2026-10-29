import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-015': lazy(() => import('./pages/Scr015CharacterRecognitionPage')),
} satisfies ScreenCatalog
