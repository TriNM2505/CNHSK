import { createElement as h, useState } from 'react'
import type { PreviewEntry, PreviewState } from '@/app/preview/types'
import { Button, Card, ErrorState, PageHeader, Select, Skeleton } from '@/shared/ui'
import { ContestListView } from './components/ContestListView'
import { ContestEditorForm } from './components/ContestEditorForm'
import { ContestEditorView } from './components/ContestEditorView'
import { ContestParticipantsPanel } from './components/ContestParticipantsPanel'
import { ContestWinnersPanel } from './components/ContestWinnersPanel'
import { editReason } from './contestActions'
import {
  contestFixture,
  endedFixture,
  examFixture,
  examPageFixture,
  participantFixtures,
  publishedFixture,
  winnerFixtures,
} from './test-fixtures'
import type { AdminContest, ContestStatus, ExamListParams } from './types'

function ListPreview({ state }: { state: PreviewState }) {
  const [status, setStatus] = useState<ContestStatus | ''>('')
  const [notice, setNotice] = useState('')
  const items = [
    contestFixture,
    endedFixture,
    publishedFixture,
    { ...contestFixture, id: 2110, name: 'Cuộc thi đã huỷ', status: 'CANCELLED' as const },
  ]
    .map((item, index) => ({ ...item, id: 2109 + index }))
    .filter((item) => !status || item.status === status)
  return h(
    'div',
    null,
    notice && h('p', { role: 'status' }, notice),
    h(ContestListView, {
      state,
      status,
      data: {
        items: state === 'empty' ? [] : items,
        page: 0,
        size: 20,
        totalElements: state === 'empty' ? 0 : items.length,
      },
      error: 'Không tải được cuộc thi. Vui lòng thử lại.',
      onFilter: setStatus,
      onPage: () => undefined,
      onRetry: () => undefined,
      onAction: () => setNotice('Đây là dữ liệu xem trước; không thay đổi cuộc thi thật.'),
    }),
  )
}
function EditorPreview({ state }: { state: PreviewState }) {
  const [mode, setMode] = useState('new')
  const [revision, setRevision] = useState(0)
  const [saved, setSaved] = useState(false)
  const [filters, setFilters] = useState<ExamListParams>({ page: 0 })
  const [rows, setRows] = useState(participantFixtures)
  const contest: AdminContest | null =
    mode === 'new'
      ? null
      : mode === 'ended'
        ? endedFixture
        : mode === 'published'
          ? publishedFixture
          : mode === 'locked'
            ? { ...contestFixture, status: 'PUBLISHED', state: 'ONGOING' }
            : contestFixture
  if (state === 'loading')
    return h(
      'main',
      { className: 'mx-auto w-full min-w-0 max-w-reading space-y-6 px-4 py-6 sm:px-6' },
      h(PageHeader, { title: 'Chi tiết cuộc thi' }),
      h('p', { role: 'status' }, 'Đang tải cuộc thi…'),
      h(Skeleton, { variant: 'card' }),
    )
  if (state === 'error')
    return h(
      'main',
      { className: 'mx-auto w-full min-w-0 max-w-reading space-y-6 px-4 py-6 sm:px-6' },
      h(PageHeader, { title: 'Chi tiết cuộc thi' }),
      h(ErrorState, { message: 'Không tải được cuộc thi.', onRetry: () => undefined }),
    )
  return h(
    ContestEditorView,
    {
      listPath: '/content/contests',
      title: contest ? 'Chi tiết cuộc thi' : 'Tạo cuộc thi',
      subtitle: contest?.name,
    },
    h(
      'label',
      { htmlFor: 'preview-mode', className: 'block font-semibold' },
      'Tình huống xem trước',
    ),
    h(
      Select,
      {
        id: 'preview-mode',
        value: mode,
        onChange: (e) => {
          setMode(e.target.value)
          setRevision((v) => v + 1)
          setSaved(false)
        },
      },
      ...[
        ['new', 'Tạo mới'],
        ['draft', 'Bản nháp'],
        ['archived', 'Đề đã lưu trữ'],
        ['locked', 'Đang thi — khoá sửa'],
        ['ended', 'Kết thúc — xét thí sinh'],
        ['published', 'Đã công bố — liên hệ'],
      ].map(([value, label]) => h('option', { value, key: value }, label)),
    ),
    saved && h('p', { role: 'status' }, 'Đã lưu dữ liệu xem trước.'),
    h(ContestEditorForm, {
      key: revision,
      contest,
      lockedReason: contest ? editReason(contest) : undefined,
      examState: state === 'empty' ? 'empty' : 'ready',
      exams:
        state === 'empty' ? { ...examPageFixture, items: [], totalElements: 0 } : examPageFixture,
      examFilters: filters,
      onExamFilter: setFilters,
      onExamRetry: () => undefined,
      selectedExam: mode === 'archived' ? { ...examFixture, status: 'ARCHIVED' } : examFixture,
      onSelectedExam: () => undefined,
      onDirty: () => setSaved(false),
      onSubmit: () => setSaved(true),
    }),
    h(
      Card,
      null,
      h(Button, { disabled: mode !== 'ended', children: 'Công bố kết quả' }),
      mode !== 'ended' &&
        h('p', null, 'Chỉ công bố sau giờ kết thúc và trước lần công bố đầu tiên.'),
    ),
    contest?.state === 'ENDED' &&
      h(ContestParticipantsPanel, {
        state: state === 'empty' ? 'empty' : 'ready',
        participants: rows,
        published: !!contest.rankingPublishedAt,
        onReload: () => undefined,
        onConflict: () => undefined,
        onDecision: async (userId, input) => {
          setRows((current) =>
            current.map((row) =>
              row.userId === userId ? { ...row, suspicious: input.excluded } : row,
            ),
          )
        },
      }),
    contest?.rankingPublishedAt &&
      h(ContestWinnersPanel, {
        key: mode,
        onReveal: async () => (state === 'empty' ? [] : winnerFixtures),
        onConflict: () => undefined,
      }),
  )
}
export const previews = [
  {
    group: 'screen',
    id: 'contest-admin-list',
    scr: 'SCR-070',
    title: 'Quản lý cuộc thi',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => h(ListPreview, { state }),
  },
  {
    group: 'screen',
    id: 'scr-070',
    scr: 'SCR-070',
    title: 'Quản lý cuộc thi (SCR-070)',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => h(ListPreview, { state }),
  },
  {
    group: 'screen',
    id: 'contest-admin-editor',
    scr: 'SCR-071',
    title: 'Tạo / sửa và tổ chức cuộc thi',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => h(EditorPreview, { state }),
  },
  {
    group: 'screen',
    id: 'scr-071',
    scr: 'SCR-071',
    title: 'Tạo / sửa và tổ chức cuộc thi (SCR-071)',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => h(EditorPreview, { state }),
  },
] satisfies readonly PreviewEntry[]
