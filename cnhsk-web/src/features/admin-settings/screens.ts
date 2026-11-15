import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-078': lazy(() => import('./pages/Scr078SystemSettingsPage')),
} satisfies ScreenCatalog
