import { createElement } from 'react'

import type { PreviewEntry } from '@/app/preview/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { AdminLayout } from '@/shared/layout'
import { previewContentEditor } from '@/shared/layout/Layout.preview'

import {
  TopicPrerequisitesScreenPreview,
  type TopicPrerequisitePreset,
} from './components/TopicPrerequisitesScreen.preview'
import { topicPrerequisitePreviews } from './components/TopicPrerequisitesView.preview'

const auth: AuthContextValue = {
  status: 'authenticated',
  user: { ...previewContentEditor, email: '' },
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
  unselected: 'Chưa chọn chủ đề',
  saving: 'Đang lưu',
  limit: 'Đủ mười tiên quyết',
  'candidate-loading': 'Đang tải ứng viên',
  'candidate-error': 'Lỗi tải ứng viên',
  pagination: 'Phân trang mẫu',
}
const presets: TopicPrerequisitePreset[] = [
  ...(Object.keys(topicPrerequisitePreviews) as (keyof typeof topicPrerequisitePreviews)[]),
  'unselected',
  'saving',
  'limit',
  'candidate-loading',
  'candidate-error',
  'pagination',
]
export const previews = presets.map((preset) => ({
  id: `scr-067-${preset}`,
  scr: 'SCR-067',
  title: `Tiên quyết chủ đề — ${titles[preset]}`,
  group: 'screen',
  fullFrame: true,
  states: preset === 'ready' ? ['loading', 'empty', 'error', 'ready'] : ['ready'],
  render: (state) =>
    createElement(
      AuthContext.Provider,
      { value: auth },
      createElement(
        AdminLayout,
        null,
        createElement(TopicPrerequisitesScreenPreview, {
          key: `${preset}:${state}`,
          examples: topicPrerequisitePreviews,
          preset: preset === 'ready' && state !== 'ready' ? state : preset,
        }),
      ),
    ),
})) satisfies readonly PreviewEntry[]
