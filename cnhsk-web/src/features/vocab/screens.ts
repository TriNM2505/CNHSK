import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-011': lazy(() => import('./pages/Scr011TopicListPage')),
  'SCR-012': lazy(() => import('./pages/Scr012TopicStudyPage')),
} satisfies ScreenCatalog
