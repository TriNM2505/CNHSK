import { useId, useState } from 'react'
import { Button } from '@/shared/ui/Button'
import { FormField, Select, Textarea } from '@/shared/ui/FormField'
import { REPORT_REASON_LABELS } from '../labels'
import { makeReport } from '../interaction-validation'
import type { ReportWrite } from '../types'

export type ReportDraft = { reason: string; detail: string }
type Props = { targetType: ReportWrite['targetType']; targetId: number; disabled: boolean; onSubmit: (body: ReportWrite) => void; onCancel: () => void } & (
  | { draft: ReportDraft; onDraftChange: (draft: ReportDraft) => void }
  | { draft?: never; onDraftChange?: never }
)
export function ReportForm({ targetType, targetId, disabled, onSubmit, onCancel, draft, onDraftChange }: Props) {
  const id = useId()
  const reasonId = `${id}-report-reason`
  const detailId = `${id}-report-detail`
  const [localDraft, setLocalDraft] = useState<ReportDraft>({ reason: '', detail: '' })
  const { reason, detail } = draft ?? localDraft
  const updateDraft = onDraftChange ?? setLocalDraft
  const [invalid, setInvalid] = useState(false)
  return <form aria-label="Báo cáo nội dung" className="my-4 space-y-4 rounded-md border border-line bg-surface p-4 shadow-sm" onSubmit={(event) => {
    event.preventDefault()
    const body = makeReport(targetType, targetId, reason, detail)
    setInvalid(!body)
    if (body && !disabled) onSubmit(body)
  }}>
    <FormField htmlFor={reasonId} label="Lý do báo cáo" required>
      <Select id={reasonId} disabled={disabled} value={reason} onChange={(event) => updateDraft({ reason: event.target.value, detail })}>
        <option value="">Chọn lý do báo cáo</option>
        {Object.entries(REPORT_REASON_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </Select>
    </FormField>
    <FormField htmlFor={detailId} label="Thông tin bổ sung" required={reason === 'OTHER'} hint={`${detail.trim().length}/1.000`} error={invalid ? 'Chọn lý do hợp lệ; chi tiết tối đa 1.000 ký tự và bắt buộc khi chọn Khác.' : undefined}>
      <Textarea className="min-h-32" id={detailId} disabled={disabled} value={detail} onChange={(event) => updateDraft({ reason, detail: event.target.value })} />
    </FormField>
    <div className="flex flex-wrap gap-3">
      <Button disabled={disabled} type="submit">Gửi báo cáo</Button>
      <Button type="button" variant="outline" onClick={onCancel}>Đóng</Button>
    </div>
  </form>
}
