import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-076': lazy(() => import('./pages/Scr076UserListPage')),
  'SCR-077': lazy(() => import('./pages/Scr077UserDetailPage')),
} satisfies ScreenCatalog
