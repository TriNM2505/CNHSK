import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-011': lazy(() => import('./pages/Scr011TopicListPage')),
} satisfies ScreenCatalog
