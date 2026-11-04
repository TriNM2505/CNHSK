import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-064': lazy(() => import('./pages/Scr064QuestionBankPage')),
} satisfies ScreenCatalog
