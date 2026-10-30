import { useState } from 'react'
import { Button } from '@/shared/ui/Button'
import { FormField } from '@/shared/ui/FormField'
import { validateText } from '../interaction-validation'
import type { CommentModeration } from '../types'

export type ModerationDraft = { reason: string; confirming: boolean }
type Props = { action: CommentModeration['action']; disabled: boolean; onSubmit: (body: CommentModeration) => void; onCancel: () => void } & (
  | { draft: ModerationDraft; onDraftChange: (draft: ModerationDraft) => void }
  | { draft?: never; onDraftChange?: never }
)
export function CommentModerationForm({ action, disabled, onSubmit, onCancel, draft, onDraftChange }: Props) {
  const [localDraft, setLocalDraft] = useState<ModerationDraft>({ reason: '', confirming: false })
  const { reason, confirming } = draft ?? localDraft
  const updateDraft = onDraftChange ?? setLocalDraft
  const [invalid, setInvalid] = useState<string>()
  return <form aria-label="Kiểm duyệt bình luận" className="my-4 rounded-md border border-line p-4" onSubmit={(event) => {
    event.preventDefault()
    const error = validateText(reason, 1000)
    setInvalid(error)
    if (!error && !disabled) updateDraft({ reason, confirming: true })
  }}>
    <FormField htmlFor="moderation-reason" label="Lý do kiểm duyệt" required error={invalid} hint={`${reason.trim().length}/1.000`}>
      <textarea className="min-h-32 w-full border border-line p-3" id="moderation-reason" disabled={disabled || confirming} value={reason} onChange={(event) => updateDraft({ reason: event.target.value, confirming })} />
    </FormField>
    {confirming ? <section aria-label="Xác nhận kiểm duyệt"><p>Xác nhận {action === 'HIDE' ? 'ẩn' : action === 'DELETE' ? 'xoá' : 'khôi phục'} bình luận này? Các trả lời được giữ nguyên.</p><Button disabled={disabled} type="button" onClick={() => onSubmit({ action, reason: reason.trim() })}>Xác nhận kiểm duyệt</Button><Button disabled={disabled} type="button" variant="outline" onClick={() => updateDraft({ reason, confirming: false })}>Quay lại</Button></section> : <Button disabled={disabled} type="submit">Tiếp tục</Button>}
    <Button className="ml-3" disabled={disabled} type="button" variant="outline" onClick={onCancel}>Đóng</Button>
  </form>
}
