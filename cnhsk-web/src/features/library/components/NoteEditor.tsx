import { ChevronRight, Save, Tag, X } from 'lucide-react'
import type { ApiError } from '@/shared/api'
import { Button, ErrorState } from '@/shared/ui'

import { duplicateNoteId, noteErrorMessage, type NoteDraft } from '../personalNotes'

export interface NoteEditorProps {
  draft: NoteDraft
  pending: boolean
  opening: boolean
  validation?: string
  error?: ApiError
  onChange: (draft: NoteDraft) => void
  onSubmit: () => void
  onOpenNote: (id: number) => void
}

/** Each part of a note sits in its own labelled box; the box outline turns brand red while typing. */
const box =
  'rounded-lg border border-line bg-surface transition-colors focus-within:border-brand-600 focus-within:ring-2 focus-within:ring-brand-100'
const boxHead = 'flex items-center justify-between gap-3 px-4 pt-3 text-label text-ink-secondary'

export function NoteEditor({
  draft,
  pending,
  opening,
  validation,
  error,
  onChange,
  onSubmit,
  onOpenNote,
}: NoteEditorProps) {
  const duplicateId = duplicateNoteId(error)
  const canAddTag = Boolean(draft.tagInput.trim()) && draft.tags.length < 10 && draft.tagInput.trim().length <= 50
  const addTag = () => onChange({ ...draft, tags: [...draft.tags, draft.tagInput.trim()], tagInput: '' })
  return (
    <form
      aria-label="Soạn ghi chú"
      className="notes-page mx-auto w-full min-w-0 max-w-reading space-y-6"
      noValidate
      onKeyDown={(event) => {
        if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
          event.preventDefault()
          onSubmit()
        }
      }}
      onSubmit={(event) => {
        event.preventDefault()
        onSubmit()
      }}
    >
      <div className="flex min-w-0 items-center justify-between gap-3">
        <p className="flex min-w-0 items-center gap-1 text-body-sm text-ink-secondary">
          <span>Sổ tay</span>
          <ChevronRight aria-hidden="true" className="size-4 shrink-0" />
          <span className="truncate font-medium text-ink">
            {draft.id ? draft.title.trim() || 'Ghi chú không có tiêu đề' : 'Ghi chú mới'}
          </span>
        </p>
        <Button className="shrink-0 rounded-pill" disabled={opening} loading={pending} size="sm" type="submit">
          <Save aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
          Lưu ghi chú
        </Button>
      </div>

      <fieldset className="m-0 min-w-0 space-y-4 border-0 p-0" disabled={pending || opening}>
        <legend className="sr-only">Nội dung và thẻ</legend>

        <div className={box}>
          <div className={boxHead}>
            <label htmlFor="note-title">Tiêu đề</label>
            <span className="tabular-nums">{draft.title.length}/200 · tuỳ chọn</span>
          </div>
          <input
            autoComplete="off"
            className="w-full border-0 bg-transparent px-4 pb-3 pt-1 text-h2 text-ink outline-none placeholder:font-normal placeholder:text-ink-muted focus-visible:outline-none focus-visible:ring-0 focus-visible:ring-offset-0"
            id="note-title"
            onChange={(event) => onChange({ ...draft, title: event.target.value })}
            placeholder="Đặt tên cho ghi chú"
            value={draft.title}
          />
        </div>

        <div className={box}>
          <div className={boxHead}>
            <span className="inline-flex items-center gap-1.5">
              <Tag aria-hidden="true" className="size-3.5" />
              Thẻ
            </span>
            <span className="tabular-nums">{draft.tags.length}/10 thẻ</span>
          </div>
          <div className="flex min-w-0 flex-wrap items-center gap-2 px-4 pb-3 pt-1">
            <ul aria-label="Thẻ ghi chú" className="contents">
              {draft.tags.map((tag, index) => (
                <li
                  className="inline-flex items-center gap-1 rounded-pill bg-brand-50 py-0.5 pl-3 pr-1 text-body-sm font-medium text-brand-800"
                  key={index}
                >
                  {tag}
                  <button
                    aria-label={`Bỏ thẻ ${tag}`}
                    className="flex size-6 items-center justify-center rounded-pill text-brand-700 hover:bg-brand-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
                    onClick={() =>
                      onChange({ ...draft, tags: draft.tags.filter((_, tagIndex) => tagIndex !== index) })
                    }
                    type="button"
                  >
                    <X aria-hidden="true" className="size-3.5" />
                  </button>
                </li>
              ))}
            </ul>
            <label className="sr-only" htmlFor="note-tag">
              Thẻ mới
            </label>
            <input
              autoComplete="off"
              className="min-w-32 flex-1 border-0 bg-transparent py-1 text-body text-ink outline-none placeholder:text-ink-muted focus-visible:outline-none focus-visible:ring-0 focus-visible:ring-offset-0"
              id="note-tag"
              onChange={(event) => onChange({ ...draft, tagInput: event.target.value })}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  event.preventDefault()
                  if (canAddTag) addTag()
                }
              }}
              placeholder={draft.tags.length ? 'Thêm thẻ…' : 'Gõ tên thẻ rồi Enter'}
              value={draft.tagInput}
            />
            {draft.tagInput.trim() ? (
              <Button disabled={!canAddTag} onClick={addTag} size="sm" variant="outline">
                Thêm thẻ
              </Button>
            ) : null}
          </div>
        </div>

        <div className={box}>
          <div className={boxHead}>
            <label htmlFor="note-content">
              Nội dung <span className="text-danger">*</span>
            </label>
            <span className="tabular-nums">{draft.content.length.toLocaleString('vi-VN')}/10.000</span>
          </div>
          <textarea
            className="min-h-[45dvh] w-full resize-y border-0 bg-transparent px-4 pb-4 pt-1 text-body leading-relaxed text-ink outline-none placeholder:text-ink-muted focus-visible:outline-none focus-visible:ring-0 focus-visible:ring-offset-0"
            id="note-content"
            onChange={(event) => onChange({ ...draft, content: event.target.value })}
            placeholder="Ghi lại điều muốn nhớ… (Ctrl+S để lưu)"
            value={draft.content}
          />
        </div>
        <p className="text-body-sm text-ink-secondary">Thẻ đang gõ dở cũng được lưu khi bấm Lưu ghi chú.</p>
      </fieldset>

      {validation ? (
        <p className="text-body-sm text-danger" role="alert">
          {validation}
        </p>
      ) : null}
      {error ? (
        <div className="space-y-3" role="alert">
          <ErrorState message={noteErrorMessage(error)} requestId={error.requestId} title="Chưa lưu được ghi chú" />
          {duplicateId ? (
            <Button disabled={opening || pending} onClick={() => onOpenNote(duplicateId)} variant="outline">
              Mở ghi chú đã có
            </Button>
          ) : null}
        </div>
      ) : null}
    </form>
  )
}
