import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-007': lazy(() => import('./pages/Scr007ProfilePage')),
  'SCR-008': lazy(() => import('./pages/Scr008ChangePasswordPage')),
} satisfies ScreenCatalog
