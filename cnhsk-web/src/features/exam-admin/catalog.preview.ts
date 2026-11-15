import { createElement as h, useState } from 'react'
import type { PreviewEntry } from '@/app/preview/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { AdminLayout } from '@/shared/layout'
import { previewContentEditor } from '@/shared/layout/Layout.preview'
import { Button } from '@/shared/ui'
import { ExamListView } from './components/ExamListView'
import { listPreviews } from './components/ExamListView.preview'
import { ExamEditorView } from './components/ExamEditorView'
import { editorPreviews } from './components/ExamEditorView.preview'
import { validateForm } from './examForm'
const auth: AuthContextValue = {
  status: 'authenticated',
  user: previewContentEditor,
  error: null,
  reloadCurrentUser: async () => undefined,
  setAuthenticatedUser: () => undefined,
  signOut: async () => undefined,
}
function EditorPreview({ mode }: { mode: string }) {
  const props = editorPreviews[mode]!
  const [form, setForm] = useState(props.form)
  const [dirty, setDirty] = useState(props.dirty)
  const [notice, setNotice] = useState<string>()
  const [errors, setErrors] = useState(props.errors)
  return h(ExamEditorView, {
    ...props,
    form,
    dirty,
    notice,
    errors,
    onChange: (next) => {
      setForm(next)
      setDirty(true)
    },
    onSave: () => {
      const issues = validateForm(form)
      setErrors(issues)
      if (!issues.length) {
        setDirty(false)
        setNotice('Đã lưu trong bản xem trước; không gửi dữ liệu lên máy chủ.')
      }
    },
    onPublish: () => setNotice('Bản xem trước: công bố luôn được kiểm tra trên máy chủ.'),
    onPicker: () => setNotice('Bản xem trước không kết nối ngân hàng câu hỏi.'),
    onClone: () => setNotice('Nhân bản bản đã lưu trong giao diện thật.'),
    onReload: () => {
      setForm(props.form)
      setDirty(false)
      setErrors([])
    },
  })
}
function frame(content: ReturnType<typeof h>) {
  return h(AuthContext.Provider, { value: auth }, h(AdminLayout, null, content))
}
export const previews: readonly PreviewEntry[] = [
  ...Object.entries(listPreviews).map(([state, props]) => ({
    id: `scr-065-${state}`,
    scr: 'SCR-065',
    title: `Quản lý đề thi — ${state}`,
    group: 'screen' as const,
    fullFrame: true,
    render: () => frame(h(ExamListView, props)),
  })),
  ...Object.keys(editorPreviews).map((mode) => ({
    id: `scr-066-${mode}`,
    scr: 'SCR-066',
    title: `Sửa đề thi — ${mode}`,
    group: 'screen' as const,
    fullFrame: true,
    render: () => frame(h(EditorPreview, { mode })),
  })),
  {
    id: 'scr-066-loading',
    scr: 'SCR-066',
    title: 'Sửa đề — đang tải',
    group: 'screen',
    fullFrame: true,
    render: () => frame(h('main', null, h('p', { role: 'status' }, 'Đang tải đề thi…'))),
  },
  {
    id: 'scr-066-error',
    scr: 'SCR-066',
    title: 'Sửa đề — lỗi tải',
    group: 'screen',
    fullFrame: true,
    render: () =>
      frame(
        h(
          'main',
          null,
          h('p', { role: 'alert' }, 'Không tải được đề thi.'),
          h(Button, { variant: 'outline', children: 'Thử tải đề lại' }),
        ),
      ),
  },
]
