import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-024': lazy(() => import('./pages/Scr024DictionarySearchPage')),
  'SCR-025': lazy(() => import('./pages/Scr025DictionaryDetailPage')),
  'SCR-027': lazy(() => import('./pages/Scr027PersonalNotesPage')),
  'SCR-028': lazy(() => import('./pages/Scr028DeckListPage')),
  'SCR-029': lazy(() => import('./pages/Scr029DeckDetailPage')),
} satisfies ScreenCatalog
