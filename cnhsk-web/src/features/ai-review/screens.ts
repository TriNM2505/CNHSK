import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-058': lazy(() => import('./pages/Scr058AIReviewQueuePage')),
} satisfies ScreenCatalog
