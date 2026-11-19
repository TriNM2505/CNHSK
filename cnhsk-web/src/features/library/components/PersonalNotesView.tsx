import {
  ArrowRight,
  BookOpenText,
  CheckCircle2,
  Clock3,
  FilePenLine,
  FolderOpen,
  Plus,
  RotateCcw,
  Search,
  Tag,
  Trash2,
} from 'lucide-react'
import type { ReactNode } from 'react'

import type { ApiError } from '@/shared/api'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  FormField,
  HanziText,
  Input,
  PageBanner,
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
  const editorOpen = Boolean(props.editor || props.opening || props.openError)
  const list = (
    <section
      key={search.tab}
      aria-label={trash ? 'Ghi chú đã xoá' : 'Danh sách ghi chú'}
      className="notes-enter min-w-0 space-y-4"
    >
      {!trash ? (
        <form
          noValidate
          className="rounded-md border border-brand-100 bg-surface p-4"
          onSubmit={(event) => {
            event.preventDefault()
            props.onSearch()
          }}
        >
          <div className="grid min-w-0 gap-x-4 sm:grid-cols-2">
            <FormField
              htmlFor="notes-query"
              label="Từ khoá tìm kiếm"
              hint="Từ 2 đến 100 ký tự; để trống để xem tất cả."
            >
              <Input
                className="min-w-0"
                type="search"
                value={filters.q}
                onChange={(event) => props.onFiltersChange({ ...filters, q: event.target.value })}
              />
            </FormField>
            <FormField
              htmlFor="notes-tag-filter"
              label="Lọc theo thẻ"
              hint="Nhập chính xác thẻ cần lọc."
            >
              <Input
                value={filters.tag}
                onChange={(event) => props.onFiltersChange({ ...filters, tag: event.target.value })}
              />
            </FormField>
          </div>
          {props.filterError ? (
            <p role="alert" className="mb-3 text-body-sm text-danger">
              {props.filterError}
            </p>
          ) : null}
          <div className="flex flex-wrap gap-2">
            <Button className="rounded-pill" type="submit">
              <Search aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
              Tìm ghi chú
            </Button>
            <Button variant="outline" onClick={props.onClear}>
              Xoá bộ lọc
            </Button>
          </div>
        </form>
      ) : (
        <p className="rounded-md border border-brand-100 bg-brand-50 p-4 text-body-sm text-ink-secondary">
          Có thể khôi phục trong 30 ngày. Ghi chú quá hạn được ẩn khỏi thùng rác.
        </p>
      )}
      {state === 'loading' ? (
        <div aria-label={trash ? 'Đang tải thùng rác' : 'Đang tải ghi chú'}>
          <Skeleton className="h-32" />
        </div>
      ) : null}
      {state === 'error' ? (
        <ErrorState
          title={trash ? 'Chưa tải được thùng rác' : 'Chưa tải được ghi chú'}
          message={props.error ? noteErrorMessage(props.error) : 'Vui lòng thử lại.'}
          requestId={props.error?.requestId}
          onRetry={props.onRetry}
        />
      ) : null}
      {state === 'empty' ? (
        <EmptyState
          title={
            trash
              ? 'Thùng rác trống'
              : search.q || search.tag
                ? 'Không tìm thấy ghi chú'
                : 'Chưa có ghi chú'
          }
          description={
            trash
              ? 'Ghi chú đã xoá còn hạn khôi phục sẽ xuất hiện ở đây.'
              : 'Tạo ghi chú mới hoặc xoá bộ lọc để xem lại.'
          }
          action={
            !trash ? (
              <Button
                variant="outline"
                onClick={search.q || search.tag ? props.onClear : props.onNew}
              >
                {search.q || search.tag ? 'Xoá bộ lọc' : 'Tạo ghi chú'}
              </Button>
            ) : undefined
          }
        />
      ) : null}
      {state === 'ready' ? (
        <>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="inline-flex items-center gap-2 text-label text-ink">
              <FolderOpen aria-hidden="true" className="size-4 text-brand-600" />
              {trash ? 'Ghi chú trong thùng rác' : 'Những điều muốn nhớ'}
            </h2>
            {props.total !== undefined ? (
              <span className="text-body-sm text-ink-secondary">{props.total} ghi chú</span>
            ) : null}
          </div>
          <ul className="notes-grid grid min-w-0 items-start gap-4 sm:grid-cols-2">
            {items.map((note) => (
              <li
                key={note.id}
                className="group relative isolate min-w-0 overflow-hidden rounded-md border border-brand-100 bg-surface p-4 shadow-sm transition duration-200 hover:border-brand-600 hover:shadow-md focus-within:border-brand-600 motion-safe:hover:-translate-y-0.5"
              >
                <div className="relative z-base space-y-3">
                  <div className="flex min-w-0 items-start gap-3">
                    <span className="flex size-9 shrink-0 items-center justify-center rounded-md bg-brand-50 text-brand-600 transition duration-200 motion-safe:group-hover:-rotate-6">
                      <FilePenLine aria-hidden="true" className="size-4" />
                    </span>
                    <h2 className="min-w-0 break-words text-h3 text-ink">
                      <NoteText text={note.title || 'Ghi chú không có tiêu đề'} />
                    </h2>
                  </div>
                  <p className="whitespace-pre-wrap break-words text-body text-ink">
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
                  <ul
                    aria-label="Thẻ"
                    className="flex flex-wrap gap-2 text-body-sm text-ink-secondary"
                  >
                    {note.tags.map((tag, index) => (
                      <li
                        key={index}
                        className="inline-flex items-center gap-1 rounded-pill border border-brand-100 bg-brand-50 px-2 py-1 text-caption text-brand-700 transition-colors hover:bg-brand-100"
                      >
                        <Tag aria-hidden="true" className="size-3 shrink-0" />
                        <NoteText text={tag} />
                      </li>
                    ))}
                  </ul>
                  <p className="text-caption text-ink-secondary">
                    <Clock3
                      aria-hidden="true"
                      className="mr-1 inline-block size-3.5 align-middle"
                    />
                    Cập nhật: <time dateTime={note.updatedAt}>{noteDate(note.updatedAt)}</time>
                  </p>
                  {trash && 'restorableUntil' in note ? (
                    <>
                      <p className="text-caption text-ink-secondary">
                        Đã xoá: <time dateTime={note.deletedAt}>{noteDate(note.deletedAt)}</time>
                      </p>
                      <p className="text-caption text-ink-secondary">
                        Khôi phục đến:{' '}
                        <time dateTime={note.restorableUntil}>
                          {noteDate(note.restorableUntil)}
                        </time>
                      </p>
                      <Button
                        variant="outline"
                        loading={props.restoringId === note.id}
                        disabled={props.busy}
                        onClick={() => props.onRestore(note.id)}
                      >
                        <RotateCcw
                          aria-hidden="true"
                          className="mr-2 inline-block size-4 align-middle"
                        />
                        Khôi phục
                      </Button>
                    </>
                  ) : (
                    <div className="flex flex-wrap gap-2">
                      <Button
                        variant="outline"
                        disabled={props.busy}
                        onClick={() => props.onOpenNote(note.id)}
                      >
                        Mở ghi chú
                        <ArrowRight
                          aria-hidden="true"
                          className="ml-2 inline-block size-4 align-middle transition-transform duration-200 motion-safe:group-hover:translate-x-1"
                        />
                      </Button>
                      <Button
                        variant="ghost"
                        disabled={props.busy}
                        onClick={() => props.onDelete(note.id)}
                      >
                        <Trash2
                          aria-hidden="true"
                          className="mr-2 inline-block size-4 align-middle"
                        />
                        Xoá ghi chú
                      </Button>
                    </div>
                  )}
                </div>
              </li>
            ))}
          </ul>
          {props.total !== undefined ? (
            <Pagination
              page={search.page + 1}
              size={notesPageSize}
              total={props.total}
              onChange={(page) => props.onPageChange(page - 1)}
            />
          ) : null}
        </>
      ) : null}
    </section>
  )
  return (
    <div className="library-screen personal-notes mx-auto w-full min-w-0 max-w-content space-y-4 px-4 py-6 pb-24 sm:px-6 lg:px-8">
      <PageBanner
        title="Sổ tay cá nhân"
        subtitle="Ghi lại điều muốn nhớ, tìm theo nội dung và thẻ."
        actions={
          <Button className="rounded-pill shadow-sm" disabled={props.busy} onClick={props.onNew}>
            <Plus aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
            Tạo ghi chú
          </Button>
        }
      />
      {props.notice ? (
        <p
          role="status"
          className="notes-enter flex items-start gap-2 rounded-md bg-success-bg p-3 text-body text-success"
        >
          <CheckCircle2 aria-hidden="true" className="mt-1 size-4 shrink-0" />
          {props.notice}
        </p>
      ) : null}
      {props.actionError ? (
        <div role="alert" className="space-y-3">
          <ErrorState
            title="Chưa hoàn tất thao tác"
            message={noteErrorMessage(props.actionError)}
            requestId={props.actionError.requestId}
            onRetry={props.onActionRetry}
          />
          {duplicateId ? (
            <Button
              variant="outline"
              disabled={props.busy || props.opening}
              onClick={() => props.onOpenNote(duplicateId)}
            >
              Mở ghi chú đã có
            </Button>
          ) : null}
        </div>
      ) : null}
      <div
        className={
          editorOpen
            ? 'notes-workspace grid min-w-0 items-start gap-4 lg:grid-cols-2'
            : 'notes-workspace min-w-0 space-y-4'
        }
        data-editor-open={editorOpen}
      >
        <Tabs
          label="Sổ tay"
          value={search.tab}
          onChange={(tab) => props.onTabChange(tab === 'trash' ? 'trash' : 'active')}
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
        />
        <section
          aria-label="Chỉnh sửa ghi chú"
          className={
            editorOpen
              ? 'notes-editor-panel notes-enter min-w-0 space-y-4 lg:sticky lg:top-20'
              : 'min-w-0'
          }
        >
          {props.opening ? (
            <p role="status" className="text-body text-ink-secondary">
              Đang mở ghi chú…
            </p>
          ) : null}
          {props.openError ? (
            <ErrorState
              title="Chưa mở được ghi chú"
              message={noteErrorMessage(props.openError)}
              requestId={props.openError.requestId}
              onRetry={props.onOpenRetry}
            />
          ) : null}
          {props.editor ??
            (!props.opening && !props.openError ? (
              <Card className="relative isolate overflow-hidden border-brand-100 bg-brand-50 p-4">
                <div className="relative z-base flex items-center gap-3">
                  <span className="flex size-9 shrink-0 items-center justify-center rounded-md bg-surface text-brand-600">
                    <FilePenLine aria-hidden="true" className="size-4" />
                  </span>
                  <div>
                    <h2 className="text-label text-brand-800">Chọn một ghi chú</h2>
                    <p className="text-body-sm text-ink-secondary">
                      Mở ghi chú để sửa hoặc tạo ghi chú mới.
                    </p>
                  </div>
                </div>
              </Card>
            ) : null)}
        </section>
      </div>
      {props.confirmation}
    </div>
  )
}
