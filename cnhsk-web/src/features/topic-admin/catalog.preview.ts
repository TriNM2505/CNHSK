import { createElement } from 'react'

import type { PreviewEntry } from '@/app/preview/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { AdminLayout } from '@/shared/layout'
import { previewContentEditor } from '@/shared/layout/Layout.preview'

import { TopicPrerequisitesView } from './components/TopicPrerequisitesView'
import { topicPrerequisitePreviews } from './components/TopicPrerequisitesView.preview'

const auth: AuthContextValue = {
  status: 'authenticated',
  user: previewContentEditor,
  error: null,
  reloadCurrentUser: async () => undefined,
  setAuthenticatedUser: () => undefined,
  signOut: async () => undefined,
}
const titles = {
  ready: 'Mặc định',
  cycle: 'Đường vòng',
  warning: 'Cảnh báo cấp',
  loading: 'Đang tải',
  empty: 'Không có chủ đề',
  error: 'Lỗi tải',
}
export const previews = Object.entries(topicPrerequisitePreviews).map(([state, props]) => ({
  id: `scr-067-${state}`,
  scr: 'SCR-067',
  title: `Tiên quyết chủ đề — ${titles[state as keyof typeof titles]}`,
  group: 'screen',
  fullFrame: true,
  render: () =>
    createElement(
      AuthContext.Provider,
      { value: auth },
      createElement(AdminLayout, null, createElement(TopicPrerequisitesView, props)),
    ),
})) satisfies readonly PreviewEntry[]
