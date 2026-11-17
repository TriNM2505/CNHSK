import { createElement, useState } from 'react'

import type { PreviewEntry, PreviewState } from '@/app/preview/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { AdminLayout, LearnerLayout } from '@/shared/layout'
import { previewLearner } from '@/shared/layout/Layout.preview'

import { BookDetailView } from './components/BookDetailView'
import { ReferenceListView } from './components/ReferenceListView'
import { ReferenceReportForm } from './components/ReferenceReportForm'
import { ReferenceAdminView } from './admin/components/ReferenceAdminView'
import { ReferenceEditorForm } from './admin/components/ReferenceEditorForm'
import { draftOf, newDraft } from './admin/referenceForm'
import { adminPage } from './admin/test-fixtures'
import type { AdminListParams, AdminReference, ReferenceAction } from './admin/types'
import { bookPage, bookReference, mediaPage, podcastReference } from './test-fixtures'
import type { PublicReferencePage, ReferenceKind } from './types'

const learnerAuth: AuthContextValue = {
  error: null,
  reloadCurrentUser: async () => undefined,
  setAuthenticatedUser: () => undefined,
  signOut: async () => undefined,
  status: 'authenticated',
  user: previewLearner,
}

const editorAuth: AuthContextValue = {
  error: null,
  reloadCurrentUser: async () => undefined,
  setAuthenticatedUser: () => undefined,
  signOut: async () => undefined,
  status: 'authenticated',
  user: {
    id: 204,
    displayName: 'Biên tập viên',
    email: 'editor@cnhsk.vn',
    emailVerified: true,
    roles: ['CONTENT_EDITOR'],
  },
}

function inLearnerLayout(element: ReturnType<typeof createElement>) {
  return createElement(
    AuthContext.Provider,
    { value: learnerAuth },
    createElement(LearnerLayout, null, element),
  )
}

function inEditorLayout(element: ReturnType<typeof createElement>) {
  return createElement(
    AuthContext.Provider,
    { value: editorAuth },
    createElement(AdminLayout, null, element),
  )
}

const previewMediaPage: PublicReferencePage = {
  ...mediaPage,
  total: 3,
  items: [
    ...mediaPage.items,
    {
      id: 511,
      kind: 'YOUTUBE_CHANNEL',
      title: 'Bản tin tiếng Trung chậm HSK 1–2',
      url: 'https://example.com/youtube-slow-chinese',
      thumbnailUrl: null,
      author: null,
      publisher: null,
      publishedYear: null,
      description:
        'Tổng hợp các bản tin ngắn đọc chậm kèm phụ đề chữ Hán và Pinyin cho người mới bắt đầu.',
      hskMin: 1,
      hskMax: 2,
      tags: ['luyện nghe', 'phụ đề'],
      isAffiliate: false,
    },
    {
      id: 512,
      kind: 'YOUTUBE_CHANNEL',
      title: 'Tình huống giao tiếp hàng ngày HSK 2–3',
      url: 'https://example.com/youtube-daily-hsk',
      thumbnailUrl: null,
      author: null,
      publisher: null,
      publishedYear: null,
      description:
        'Các mẫu câu phản xạ tự nhiên trong đời sống, mua sắm, trường học và công sở.',
      hskMin: 2,
      hskMax: 3,
      tags: ['giao tiếp', 'phản xạ'],
      isAffiliate: false,
    },
  ],
}

const previewBookPage: PublicReferencePage = {
  ...bookPage,
  total: 3,
  items: [
    ...bookPage.items,
    {
      id: 513,
      kind: 'BOOK',
      title: 'Giáo trình ngữ pháp HSK Sơ cấp (Cấp 1–3)',
      url: 'https://example.com/books/513',
      thumbnailUrl: null,
      author: 'Nhóm học liệu CNHSK',
      publisher: 'NXB Đại học Ngôn ngữ',
      publishedYear: 2025,
      description:
        'Hệ thống toàn bộ cấu trúc ngữ pháp trọng tâm HSK 1–3 kèm bài tập ứng dụng sát đề thi.',
      hskMin: 1,
      hskMax: 3,
      tags: ['ngữ pháp', 'giáo trình'],
      isAffiliate: false,
    },
    {
      id: 514,
      kind: 'BOOK',
      title: '300 chữ Hán nền tảng qua hình vẽ',
      url: 'https://example.com/books/514',
      thumbnailUrl: null,
      author: 'Trần Minh Châu',
      publisher: 'NXB Tổng hợp',
      publishedYear: 2024,
      description:
        'Phương pháp ghi nhớ bộ thủ, thứ tự nét viết và từ ghép thông dụng cho người mới học.',
      hskMin: 1,
      hskMax: 2,
      tags: ['chữ Hán', 'bộ thủ'],
      isAffiliate: true,
    },
  ],
}

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
    ? previewBookPage
    : kind === 'PODCAST'
      ? { ...mediaPage, items: [podcastReference] }
      : previewMediaPage
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
    fullFrame: true,
    group: 'screen',
    id: 'references-admin',
    scr: 'SCR-072',
    title: 'Tài nguyên tham khảo',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => inEditorLayout(createElement(AdminPreview, { state })),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'references-media',
    scr: 'SCR-053',
    title: 'Tài liệu nghe nhìn',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => inLearnerLayout(createElement(ListPreview, { state })),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'references-books',
    scr: 'SCR-054',
    title: 'Danh mục sách tham khảo',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => inLearnerLayout(createElement(ListPreview, { state, books: true })),
  },
  {
    fullFrame: true,
    group: 'screen',
    id: 'references-book-detail',
    scr: 'SCR-055',
    title: 'Chi tiết sách tham khảo',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) =>
      inLearnerLayout(
        createElement(BookDetailView, {
          state,
          book: bookReference,
          error,
          canReport: false,
          onReport: () => undefined,
          onRetry: () => undefined,
        }),
      ),
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
