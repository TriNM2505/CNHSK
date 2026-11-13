import { createElement, useState } from 'react'

import type { PreviewEntry, PreviewState } from '@/app/preview/types'
import { ApiError } from '@/shared/api'

import { BookDetailView } from './components/BookDetailView'
import { ReferenceListView } from './components/ReferenceListView'
import { ReferenceReportForm } from './components/ReferenceReportForm'
import { ReferenceAdminView } from './admin/components/ReferenceAdminView'
import { ReferenceEditorForm } from './admin/components/ReferenceEditorForm'
import { draftOf, newDraft } from './admin/referenceForm'
import { adminPage } from './admin/test-fixtures'
import type { AdminListParams, AdminReference, ReferenceAction } from './admin/types'
import { bookPage, bookReference, mediaPage, podcastReference } from './test-fixtures'
import type { ReferenceKind } from './types'

const error = new ApiError({
  status: 0,
  errorCode: 'NETWORK_ERROR',
  message: 'Không tải được tài liệu. Vui lòng thử lại.',
})

function ListPreview({ state, books = false }: { state: PreviewState; books?: boolean }) {
  const [kind, setKind] = useState<ReferenceKind>(books ? 'BOOK' : 'YOUTUBE_CHANNEL')
  const [hsk, setHsk] = useState('')
  const [tag, setTag] = useState('')
  const [reportId, setReportId] = useState<number>()
  const [sent, setSent] = useState(false)
  const result = books
    ? bookPage
    : kind === 'PODCAST'
      ? { ...mediaPage, items: [podcastReference] }
      : mediaPage
  return createElement(ReferenceListView, {
    kind,
    state,
    hsk,
    tag,
    error,
    canReport: true,
    result: state === 'empty' ? { ...result, items: [], total: 0 } : result,
    reportId,
    onReport: (id) => {
      setReportId(id)
      setSent(false)
    },
    onKindChange: setKind,
    onHskChange: setHsk,
    onTagChange: setTag,
    onApply: () => undefined,
    onPageChange: () => undefined,
    onRetry: () => undefined,
    report: createElement(ReferenceReportForm, {
      pending: false,
      sent,
      onSubmit: () => setSent(true),
      onCancel: () => setReportId(undefined),
    }),
  })
}

export const previews = [
  {
    group: 'screen',
    id: 'references-admin',
    scr: 'SCR-072',
    title: 'Tài nguyên tham khảo',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => createElement(AdminPreview, { state }),
  },
  {
    group: 'screen',
    id: 'references-media',
    scr: 'SCR-053',
    title: 'Tài liệu nghe nhìn',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => createElement(ListPreview, { state }),
  },
  {
    group: 'screen',
    id: 'references-books',
    scr: 'SCR-054',
    title: 'Danh mục sách tham khảo',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => createElement(ListPreview, { state, books: true }),
  },
  {
    group: 'screen',
    id: 'references-book-detail',
    scr: 'SCR-055',
    title: 'Chi tiết sách tham khảo',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) =>
      createElement(BookDetailView, {
        state,
        book: bookReference,
        error,
        canReport: false,
        onReport: () => undefined,
        onRetry: () => undefined,
      }),
  },
] satisfies readonly PreviewEntry[]

function AdminPreview({ state }: { state: PreviewState }) {
  const [filters, setFilters] = useState<AdminListParams>({})
  const [editing, setEditing] = useState<AdminReference | 'new'>()
  const [confirmation, setConfirmation] = useState<{
    item: AdminReference
    action: ReferenceAction
  }>()
  const [notice, setNotice] = useState<string>()
  return createElement(ReferenceAdminView, {
    state,
    data: state === 'empty' ? { ...adminPage, items: [], total: 0 } : adminPage,
    error,
    filters,
    busy: false,
    confirmation,
    notice,
    onFilter: setFilters,
    onPage: (page) => setFilters({ ...filters, page }),
    onRetry: () => undefined,
    onCreate: () => setEditing('new'),
    onEdit: (id) => setEditing(adminPage.items.find((item) => item.id === id)),
    onAction: (item, action) => setConfirmation({ item, action }),
    onCancel: () => setConfirmation(undefined),
    onConfirm: () => {
      setConfirmation(undefined)
      setNotice('Đã cập nhật dữ liệu xem trước.')
    },
    editor: editing
      ? createElement(ReferenceEditorForm, {
          key: editing === 'new' ? 'new' : editing.id,
          initial: editing === 'new' ? newDraft() : draftOf(editing),
          editing: editing !== 'new',
          pending: false,
          onCancel: () => setEditing(undefined),
          onSubmit: () => {
            setEditing(undefined)
            setNotice('Đã lưu dữ liệu xem trước.')
          },
        })
      : undefined,
  })
}
