import { FilePenLine, Save, Tag } from 'lucide-react'
import type { ApiError } from '@/shared/api'
import { Button, ErrorState, FormField, Input, Textarea } from '@/shared/ui'

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
  return (
    <form
      aria-label="Soạn ghi chú"
      className="space-y-4 rounded-md border border-brand-100 bg-surface p-4 shadow-sm sm:p-5"
      noValidate
      onSubmit={(event) => {
        event.preventDefault()
        onSubmit()
      }}
    >
      <h2 className="inline-flex items-center gap-2 text-h3 text-ink">
        <FilePenLine aria-hidden="true" className="size-5 text-brand-600" />
        {draft.id ? 'Sửa ghi chú' : 'Ghi chú mới'}
      </h2>
      <fieldset disabled={pending || opening}>
        <legend className="sr-only">Nội dung và thẻ</legend>
        <FormField
          htmlFor="note-title"
          label="Tiêu đề"
          hint={`${draft.title.length}/200 ký tự · Tuỳ chọn`}
        >
          <Input
            className="font-hanzi"
            value={draft.title}
            onChange={(event) => onChange({ ...draft, title: event.target.value })}
          />
        </FormField>
        <FormField
          htmlFor="note-content"
          label="Nội dung"
          hint={`${draft.content.length}/10.000 ký tự`}
          required
        >
          <Textarea
            className="min-h-40 font-hanzi"
            value={draft.content}
            onChange={(event) => onChange({ ...draft, content: event.target.value })}
          />
        </FormField>
        <ul aria-label="Thẻ ghi chú" className="mb-3 flex flex-wrap gap-2">
          {draft.tags.map((tag, index) => (
            <li
              key={index}
              className="flex items-center gap-1 rounded-pill border border-brand-100 bg-brand-50 px-2 py-1 font-hanzi text-body-sm text-ink"
            >
              {tag}
              <Button
                size="sm"
                variant="ghost"
                aria-label={`Bỏ thẻ ${tag}`}
                onClick={() =>
                  onChange({
                    ...draft,
                    tags: draft.tags.filter((_, tagIndex) => tagIndex !== index),
                  })
                }
              >
                ×
              </Button>
            </li>
          ))}
        </ul>
        <FormField
          htmlFor="note-tag"
          label="Thẻ mới"
          hint={`${draft.tags.length}/10 thẻ · Mỗi thẻ tối đa 50 ký tự; thẻ đang nhập cũng được lưu khi bấm Lưu.`}
        >
          <Input
            className="font-hanzi"
            value={draft.tagInput}
            onChange={(event) => onChange({ ...draft, tagInput: event.target.value })}
          />
        </FormField>
        <Button
          variant="outline"
          disabled={
            !draft.tagInput.trim() || draft.tags.length >= 10 || draft.tagInput.trim().length > 50
          }
          onClick={() =>
            onChange({ ...draft, tags: [...draft.tags, draft.tagInput.trim()], tagInput: '' })
          }
        >
          <Tag aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
          Thêm thẻ
        </Button>
      </fieldset>
      {validation ? (
        <p role="alert" className="text-body-sm text-danger">
          {validation}
        </p>
      ) : null}
      {error ? (
        <div role="alert" className="space-y-3">
          <ErrorState
            title="Chưa lưu được ghi chú"
            message={noteErrorMessage(error)}
            requestId={error.requestId}
          />
          {duplicateId ? (
            <Button
              variant="outline"
              disabled={opening || pending}
              onClick={() => onOpenNote(duplicateId)}
            >
              Mở ghi chú đã có
            </Button>
          ) : null}
        </div>
      ) : null}
      <Button className="w-full rounded-pill" type="submit" loading={pending} disabled={opening}>
        <Save aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
        Lưu ghi chú
      </Button>
    </form>
  )
}
