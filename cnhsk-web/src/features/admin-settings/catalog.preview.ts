import { createElement } from 'react'

import type { PreviewEntry } from '@/app/preview/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { AdminLayout } from '@/shared/layout'
import { previewAdministrator } from '@/shared/layout/Layout.preview'

import { SettingsView } from './components/SettingsView'
import { settingsPreviews } from './components/SettingsView.preview'

const auth: AuthContextValue = {
  status: 'authenticated',
  user: previewAdministrator,
  error: null,
  reloadCurrentUser: async () => undefined,
  setAuthenticatedUser: () => undefined,
  signOut: async () => undefined,
}
export const previews = Object.entries(settingsPreviews).map(([state, props]) => ({
  id: `scr-078-${state}`,
  scr: 'SCR-078',
  title: `Cài đặt hệ thống — ${state}`,
  group: 'screen',
  fullFrame: true,
  render: () =>
    createElement(
      AuthContext.Provider,
      { value: auth },
      createElement(AdminLayout, null, createElement(SettingsView, props)),
    ),
})) satisfies readonly PreviewEntry[]
