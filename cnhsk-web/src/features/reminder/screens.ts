import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-009': lazy(() => import('./pages/Scr009ReminderSettingsPage')),
} satisfies ScreenCatalog
