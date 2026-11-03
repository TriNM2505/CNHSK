import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-024': lazy(() => import('./pages/Scr024DictionarySearchPage')),
  'SCR-025': lazy(() => import('./pages/Scr025DictionaryDetailPage')),
} satisfies ScreenCatalog
