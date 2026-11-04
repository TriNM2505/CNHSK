import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-059': lazy(() => import('./pages/Scr059QuestionEditPage')),
  'SCR-064': lazy(() => import('./pages/Scr064QuestionBankPage')),
} satisfies ScreenCatalog
