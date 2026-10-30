import { useEffect, useRef, useState } from 'react'
import { Button } from '@/shared/ui/Button'
import { FormField, Textarea } from '@/shared/ui/FormField'
import { validateText } from '../interaction-validation'
import type { RejectWrite } from '../types'

type Props = {
  reason: string
  onReasonChange: (reason: string) => void
  disabled: boolean
  onApprove: () => void
  onReject: (body: RejectWrite) => void
  onCancel: () => void
}

export function PostReviewForm({ reason, onReasonChange, disabled, onApprove, onReject, onCancel }: Props) {
  const [selection, setSelection] = useState<'approve' | 'reject'>()
  const [confirming, setConfirming] = useState(false)
  const [invalid, setInvalid] = useState<string>()
  const reasonInput = useRef<HTMLTextAreaElement>(null)
  const approveButton = useRef<HTMLButtonElement>(null)
  const rejectButton = useRef<HTMLButtonElement>(null)
  const restoreApprovalFocus = useRef(false)
  useEffect(() => {
    if (disabled) setConfirming(false)
  }, [disabled])
  useEffect(() => {
    if (selection === 'reject' && !confirming && !disabled) reasonInput.current?.focus()
    if (restoreApprovalFocus.current && !confirming && !disabled) {
      restoreApprovalFocus.current = false
      approveButton.current?.focus()
    }
  }, [selection, confirming, disabled])
  const validate = () => {
    const error = validateText(reason, 1000)
    setInvalid(error)
    if (error) reasonInput.current?.focus()
    return !error
  }
  const back = () => {
    setConfirming(false)
    if (selection === 'approve') setSelection(undefined)
    if (selection === 'approve') restoreApprovalFocus.current = true
    else reasonInput.current?.focus()
  }
  return <form aria-label="Kiểm duyệt bài viết" className="my-6 rounded-md border border-line bg-surface p-4" onSubmit={(event) => {
    event.preventDefault()
    if (!disabled && selection === 'reject' && validate()) setConfirming(true)
  }}>
    <h2 className="mb-4 text-h2">Quyết định kiểm duyệt</h2>
    <div className="flex flex-wrap gap-3">
      <button ref={approveButton} className="min-h-control rounded-sm bg-brand-600 px-4 text-surface disabled:opacity-50" disabled={disabled || confirming} type="button" onClick={() => { setSelection('approve'); setConfirming(true); setInvalid(undefined) }}>Duyệt</button>
      <button ref={rejectButton} className="min-h-control rounded-sm border border-danger px-4 text-danger disabled:opacity-50" disabled={disabled || confirming} type="button" onClick={() => { setSelection('reject'); setInvalid(undefined) }}>Từ chối</button>
    </div>
    {selection === 'reject' ? <div className="mt-4">
      <FormField htmlFor="post-rejection-reason" label="Lý do từ chối" required error={invalid} hint={`${reason.trim().length}/1.000 ký tự`}>
        <Textarea ref={reasonInput} id="post-rejection-reason" disabled={disabled || confirming} value={reason} onChange={(event) => { onReasonChange(event.target.value); setInvalid(undefined) }} />
      </FormField>
      {!confirming ? <Button disabled={disabled} type="submit">Tiếp tục từ chối</Button> : null}
    </div> : null}
    {confirming ? <section aria-label="Xác nhận kiểm duyệt bài viết" className="my-4 border-t border-line pt-4">
      <p className="mb-3">{selection === 'approve' ? 'Duyệt và đăng bài viết này?' : 'Từ chối bài viết với lý do đã nhập?'}</p>
      <div className="flex flex-wrap gap-3">
        <Button disabled={disabled} type="button" onClick={() => {
          if (disabled) return
          if (selection === 'approve') onApprove()
          else if (selection === 'reject' && validate()) onReject({ reason: reason.trim() })
        }}>{selection === 'approve' ? 'Xác nhận duyệt' : 'Xác nhận từ chối'}</Button>
        <Button disabled={disabled} type="button" variant="outline" onClick={back}>Quay lại</Button>
      </div>
    </section> : null}
    <Button className="mt-4" disabled={disabled} type="button" variant="ghost" onClick={onCancel}>Huỷ kiểm duyệt</Button>
  </form>
}
