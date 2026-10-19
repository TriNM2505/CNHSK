import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-002': lazy(() => import('./pages/Scr002SignInPage')),
  'SCR-003': lazy(() => import('./pages/Scr003RegisterPage')),
  'SCR-004': lazy(() => import('./pages/Scr004VerifyEmailPage')),
} satisfies ScreenCatalog
