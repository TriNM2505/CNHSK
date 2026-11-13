import { createElement, useState } from 'react'

import type { PreviewEntry, PreviewState } from '@/app/preview/types'
import { ApiError } from '@/shared/api'

import { BookDetailView } from './components/BookDetailView'
import { ReferenceListView } from './components/ReferenceListView'
import { ReferenceReportForm } from './components/ReferenceReportForm'
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
