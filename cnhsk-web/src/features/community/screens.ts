import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-039': lazy(() => import('./pages/Scr039CommunityPostListPage')),
  'SCR-040': lazy(() => import('./pages/Scr040CommunityPostDetailPage')),
  'SCR-041': lazy(() => import('./pages/Scr041CommunityPostEditorPage')),
} satisfies ScreenCatalog
