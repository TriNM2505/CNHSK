import { createElement } from 'react'

import type { PreviewEntry } from '@/app/preview/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { LearnerLayout } from '@/shared/layout'
import { previewLearner } from '@/shared/layout/Layout.preview'

import { RecognitionView } from './components/RecognitionView'
import { recognitionPreviews } from './components/RecognitionView.preview'

const learnerAuth: AuthContextValue = {
  error: null,
  reloadCurrentUser: async () => undefined,
  setAuthenticatedUser: () => undefined,
  signOut: async () => undefined,
  status: 'authenticated',
  user: previewLearner,
}

function inLearnerLayout(element: ReturnType<typeof createElement>) {
  return createElement(
    AuthContext.Provider,
    { value: learnerAuth },
    createElement(LearnerLayout, null, element),
  )
}

const recognitionEntries = [
  ['scr-015-setup', 'Nhận diện chữ — Chọn chế độ', recognitionPreviews.setup],
  ['scr-015-no-characters', 'Nhận diện chữ — Chưa có chữ', recognitionPreviews.noCharacters],
  ['scr-015-question', 'Nhận diện chữ — Nhìn chữ chọn nghĩa', recognitionPreviews.question],
  ['scr-015-feedback', 'Nhận diện chữ — Phản hồi', recognitionPreviews.feedback],
  ['scr-015-audio', 'Nhận diện chữ — Nghe chọn chữ', recognitionPreviews.audio],
  ['scr-015-finished', 'Nhận diện chữ — Kết quả', recognitionPreviews.finished],
  ['scr-015-loading', 'Nhận diện chữ — Đang tải', recognitionPreviews.loading],
  ['scr-015-error', 'Nhận diện chữ — Lỗi tải', recognitionPreviews.error],
] as const

export const previews = recognitionEntries.map(([id, title, props]) => ({
  fullFrame: true,
  group: 'screen' as const,
  id,
  render: () => inLearnerLayout(createElement(RecognitionView, props)),
  scr: 'SCR-015',
  title,
})) satisfies readonly PreviewEntry[]
