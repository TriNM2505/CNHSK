import { useState } from 'react'

import type { PreviewState } from '@/app/preview/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { LearnerLayout } from '@/shared/layout'
import { ConfirmPanel } from '@/shared/ui'

import {
  draftTags,
  newNoteDraft,
  noteDraft,
  notesPageSize,
  validateNoteDraft,
  validateNotesSearch,
  type NoteDraft,
  type NotesSearch,
} from '../personalNotes'
import type { Note, TrashNote } from '../types'
import { NoteEditor } from './NoteEditor'
import { PersonalNotesView } from './PersonalNotesView'

// Original review fixtures; all mutations below stay in component state.
const samples: Note[] = [
  {
    id: 701,
    title: 'Nhớ từ qua một buổi sáng',
    content:
      '早上 — buổi sáng. 早上我喝水。 Buổi sáng tôi uống nước.\nĐọc câu rồi tự thay hành động để nhớ từ lâu hơn.',
    tags: ['hsk1', 'mỗi ngày'],
    lexemeId: null,
    sourceType: 'MANUAL',
    createdAt: '2026-11-16T02:00:00Z',
    updatedAt: '2026-11-17T02:00:00Z',
  },
  {
    id: 702,
    title: 'Phân biệt 今天 và 明天',
    content:
      '今天 — hôm nay; 明天 — ngày mai.\n今天我看书。 Hôm nay tôi đọc sách.\n明天我去学校。 Ngày mai tôi đến trường.',
    tags: ['thời gian', 'hsk1'],
    lexemeId: 201,
    sourceType: 'DICTIONARY',
    createdAt: '2026-11-15T03:30:00Z',
    updatedAt: '2026-11-17T01:30:00Z',
  },
  {
    id: 703,
    title: 'Một câu về gia đình',
    content:
      '我和家人一起吃饭。 Tôi ăn cơm cùng gia đình.\nGhi lại một hoạt động mình làm cùng người thân mỗi ngày.',
    tags: ['gia đình', 'luyện câu'],
    lexemeId: null,
    sourceType: 'MANUAL',
    createdAt: '2026-11-14T08:00:00Z',
    updatedAt: '2026-11-16T08:00:00Z',
  },
  {
    id: 704,
    title: 'Cách dùng 是……的',
    content:
      '我是昨天来的。 Tôi đã đến hôm qua.\nCấu trúc nhấn mạnh thời gian hoặc cách thức của hành động đã xảy ra.',
    tags: ['ngữ pháp', 'hsk2'],
    lexemeId: null,
    sourceType: 'MANUAL',
    createdAt: '2026-11-14T07:00:00Z',
    updatedAt: '2026-11-16T07:00:00Z',
  },
  {
    id: 705,
    title: 'Ghi nhớ bằng hình dung',
    content:
      '山 — núi; 水 — nước.\nHình dung một dòng sông đi qua chân núi rồi đọc hai từ thành tiếng.',
    tags: ['chữ Hán', 'mẹo nhớ'],
    lexemeId: null,
    sourceType: 'MANUAL',
    createdAt: '2026-11-13T04:00:00Z',
    updatedAt: '2026-11-15T04:00:00Z',
  },
  {
    id: 706,
    title: 'Mục tiêu buổi học tiếp theo',
    content:
      'Ôn lại ba câu mẫu, đọc pinyin chậm và tự viết một câu có 朋友.\n我和朋友学习中文。 Tôi học tiếng Trung cùng bạn.',
    tags: ['tự học', 'kế hoạch'],
    lexemeId: null,
    sourceType: 'MANUAL',
    createdAt: '2026-11-12T06:00:00Z',
    updatedAt: '2026-11-14T06:00:00Z',
  },
]

const discarded: TrashNote = {
  id: 707,
  title: 'Câu cần viết lại',
  snippet: '下午我看书。 Buổi chiều tôi đọc sách.',
  tags: ['luyện câu'],
  sourceType: 'MANUAL',
  lexemeId: null,
  updatedAt: '2026-11-15T04:00:00Z',
  deletedAt: '2026-11-16T04:00:00Z',
  restorableUntil: '2026-12-16T04:00:00Z',
}

interface NotesPreviewProps {
  state: PreviewState
  trash?: boolean
  editing?: boolean
  creating?: boolean
}

function NotesSampleContent({
  state,
  trash = false,
  editing = false,
  creating = false,
}: NotesPreviewProps) {
  const [notes, setNotes] = useState(state === 'empty' ? [] : samples)
  const [removed, setRemoved] = useState(state === 'empty' ? [] : [discarded])
  const [search, setSearch] = useState<NotesSearch>({
    q: '',
    tag: '',
    page: 0,
    tab: trash ? 'trash' : 'active',
  })
  const [filters, setFilters] = useState({ q: '', tag: '' })
  const [filterError, setFilterError] = useState<string>()
  const [validation, setValidation] = useState<string>()
  const [draft, setDraft] = useState<NoteDraft | undefined>(
    creating ? newNoteDraft() : editing ? noteDraft(samples[0]!) : undefined,
  )
  const [confirmId, setConfirmId] = useState<number>()
  const [notice, setNotice] = useState('')
  const matches = notes.filter(
    (note) =>
      (!search.q ||
        `${note.title ?? ''} ${note.content}`
          .toLocaleLowerCase()
          .includes(search.q.toLocaleLowerCase())) &&
      (!search.tag || note.tags.includes(search.tag)),
  )
  const visible =
    search.tab === 'trash'
      ? removed
      : matches.map(({ content, ...note }) => ({ ...note, snippet: content.slice(0, 200) }))
  const open = (id: number) => {
    const note = notes.find((item) => item.id === id)
    if (note) {
      setDraft(noteDraft(note))
      setValidation(undefined)
      setNotice('')
    }
  }
  const save = () => {
    if (!draft) return
    const error = validateNoteDraft(draft)
    setValidation(error)
    if (error) return
    const saved: Note = {
      id:
        draft.id ?? Math.max(700, ...notes.map(({ id }) => id), ...removed.map(({ id }) => id)) + 1,
      title: draft.title || null,
      content: draft.content,
      tags: draftTags(draft),
      lexemeId: draft.lexemeId,
      sourceType: draft.sourceType,
      createdAt: notes.find(({ id }) => id === draft.id)?.createdAt ?? '2026-11-17T08:00:00Z',
      updatedAt: '2026-11-17T08:00:00Z',
    }
    setNotes((current) => [saved, ...current.filter(({ id }) => id !== saved.id)])
    setDraft(noteDraft(saved))
    setNotice('Đã lưu ghi chú mẫu. Dữ liệu chỉ thay đổi trong bản xem trước.')
  }
  return (
    <PersonalNotesView
      search={search}
      filters={filters}
      filterError={filterError}
      state={state === 'error' || state === 'loading' ? state : visible.length ? 'ready' : 'empty'}
      error={
        new ApiError({
          status: 503,
          errorCode: 'UNAVAILABLE',
          message: 'Chưa tải được ghi chú. Vui lòng thử lại.',
        })
      }
      items={visible.slice(search.page * notesPageSize, (search.page + 1) * notesPageSize)}
      total={visible.length}
      busy={false}
      opening={false}
      notice={notice}
      onFiltersChange={(next) => {
        setFilters(next)
        setFilterError(undefined)
      }}
      onSearch={() => {
        const error = validateNotesSearch(filters.q, filters.tag)
        setFilterError(error)
        if (!error)
          setSearch((current) => ({
            ...current,
            q: filters.q.trim().normalize('NFC'),
            tag: filters.tag,
            page: 0,
          }))
      }}
      onClear={() => {
        setFilters({ q: '', tag: '' })
        setFilterError(undefined)
        setSearch((current) => ({ ...current, q: '', tag: '', page: 0 }))
      }}
      onTabChange={(tab) => {
        setSearch((current) => ({ ...current, tab, page: 0 }))
        setNotice('')
      }}
      onPageChange={(page) => setSearch((current) => ({ ...current, page }))}
      onRetry={() => undefined}
      onActionRetry={() => undefined}
      onOpenRetry={() => undefined}
      onNew={() => {
        setDraft(newNoteDraft())
        setValidation(undefined)
        setNotice('')
      }}
      onOpenNote={open}
      onDelete={setConfirmId}
      onRestore={(id) => {
        const item = removed.find((note) => note.id === id)
        if (!item) return
        setNotes((current) => [
          {
            id: item.id,
            title: item.title,
            content: item.snippet,
            tags: item.tags,
            lexemeId: item.lexemeId,
            sourceType: item.sourceType,
            createdAt: item.updatedAt,
            updatedAt: item.updatedAt,
          },
          ...current,
        ])
        setRemoved((current) => current.filter((note) => note.id !== id))
        setNotice('Đã khôi phục ghi chú mẫu.')
      }}
      editor={
        draft ? (
          <NoteEditor
            draft={draft}
            pending={false}
            opening={false}
            validation={validation}
            onChange={(next) => {
              setDraft(next)
              setValidation(undefined)
            }}
            onSubmit={save}
            onOpenNote={open}
          />
        ) : undefined
      }
      confirmation={
        <ConfirmPanel
          danger
          open={confirmId !== undefined}
          title="Xoá ghi chú?"
          message="Ghi chú mẫu sẽ được chuyển vào thùng rác của bản xem trước."
          confirmLabel="Xác nhận xoá"
          onCancel={() => setConfirmId(undefined)}
          onConfirm={() => {
            const note = notes.find(({ id }) => id === confirmId)
            if (note) {
              setRemoved((current) => [
                {
                  ...note,
                  snippet: note.content,
                  deletedAt: '2026-11-17T08:00:00Z',
                  restorableUntil: '2026-12-17T08:00:00Z',
                },
                ...current,
              ])
              setNotes((current) => current.filter(({ id }) => id !== note.id))
              if (draft?.id === note.id) setDraft(undefined)
              setNotice('Đã chuyển ghi chú mẫu vào thùng rác.')
            }
            setConfirmId(undefined)
          }}
        />
      }
    />
  )
}

export function PersonalNotesScreenPreview(props: NotesPreviewProps) {
  const auth: AuthContextValue = {
    error: null,
    reloadCurrentUser: async () => undefined,
    setAuthenticatedUser: () => undefined,
    signOut: async () => undefined,
    status: 'authenticated',
    user: {
      id: 101,
      displayName: 'Bạn học CNHSK',
      email: '',
      emailVerified: true,
      hskLevel: 1,
      roles: ['LEARNER'],
    },
  }
  return (
    <AuthContext.Provider value={auth}>
      <LearnerLayout>
        <NotesSampleContent {...props} />
      </LearnerLayout>
    </AuthContext.Provider>
  )
}
