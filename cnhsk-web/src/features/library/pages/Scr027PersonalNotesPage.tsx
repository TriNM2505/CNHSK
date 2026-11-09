import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { ApiError, toApiError, useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { ConfirmPanel, ErrorState, PageHeader } from '@/shared/ui'

import {
  createNote,
  deleteNote,
  getNote,
  listNotes,
  listTrash,
  restoreNote,
  updateNote,
} from '../api/notes'
import { NoteEditor } from '../components/NoteEditor'
import { isLibraryIdentifier } from '../api/identifiers'
import { PersonalNotesView } from '../components/PersonalNotesView'
import {
  newNoteDraft,
  noteDraft,
  noteRequest,
  notesPageSize,
  notesSearchParams,
  readNotesSearch,
  validateNoteDraft,
  validateNotesSearch,
  type NoteDraft,
  type NotesSearch,
} from '../personalNotes'

type MutationKind = 'save' | 'delete' | 'restore'
type ActionIssue = { kind: 'delete' | 'restore'; id: number; error: ApiError }
type OpenState = { id?: number; pending: boolean; error?: ApiError }

function PersonalNotesContent() {
  const [params, setParams] = useSearchParams()
  const paramsKey = params.toString()
  const currentParams = useRef(paramsKey)
  currentParams.current = paramsKey
  const search = readNotesSearch(params)
  const [filters, setFilters] = useState({ q: search.q, tag: search.tag })
  const [filterError, setFilterError] = useState<string>()
  const [draft, setDraft] = useState<NoteDraft>()
  const [validation, setValidation] = useState<string>()
  const [saveError, setSaveError] = useState<ApiError>()
  const [actionIssue, setActionIssue] = useState<ActionIssue>()
  const [open, setOpen] = useState<OpenState>({ pending: false })
  const [pending, setPending] = useState<{ kind: MutationKind; id?: number }>()
  const [confirmId, setConfirmId] = useState<number>()
  const [notice, setNotice] = useState('')
  const busy = useRef(false)
  const alive = useRef(true)
  const readController = useRef<AbortController>()

  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
      readController.current?.abort()
    }
  }, [])

  useEffect(() => {
    const next = readNotesSearch(new URLSearchParams(paramsKey))
    setFilters({ q: next.q, tag: next.tag })
    setFilterError(undefined)
  }, [paramsKey])

  const urlValidation = validateNotesSearch(search.q, search.tag)
  const notes = useApiQuery(
    (signal) => {
      if (search.tab !== 'active') return Promise.resolve(undefined)
      if (urlValidation)
        return Promise.reject(
          new ApiError({ status: 422, errorCode: 'VALIDATION_FAILED', message: urlValidation }),
        )
      return listNotes(
        {
          q: search.q || undefined,
          tag: search.tag || undefined,
          page: search.page,
          size: notesPageSize,
        },
        signal,
      )
    },
    [paramsKey],
    { isEmpty: (data) => data?.items.length === 0 },
  )
  const trash = useApiQuery(
    (signal) => (search.tab === 'trash' ? listTrash(signal) : Promise.resolve(undefined)),
    [search.tab],
    { isEmpty: (data) => data?.length === 0 },
  )

  const changeSearch = (next: NotesSearch) => {
    const nextParams = notesSearchParams(next)
    if (nextParams.toString() === paramsKey) {
      if (next.tab === 'trash') trash.reload()
      else notes.reload()
    } else setParams(nextParams)
  }

  const openNote = useCallback(async (id: number) => {
    if (busy.current) return
    if (!Number.isSafeInteger(id) || id < 1) return
    readController.current?.abort()
    const controller = new AbortController()
    readController.current = controller
    setOpen({ id, pending: true })
    setNotice('')
    try {
      const note = await getNote(id, controller.signal)
      if (!alive.current || controller.signal.aborted) return
      setDraft(noteDraft(note))
      setSaveError(undefined)
      setActionIssue(undefined)
      setValidation(undefined)
      setOpen({ pending: false })
    } catch (error) {
      if (alive.current && !controller.signal.aborted)
        setOpen({ id, pending: false, error: toApiError(error) })
    }
  }, [])

  const requestedNoteId = params.get('noteId')
  useEffect(() => {
    if (requestedNoteId === null) {
      setOpen((previous) => (previous.pending ? { pending: false } : previous))
      return
    }
    const id = /^\d+$/.test(requestedNoteId) ? Number(requestedNoteId) : 0
    if (!isLibraryIdentifier(id)) {
      setOpen({
        pending: false,
        error: new ApiError({
          status: 422,
          errorCode: 'VALIDATION_FAILED',
          message: 'ID ghi chú không hợp lệ.',
        }),
      })
      return
    }
    void openNote(id)
    return () => readController.current?.abort()
  }, [requestedNoteId, openNote])

  const mutate = async (kind: MutationKind, id?: number) => {
    if (busy.current) return
    if (kind === 'save') {
      if (!draft || open.pending) return
      const invalid = validateNoteDraft(draft)
      setValidation(invalid)
      if (invalid) return
    }
    busy.current = true
    if (kind === 'delete') {
      readController.current?.abort()
      setOpen({ pending: false })
    }
    setPending({ kind, id })
    setNotice('')
    if (kind === 'save') setSaveError(undefined)
    else setActionIssue(undefined)
    try {
      if (kind === 'save' && draft) {
        const body = noteRequest(draft)
        const saved =
          draft.id === undefined ? await createNote(body) : await updateNote(draft.id, body)
        if (!alive.current) return
        setDraft(noteDraft(saved))
        setNotice('Đã lưu ghi chú.')
      } else if (kind === 'delete' && id !== undefined) {
        await deleteNote(id)
        if (!alive.current) return
        if (draft?.id === id) setDraft(undefined)
        setConfirmId(undefined)
        setNotice('Đã chuyển ghi chú vào thùng rác.')
      } else if (kind === 'restore' && id !== undefined) {
        await restoreNote(id)
        if (!alive.current) return
        setNotice('Đã khôi phục ghi chú.')
      }
      if (!alive.current) return
      notes.reload()
      trash.reload()
      if (
        currentParams.current === paramsKey &&
        search.page > 0 &&
        (kind === 'delete'
          ? notes.data?.items.length === 1
          : kind === 'restore' && trash.data?.length === search.page * notesPageSize + 1)
      ) {
        changeSearch({ ...search, page: search.page - 1 })
      }
    } catch (error) {
      if (!alive.current) return
      if (kind === 'save') setSaveError(toApiError(error))
      else if (id !== undefined) {
        setConfirmId(undefined)
        setActionIssue({ kind, id, error: toApiError(error) })
      }
    } finally {
      busy.current = false
      if (alive.current) setPending(undefined)
    }
  }

  const current = search.tab === 'trash' ? trash : notes
  return (
    <PersonalNotesView
      search={search}
      filters={filters}
      filterError={filterError ?? (search.tab === 'active' ? urlValidation : undefined)}
      state={current.state}
      error={current.error}
      items={
        search.tab === 'trash'
          ? (trash.data ?? []).slice(search.page * notesPageSize, (search.page + 1) * notesPageSize)
          : (notes.data?.items ?? [])
      }
      total={search.tab === 'trash' ? trash.data?.length : notes.data?.total}
      busy={pending !== undefined}
      restoringId={pending?.kind === 'restore' ? pending.id : undefined}
      actionError={actionIssue?.error}
      opening={open.pending}
      openError={open.error}
      notice={notice}
      onFiltersChange={(next) => {
        setFilters(next)
        setFilterError(undefined)
      }}
      onSearch={() => {
        const error = validateNotesSearch(filters.q, filters.tag)
        setFilterError(error)
        if (!error)
          changeSearch({
            ...search,
            q: filters.q.trim().normalize('NFC'),
            tag: filters.tag,
            page: 0,
          })
      }}
      onClear={() => {
        setFilters({ q: '', tag: '' })
        setFilterError(undefined)
        changeSearch({ ...search, q: '', tag: '', page: 0 })
      }}
      onTabChange={(tab) => changeSearch({ ...search, tab, page: 0 })}
      onPageChange={(page) => changeSearch({ ...search, page })}
      onRetry={current.reload}
      onActionRetry={() => {
        if (!actionIssue) return
        if (actionIssue.kind === 'delete') setConfirmId(actionIssue.id)
        else void mutate('restore', actionIssue.id)
      }}
      onOpenRetry={() => {
        if (open.id !== undefined) void openNote(open.id)
      }}
      onNew={() => {
        if (busy.current) return
        readController.current?.abort()
        setOpen({ pending: false })
        setDraft(newNoteDraft())
        setValidation(undefined)
        setSaveError(undefined)
        setNotice('')
      }}
      onOpenNote={(id) => {
        void openNote(id)
      }}
      onDelete={(id) => {
        setActionIssue(undefined)
        setConfirmId(id)
      }}
      onRestore={(id) => {
        void mutate('restore', id)
      }}
      editor={
        draft ? (
          <NoteEditor
            draft={draft}
            pending={pending !== undefined}
            opening={open.pending}
            validation={validation}
            error={saveError}
            onChange={(next) => {
              setDraft(next)
              setValidation(undefined)
            }}
            onSubmit={() => {
              void mutate('save')
            }}
            onOpenNote={(id) => {
              void openNote(id)
            }}
          />
        ) : undefined
      }
      confirmation={
        <ConfirmPanel
          open={confirmId !== undefined}
          title="Xoá ghi chú?"
          message="Ghi chú sẽ được chuyển vào thùng rác và có thể khôi phục trong 30 ngày."
          danger
          confirmLabel="Xác nhận xoá"
          pending={pending?.kind === 'delete'}
          onCancel={() => setConfirmId(undefined)}
          onConfirm={() => {
            if (confirmId !== undefined) void mutate('delete', confirmId)
          }}
        />
      }
    />
  )
}

export default function Scr027PersonalNotesPage() {
  const auth = useCurrentUser()
  if (auth.status !== 'authenticated' || !auth.user?.roles.includes('LEARNER')) {
    return (
      <div className="space-y-4">
        <PageHeader title="Sổ tay cá nhân" />
        {auth.status === 'loading' ? (
          <p role="status">Đang kiểm tra quyền truy cập…</p>
        ) : (
          <ErrorState
            message={
              auth.status === 'error'
                ? 'Chưa kiểm tra được phiên đăng nhập.'
                : 'Cần đăng nhập với quyền học viên để xem sổ tay.'
            }
            onRetry={
              auth.status === 'error'
                ? () => {
                    void auth.reloadCurrentUser()
                  }
                : undefined
            }
          />
        )}
      </div>
    )
  }
  return <PersonalNotesContent key={auth.user.id} />
}
