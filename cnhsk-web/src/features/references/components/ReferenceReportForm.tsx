import { useId, useState } from 'react'

import type { ApiError } from '@/shared/api'
import { Button, FormField, Select, Textarea } from '@/shared/ui'

import type { ReportRequest } from '../types'

type Props = {
  pending: boolean
  sent: boolean
  error?: ApiError
  onSubmit: (request: ReportRequest) => void
  onCancel: () => void
}

export function ReferenceReportForm({ pending, sent, error, onSubmit, onCancel }: Props) {
  const id = useId()
  const [reason, setReason] = useState<ReportRequest['reason']>('DEAD_LINK')
  const [note, setNote] = useState('')
  const count = [...note].length

  if (sent)
    return (
      <p role="status" className="text-body-sm text-ink">
        Đã ghi nhận báo lỗi.
      </p>
    )

  return (
    <form
      aria-label="Báo lỗi tài liệu"
      className="mt-3 min-w-0 border-t border-line pt-3"
      onSubmit={(event) => {
        event.preventDefault()
        if (!pending && count <= 300) onSubmit({ reason, note: note || null })
      }}
    >
      <FormField htmlFor={`${id}-reason`} label="Lý do">
        <Select
          disabled={pending}
          value={reason}
          onChange={(event) => setReason(event.target.value as ReportRequest['reason'])}
        >
          <option value="DEAD_LINK">Liên kết không mở được</option>
          <option value="WRONG_CONTENT">Nội dung không đúng</option>
          <option value="OTHER">Lý do khác</option>
        </Select>
      </FormField>
      <FormField
        htmlFor={`${id}-note`}
        label="Ghi chú (không bắt buộc)"
        hint={`${count}/300 ký tự`}
        error={count > 300 ? 'Ghi chú tối đa 300 ký tự.' : undefined}
      >
        <Textarea
          disabled={pending}
          rows={3}
          value={note}
          onChange={(event) => setNote(event.target.value)}
        />
      </FormField>
      {error ? (
        <p role="alert" className="mb-3 text-body-sm text-danger">
          {error.errorCode === 'RATE_LIMITED'
            ? 'Bạn đã báo lỗi tài liệu này hôm nay. Vui lòng quay lại vào ngày mai.'
            : error.message}
        </p>
      ) : null}
      <div className="flex flex-wrap gap-2">
        <Button disabled={count > 300} loading={pending} type="submit" size="sm">
          Gửi báo lỗi
        </Button>
        <Button disabled={pending} onClick={onCancel} type="button" size="sm" variant="ghost">
          Huỷ
        </Button>
      </div>
    </form>
  )
}
