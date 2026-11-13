import { lazy } from 'react'

import type { ScreenCatalog } from '@/app/screenRegistry'

export const screens = {
  'SCR-072': lazy(() => import('./admin/pages/Scr072ReferenceManagementPage')),
  'SCR-053': lazy(() => import('./pages/Scr053MediaReferencesPage')),
  'SCR-054': lazy(() => import('./pages/Scr054BookCataloguePage')),
  'SCR-055': lazy(() => import('./pages/Scr055BookDetailPage')),
} satisfies ScreenCatalog
