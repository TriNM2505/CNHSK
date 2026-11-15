import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-067': lazy(() => import('./pages/Scr067TopicPrerequisitesPage')),
} satisfies ScreenCatalog
