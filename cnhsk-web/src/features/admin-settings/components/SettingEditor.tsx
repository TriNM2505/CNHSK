import { Button, FormField, Input, Textarea } from '@/shared/ui'

import { formatNumber, validateSetting } from '../settingsForm'
import type { SettingDraft, SettingItem } from '../types'

export type SettingEditorProps = {
  item: SettingItem
  catalog: readonly SettingItem[]
  draft: SettingDraft
  pending: boolean
  blocked?: boolean
  error?: { message: string; requestId?: string }
  onChange: (draft: SettingDraft) => void
  onSubmit: () => void
  onCancel: () => void
}

export function SettingEditor({
  item,
  catalog,
  draft,
  pending,
  blocked,
  error,
  onChange,
  onSubmit,
  onCancel,
}: SettingEditorProps) {
  const errors = validateSetting(item, draft, catalog)
  const value = draft.mode === 'reset' ? item.defaultValue : Number(draft.value)
  const valid = !errors.value && !errors.reason
  return (
    <form
      aria-label={`Thay đổi ${item.key}`}
      className="mt-4 space-y-3 border-t border-line pt-4"
      noValidate
      onSubmit={(event) => {
        event.preventDefault()
        if (pending || blocked || !valid) return
        if (draft.confirming) onSubmit()
        else onChange({ ...draft, confirming: true })
      }}
    >
      <h3 className="text-h3">{draft.mode === 'reset' ? 'Về mặc định' : 'Sửa giá trị'}</h3>
      {draft.mode === 'edit' ? (
        <FormField
          htmlFor="setting-value"
          label="Giá trị mới"
          required
          error={errors.value}
          hint={`Số nguyên từ ${formatNumber(item.min)} đến ${formatNumber(item.max)}`}
        >
          <Input
            type="text"
            inputMode="numeric"
            disabled={pending || draft.confirming || blocked}
            value={draft.value}
            onChange={(event) =>
              onChange({ ...draft, value: event.target.value, confirming: false })
            }
          />
        </FormField>
      ) : (
        <p>
          Giá trị mặc định: <strong>{formatNumber(item.defaultValue)}</strong>
        </p>
      )}
      {draft.mode === 'reset' && errors.value ? (
        <p role="alert" className="text-danger">
          {errors.value}
        </p>
      ) : null}
      <FormField
        htmlFor="setting-reason"
        label="Lý do"
        required
        error={errors.reason}
        hint={`${Array.from(draft.reason).length}/500 ký tự`}
      >
        <Textarea
          disabled={pending || draft.confirming || blocked}
          value={draft.reason}
          onChange={(event) =>
            onChange({ ...draft, reason: event.target.value, confirming: false })
          }
        />
      </FormField>
      {draft.confirming ? (
        <div role="status" className="rounded-sm border border-warning bg-warning-bg p-3">
          <p className="font-semibold">Xác nhận thay đổi</p>
          <p>
            {formatNumber(item.currentValue)} → {formatNumber(value)}
            {draft.mode === 'reset' ? ' (mặc định)' : ''}
          </p>
          <p className="whitespace-pre-wrap break-words">Lý do: {draft.reason}</p>
        </div>
      ) : null}
      {error ? (
        <div role="alert" className="break-words text-danger">
          <p>{error.message}</p>
          {error.requestId ? <p className="text-caption">Mã yêu cầu: {error.requestId}</p> : null}
        </div>
      ) : null}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" loading={pending} disabled={!valid || blocked}>
          {draft.confirming ? 'Xác nhận lưu' : 'Tiếp tục'}
        </Button>
        {draft.confirming ? (
          <Button
            type="button"
            variant="outline"
            disabled={pending || blocked}
            onClick={() => onChange({ ...draft, confirming: false })}
          >
            Sửa lại
          </Button>
        ) : null}
        <Button type="button" variant="outline" disabled={pending} onClick={onCancel}>
          Hủy
        </Button>
      </div>
    </form>
  )
}
