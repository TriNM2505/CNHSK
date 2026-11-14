import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-046': lazy(() => import('./pages/Scr046TopicLeaderboardPage')),
  'SCR-047': lazy(() => import('./pages/Scr047GameLeaderboardPage')),
} satisfies ScreenCatalog
