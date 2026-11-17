import { BookMarked, Plus, RotateCcw, Search, Sparkles, Tag, Trash2 } from 'lucide-react'
import type { ReactNode } from 'react'

import type { ApiError } from '@/shared/api'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  PageHeader,
  Pagination,
  Skeleton,
  Tabs,
  type PageStateValue,
} from '@/shared/ui'

import {
  duplicateNoteId,
  noteDate,
  noteErrorMessage,
  notesPageSize,
  snippetParts,
  type NotesSearch,
} from '../personalNotes'
import type { NoteListItem, TrashNote } from '../types'

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
    <section aria-label={trash ? 'Ghi chú đã xoá' : 'Danh sách ghi chú'} className="space-y-4">
      {!trash ? (
        <form
          noValidate
          className="space-y-4 rounded-md border border-brand-100 bg-surface-subtle p-4 shadow-sm sm:p-5"
          onSubmit={(event) => {
            event.preventDefault()
            props.onSearch()
          }}
        >
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField
              htmlFor="notes-query"
              label="Từ khoá tìm kiếm"
              hint="Từ 2 đến 100 ký tự; để trống để xem tất cả."
            >
              <Input
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
            <p role="alert" className="text-body-sm text-danger">
              {props.filterError}
            </p>
          ) : null}
          <div className="flex flex-wrap gap-2">
            <Button type="submit">
              <span className="inline-flex items-center gap-2">
                <Search aria-hidden="true" className="size-4 shrink-0" />
                <span>Tìm ghi chú</span>
              </span>
            </Button>
            <Button variant="outline" onClick={props.onClear}>
              Xoá bộ lọc
            </Button>
          </div>
        </form>
      ) : (
        <p className="rounded-md border border-brand-100 bg-surface-subtle p-4 text-body-sm text-ink-secondary">
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
          <ul className="space-y-3">
            {items.map((note) => (
              <li
                key={note.id}
                className="space-y-3 rounded-md border border-line border-l-4 border-l-brand-600 bg-surface p-4 shadow-sm sm:p-5"
              >
                <div className="flex items-start justify-between gap-2">
                  <h2 className="break-words text-h3 text-ink">
                    {note.title || 'Ghi chú không có tiêu đề'}
                  </h2>
                  <BookMarked aria-hidden="true" className="mt-1 size-4 shrink-0 text-brand-700" />
                </div>
                <p className="whitespace-pre-wrap break-words rounded-sm bg-canvas p-3 text-body text-ink">
                  {snippetParts(note.snippet, trash ? '' : search.q).map((part, index) =>
                    part.match ? <mark key={index}>{part.text}</mark> : part.text,
                  )}
                </p>
                <ul
                  aria-label="Thẻ"
                  className="flex flex-wrap gap-2 text-body-sm text-ink-secondary"
                >
                  {note.tags.map((tag, index) => (
                    <li
                      key={index}
                      className="inline-flex items-center gap-1 rounded-pill border border-brand-100 bg-brand-50 px-2.5 py-0.5 text-caption font-medium text-brand-700"
                    >
                      <Tag aria-hidden="true" className="size-3 shrink-0" />
                      <span>{tag}</span>
                    </li>
                  ))}
                </ul>
                <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line pt-3">
                  <div className="space-y-0.5 text-caption text-ink-secondary">
                    <p>
                      Cập nhật: <time dateTime={note.updatedAt}>{noteDate(note.updatedAt)}</time>
                    </p>
                    {trash && 'restorableUntil' in note ? (
                      <>
                        <p>
                          Đã xoá: <time dateTime={note.deletedAt}>{noteDate(note.deletedAt)}</time>
                        </p>
                        <p>
                          Khôi phục đến:{' '}
                          <time dateTime={note.restorableUntil}>
                            {noteDate(note.restorableUntil)}
                          </time>
                        </p>
                      </>
                    ) : null}
                  </div>
                  {trash && 'restorableUntil' in note ? (
                    <Button
                      variant="outline"
                      loading={props.restoringId === note.id}
                      disabled={props.busy}
                      onClick={() => props.onRestore(note.id)}
                    >
                      <span className="inline-flex items-center gap-2">
                        <RotateCcw aria-hidden="true" className="size-4 shrink-0" />
                        <span>Khôi phục</span>
                      </span>
                    </Button>
                  ) : (
                    <div className="flex flex-wrap gap-2">
                      <Button
                        variant="outline"
                        disabled={props.busy}
                        onClick={() => props.onOpenNote(note.id)}
                      >
                        Mở ghi chú
                      </Button>
                      <Button
                        variant="ghost"
                        disabled={props.busy}
                        onClick={() => props.onDelete(note.id)}
                      >
                        <span className="inline-flex items-center gap-1.5">
                          <Trash2 aria-hidden="true" className="size-4 shrink-0" />
                          <span>Xoá ghi chú</span>
                        </span>
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
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-20 sm:px-6">
      <PageHeader
        title="Sổ tay cá nhân"
        subtitle="Ghi lại điều muốn nhớ, tìm theo nội dung và thẻ."
        actions={
          <Button disabled={props.busy} onClick={props.onNew}>
            <span className="inline-flex items-center gap-2">
              <Plus aria-hidden="true" className="size-4 shrink-0" />
              <span>Tạo ghi chú</span>
            </span>
          </Button>
        }
      />

      <Card className="flex flex-wrap items-center justify-between gap-3 border-brand-100 bg-surface-subtle p-4 sm:px-5">
        <div className="flex items-center gap-2 text-body-sm text-ink">
          <Sparkles aria-hidden="true" className="size-4 shrink-0 text-brand-700" />
          <span>
            Sổ tay học tập cá nhân giúp lưu mẹo nhớ chữ Hán, phân biệt từ cận nghĩa và ví dụ ngữ
            cảnh.
          </span>
        </div>
        {props.total !== undefined ? (
          <span className="rounded-pill border border-brand-100 bg-surface px-3 py-0.5 text-caption font-semibold text-brand-700">
            {props.total} mục
          </span>
        ) : null}
      </Card>

      {props.notice ? (
        <p
          role="status"
          className="rounded-md border border-success bg-success-bg p-4 text-body-sm text-success"
        >
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
      <div className="grid min-w-0 items-start gap-6 lg:grid-cols-2">
        <div className="min-w-0">
          <Tabs
            label="Sổ tay"
            value={search.tab}
            onChange={(tab) => props.onTabChange(tab === 'trash' ? 'trash' : 'active')}
            items={[
              { id: 'active', label: 'Ghi chú', panel: list },
              { id: 'trash', label: 'Thùng rác', panel: list },
            ]}
          />
        </div>
        <section aria-label="Chỉnh sửa ghi chú" className="min-w-0 space-y-4">
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
              <EmptyState
                title="Chọn một ghi chú"
                description="Mở ghi chú để sửa hoặc tạo ghi chú mới."
              />
            ) : null)}
        </section>
      </div>
      {props.confirmation}
    </main>
  )
}
