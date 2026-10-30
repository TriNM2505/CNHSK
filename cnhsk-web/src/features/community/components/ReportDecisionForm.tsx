import { useRef, useState } from 'react'
import { ApiError } from '@/shared/api'
import { Button } from '@/shared/ui/Button'
import { FormField, Select, Textarea } from '@/shared/ui/FormField'
import { validateText } from '../interaction-validation'
import { makeReportDecision } from '../moderation-actions'
import type { ReportDecision, ReportTargetType } from '../types'

type Props = {
  targetKind: ReportTargetType
  note: string
  onNoteChange: (value: string) => void
  decision: ReportDecision['decision'] | ''
  onDecisionChange: (value: ReportDecision['decision'] | '') => void
  commentAction: NonNullable<ReportDecision['commentAction']> | ''
  onCommentActionChange: (value: NonNullable<ReportDecision['commentAction']> | '') => void
  confirming: boolean
  onConfirmingChange: (value: boolean) => void
  disabled: boolean
  error?: unknown
  onSubmit: (body: ReportDecision) => void
  onCancel: () => void
}

export function ReportDecisionForm({ targetKind, note, onNoteChange, decision, onDecisionChange, commentAction, onCommentActionChange, confirming, onConfirmingChange, disabled, error, onSubmit, onCancel }: Props) {
  const [attempted, setAttempted] = useState(false)
  const decisionRef = useRef<HTMLSelectElement>(null)
  const noteRef = useRef<HTMLTextAreaElement>(null)
  const commentRef = useRef<HTMLSelectElement>(null)
  const serverErrors: Partial<Record<'decision' | 'note' | 'commentAction', string>> = {}
  if (error instanceof ApiError && error.status === 422 && Array.isArray(error.details)) {
    for (const detail of error.details as unknown[]) {
      if (typeof detail !== 'object' || detail === null || !('field' in detail) || !('message' in detail) || typeof detail.message !== 'string') continue
      if (detail.field === 'decision' || detail.field === 'note' || detail.field === 'commentAction') serverErrors[detail.field] = detail.message
    }
  }
  const noteError = (attempted ? validateText(note, 1000) : undefined) ?? serverErrors.note
  const needsCommentAction = targetKind === 'COMMENT' && decision === 'ACTION'
  const body = makeReportDecision(targetKind, decision, note, commentAction)
  return <form className="mt-4 rounded-md border border-line bg-surface p-4" aria-label="Quyết định hồ sơ" onSubmit={(event) => {
    event.preventDefault()
    if (disabled) return
    setAttempted(true)
    if (!decision) decisionRef.current?.focus()
    else if (validateText(note, 1000)) noteRef.current?.focus()
    else if (needsCommentAction && !commentAction) commentRef.current?.focus()
    if (body) onConfirmingChange(true)
  }}>
    <h2 className="text-h2">Quyết định hồ sơ</h2>
    <FormField htmlFor="report-decision" label="Quyết định" required error={attempted && !decision ? 'Chọn quyết định xử lý hồ sơ.' : serverErrors.decision}>
      <Select ref={decisionRef} value={decision} disabled={disabled || confirming} onChange={(event) => {
        const value = event.target.value
        if (value === '' || value === 'ACTION' || value === 'REJECT') { onDecisionChange(value); onCommentActionChange('') }
      }}>
        <option value="">Chọn quyết định</option><option value="REJECT">Bác báo cáo</option><option value="ACTION">Xử lý nội dung</option>
      </Select>
    </FormField>
    <FormField htmlFor="report-note" label="Lý do xử lý" required error={noteError} hint={`${note.trim().length}/1.000 ký tự`}>
      <Textarea ref={noteRef} value={note} disabled={disabled || confirming} onChange={(event) => onNoteChange(event.target.value)} />
    </FormField>
    {needsCommentAction ? <FormField htmlFor="report-comment-action" label="Thao tác với bình luận" required error={attempted && !commentAction ? 'Chọn Ẩn hoặc Xoá bình luận.' : serverErrors.commentAction}>
      <Select ref={commentRef} value={commentAction} disabled={disabled || confirming} onChange={(event) => { const value = event.target.value; if (value === '' || value === 'HIDE' || value === 'DELETE') onCommentActionChange(value) }}>
        <option value="">Chọn thao tác</option><option value="HIDE">Ẩn bình luận</option><option value="DELETE">Xoá bình luận</option>
      </Select>
    </FormField> : null}
    {confirming ? <section aria-label="Xác nhận quyết định hồ sơ"><p>{decision === 'REJECT' ? 'Bác toàn bộ báo cáo trong hồ sơ và giữ nguyên nội dung?' : targetKind === 'POST' ? 'Ẩn bài viết và đóng toàn bộ báo cáo trong hồ sơ?' : `${commentAction === 'DELETE' ? 'Xoá' : 'Ẩn'} bình luận và đóng toàn bộ báo cáo trong hồ sơ? Các trả lời được giữ nguyên.`}</p><Button disabled={disabled || !body} onClick={() => { if (!disabled && body) onSubmit(body) }}>Xác nhận quyết định</Button><Button className="ml-3" variant="outline" disabled={disabled} onClick={() => onConfirmingChange(false)}>Quay lại</Button></section> : <Button disabled={disabled} type="submit">Tiếp tục quyết định</Button>}
    <Button className="mt-3" variant="outline" disabled={disabled} onClick={onCancel}>Huỷ và về hàng đợi</Button>
  </form>
}
