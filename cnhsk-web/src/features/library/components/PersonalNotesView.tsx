import { BookOpenText, CheckCircle2, FileText, NotebookPen, Plus, RotateCcw, Search, Trash2 } from 'lucide-react'

import type { ReactNode } from 'react'

import type { ApiError } from '@/shared/api'
import {
  Button,
  EmptyState,
  ErrorState,
  HanziText,
  Input,
  Pagination,
  Skeleton,
  Tabs,
  type PageStateValue,
} from '@/shared/ui'
import './LibraryView.css'

import {
  duplicateNoteId,
  noteDate,
  noteErrorMessage,
  notesPageSize,
  snippetParts,
  type NotesSearch,
} from '../personalNotes'
import type { NoteListItem, TrashNote } from '../types'
import './PersonalNotesView.css'

function NoteText({ text }: { text: string }) {
  return text.split(/([\p{Script=Han}\u3000-\u303f\uff00-\uffef]+)/u).map((part, index) =>
    /[\p{Script=Han}\u3000-\u303f\uff00-\uffef]/u.test(part) ? (
      <HanziText className="notes-hanzi" key={index} size="sm">
        {part}
      </HanziText>
    ) : (
      part
    ),
  )
}

export interface PersonalNotesViewProps {
  search: NotesSearch
  filters: { q: string; tag: string }
  filterError?: string
  state: PageStateValue
  error?: ApiError
  items: readonly (NoteListItem | TrashNote)[]
  total?: number
  busy: boolean
  restoringId?: number
  actionError?: ApiError
  opening: boolean
  openError?: ApiError
  notice?: string
  editor?: ReactNode
  confirmation?: ReactNode
  onFiltersChange: (filters: { q: string; tag: string }) => void
  onSearch: () => void
  onClear: () => void
  onTabChange: (tab: NotesSearch['tab']) => void
  onPageChange: (page: number) => void
  onRetry: () => void
  onActionRetry: () => void
  onOpenRetry: () => void
  onNew: () => void
  onOpenNote: (id: number) => void
  onDelete: (id: number) => void
  onRestore: (id: number) => void
}

export function PersonalNotesView(props: PersonalNotesViewProps) {
  const { search, filters, state, items } = props
  const trash = search.tab === 'trash'
  const duplicateId = duplicateNoteId(props.actionError)
  const list = (
    <section
      aria-label={trash ? 'Ghi chú đã xoá' : 'Danh sách ghi chú'}
      className="notes-enter min-w-0 space-y-2"
      key={search.tab}
    >
      {!trash ? (
        <form
          className="space-y-2"
          noValidate
          onSubmit={(event) => {
            event.preventDefault()
            props.onSearch()
          }}
        >
          <label className="sr-only" htmlFor="notes-query">
            Từ khoá tìm kiếm
          </label>
          <div className="relative">
            <Search aria-hidden="true" className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-secondary" />
            <Input
              className="min-w-0 pl-9"
              id="notes-query"
              onChange={(event) => props.onFiltersChange({ ...filters, q: event.target.value })}
              placeholder="Tìm trong sổ tay…"
              type="search"
              value={filters.q}
            />
          </div>
          <label className="sr-only" htmlFor="notes-tag-filter">
            Lọc theo thẻ
          </label>
          <Input
            id="notes-tag-filter"
            onChange={(event) => props.onFiltersChange({ ...filters, tag: event.target.value })}
            placeholder="Lọc theo thẻ (nhập đúng tên thẻ)"
            value={filters.tag}
          />
          {props.filterError ? (
            <p className="text-body-sm text-danger" role="alert">
              {props.filterError}
            </p>
          ) : null}
          <div className="flex gap-2">
            <Button className="flex-1" size="sm" type="submit" variant="outline">
              Tìm ghi chú
            </Button>
            <Button onClick={props.onClear} size="sm" variant="ghost">
              Xoá bộ lọc
            </Button>
          </div>
        </form>
      ) : (
        <p className="px-2 text-body-sm text-ink-secondary">
          Có thể khôi phục trong 30 ngày. Ghi chú quá hạn được ẩn khỏi thùng rác.
        </p>
      )}
      {state === 'loading' ? (
        <div aria-label={trash ? 'Đang tải thùng rác' : 'Đang tải ghi chú'} className="space-y-2 pt-2">
          <Skeleton className="h-10" />
          <Skeleton className="h-10" />
          <Skeleton className="h-10" />
        </div>
      ) : null}
      {state === 'error' ? (
        <ErrorState
          message={props.error ? noteErrorMessage(props.error) : 'Vui lòng thử lại.'}
          onRetry={props.onRetry}
          requestId={props.error?.requestId}
          title={trash ? 'Chưa tải được thùng rác' : 'Chưa tải được ghi chú'}
        />
      ) : null}
      {state === 'empty' ? (
        <EmptyState
          action={
            !trash ? (
              <Button onClick={search.q || search.tag ? props.onClear : props.onNew} size="sm" variant="outline">
                {search.q || search.tag ? 'Xoá bộ lọc' : 'Tạo ghi chú'}
              </Button>
            ) : undefined
          }
          description={
            trash ? 'Ghi chú đã xoá còn hạn khôi phục sẽ xuất hiện ở đây.' : 'Tạo ghi chú mới hoặc xoá bộ lọc để xem lại.'
          }
          title={trash ? 'Thùng rác trống' : search.q || search.tag ? 'Không tìm thấy ghi chú' : 'Chưa có ghi chú'}
        />
      ) : null}
      {state === 'ready' ? (
        <>
          {props.total !== undefined ? (
            <p className="px-2 pt-2 text-caption uppercase tracking-wide text-ink-secondary">
              {trash ? 'Trong thùng rác' : 'Trang'} · {props.total} ghi chú
            </p>
          ) : null}
          <ul className="min-w-0 space-y-0.5">
            {items.map((note) => (
              <li
                className="group relative min-w-0 rounded-md px-2 py-2 transition-colors hover:bg-brand-50 focus-within:bg-brand-50"
                key={note.id}
              >
                <div className="flex min-w-0 items-start gap-2">
                  <FileText aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-ink-secondary" />
                  <div className="min-w-0 flex-1 space-y-1">
                    <h2 className="truncate text-label text-ink">
                      <NoteText text={note.title || 'Ghi chú không có tiêu đề'} />
                    </h2>
                    <p className="line-clamp-2 whitespace-pre-wrap break-words text-body-sm text-ink-secondary">
                      {snippetParts(note.snippet, trash ? '' : search.q).map((part, index) =>
                        part.match ? (
                          <mark className="rounded-sm bg-warning-bg text-ink" key={index}>
                            <NoteText text={part.text} />
                          </mark>
                        ) : (
                          <NoteText key={index} text={part.text} />
                        ),
                      )}
                    </p>
                    {note.tags.length ? (
                      <ul aria-label="Thẻ" className="flex flex-wrap gap-1">
                        {note.tags.map((tag, index) => (
                          <li className="rounded-sm bg-brand-50 px-1.5 text-caption text-brand-700" key={index}>
                            <NoteText text={tag} />
                          </li>
                        ))}
                      </ul>
                    ) : null}
                    <p className="text-caption text-ink-secondary">
                      Cập nhật: <time dateTime={note.updatedAt}>{noteDate(note.updatedAt)}</time>
                    </p>
                    {trash && 'restorableUntil' in note ? (
                      <>
                        <p className="text-caption text-ink-secondary">
                          Đã xoá: <time dateTime={note.deletedAt}>{noteDate(note.deletedAt)}</time>
                        </p>
                        <p className="text-caption text-ink-secondary">
                          Khôi phục đến: <time dateTime={note.restorableUntil}>{noteDate(note.restorableUntil)}</time>
                        </p>
                        <Button
                          disabled={props.busy}
                          loading={props.restoringId === note.id}
                          onClick={() => props.onRestore(note.id)}
                          size="sm"
                          variant="outline"
                        >
                          <RotateCcw aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
                          Khôi phục
                        </Button>
                      </>
                    ) : null}
                  </div>
                  {!trash ? (
                    <button
                      aria-label="Xoá ghi chú"
                      className="relative z-dropdown flex size-8 shrink-0 items-center justify-center rounded-sm text-ink-secondary opacity-0 transition-opacity hover:bg-brand-100 hover:text-danger focus-visible:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus group-hover:opacity-100 disabled:opacity-40"
                      disabled={props.busy}
                      onClick={() => props.onDelete(note.id)}
                      title="Xoá ghi chú"
                      type="button"
                    >
                      <Trash2 aria-hidden="true" className="size-4" />
                    </button>
                  ) : null}
                </div>
                {!trash ? (
                  // The whole row opens the note; the delete button sits above this layer.
                  <button
                    className="absolute inset-0 rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-focus"
                    disabled={props.busy}
                    onClick={() => props.onOpenNote(note.id)}
                    type="button"
                  >
                    <span className="sr-only">Mở ghi chú</span>
                  </button>
                ) : null}
              </li>
            ))}
          </ul>
          {props.total !== undefined ? (
            <Pagination
              onChange={(page) => props.onPageChange(page - 1)}
              page={search.page + 1}
              size={notesPageSize}
              total={props.total}
            />
          ) : null}
        </>
      ) : null}
    </section>
  )
  return (
    <div className="library-screen personal-notes mx-auto w-full min-w-0 max-w-content px-4 py-6 pb-24 sm:px-6 lg:px-8">
      <div className="notes-workspace grid min-h-[75dvh] min-w-0 overflow-hidden rounded-lg border border-line bg-surface shadow-sm lg:grid-cols-[19rem_minmax(0,1fr)]">
        <aside
          aria-label="Sổ tay cá nhân"
          className="notes-sidebar min-w-0 space-y-3 border-b border-line bg-canvas p-3 lg:border-b-0 lg:border-r"
        >
          <div className="flex items-center justify-between gap-2 px-1">
            <h1 className="inline-flex min-w-0 items-center gap-2 text-h3 text-ink">
              <NotebookPen aria-hidden="true" className="size-5 shrink-0 text-brand-600" />
              <span className="truncate">Sổ tay cá nhân</span>
            </h1>
            <Button
              aria-label="Tạo ghi chú"
              className="min-h-touch min-w-touch"
              disabled={props.busy}
              onClick={props.onNew}
              size="sm"
              title="Tạo ghi chú"
              variant="ghost"
            >
              <Plus aria-hidden="true" className="size-5" />
            </Button>
          </div>
          <Tabs
            items={[
              {
                id: 'active',
                label: (
                  <span className="inline-flex items-center gap-2">
                    <BookOpenText aria-hidden="true" className="size-4" />
                    Ghi chú
                  </span>
                ),
                panel: list,
              },
              {
                id: 'trash',
                label: (
                  <span className="inline-flex items-center gap-2">
                    <Trash2 aria-hidden="true" className="size-4" />
                    Thùng rác
                  </span>
                ),
                panel: list,
              },
            ]}
            label="Sổ tay"
            onChange={(tab) => props.onTabChange(tab === 'trash' ? 'trash' : 'active')}
            value={search.tab}
          />
        </aside>
        <section
          aria-label="Chỉnh sửa ghi chú"
          className="notes-editor-panel min-w-0 space-y-4 px-5 py-6 sm:px-10 sm:py-10 lg:px-16"
        >
          {props.notice ? (
            <p
              className="notes-enter mx-auto flex max-w-reading items-start gap-2 rounded-md bg-success-bg p-3 text-body text-success"
              role="status"
            >
              <CheckCircle2 aria-hidden="true" className="mt-1 size-4 shrink-0" />
              {props.notice}
            </p>
          ) : null}
          {props.actionError ? (
            <div className="mx-auto max-w-reading space-y-3" role="alert">
              <ErrorState
                message={noteErrorMessage(props.actionError)}
                onRetry={props.onActionRetry}
                requestId={props.actionError.requestId}
                title="Chưa hoàn tất thao tác"
              />
              {duplicateId ? (
                <Button
                  disabled={props.busy || props.opening}
                  onClick={() => props.onOpenNote(duplicateId)}
                  variant="outline"
                >
                  Mở ghi chú đã có
                </Button>
              ) : null}
            </div>
          ) : null}
          {props.opening ? (
            <p className="mx-auto max-w-reading text-body text-ink-secondary" role="status">
              Đang mở ghi chú…
            </p>
          ) : null}
          {props.openError ? (
            <div className="mx-auto max-w-reading">
              <ErrorState
                message={noteErrorMessage(props.openError)}
                onRetry={props.onOpenRetry}
                requestId={props.openError.requestId}
                title="Chưa mở được ghi chú"
              />
            </div>
          ) : null}
          {props.editor ??
            (!props.opening && !props.openError ? (
              <div className="mx-auto flex max-w-reading flex-col items-start gap-4 pt-10">
                <NotebookPen aria-hidden="true" className="size-10 text-brand-600" />
                <h2 className="text-h2 text-ink">Chọn một ghi chú</h2>
                <p className="text-body text-ink-secondary">
                  Mở một trang ở cột bên trái để đọc và sửa, hoặc bắt đầu một trang mới.
                </p>
                <Button disabled={props.busy} onClick={props.onNew}>
                  <Plus aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
                  Tạo ghi chú
                </Button>
              </div>
            ) : null)}
        </section>
      </div>
      {props.confirmation}
    </div>
  )
}