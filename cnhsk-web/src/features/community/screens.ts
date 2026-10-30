import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-039': lazy(() => import('./pages/Scr039CommunityPostListPage')),
  'SCR-040': lazy(() => import('./pages/Scr040CommunityPostDetailPage')),
  'SCR-041': lazy(() => import('./pages/Scr041CommunityPostEditorPage')),
  'SCR-060': lazy(() => import('./pages/Scr060PostModerationQueuePage')),
  'SCR-061': lazy(() => import('./pages/Scr061PostModerationDetailPage')),
  'SCR-062': lazy(() => import('./pages/Scr062ViolationReportQueuePage')),
  'SCR-063': lazy(() => import('./pages/Scr063ViolationReportDetailPage')),
} satisfies ScreenCatalog
