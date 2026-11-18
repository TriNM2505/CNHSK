import { Button } from '@/shared/ui/Button'
import { FormField, Textarea } from '@/shared/ui/FormField'

type Props = { value: string; onChange: (value: string) => void; onSubmit: () => void; onCancel: () => void; disabled: boolean; label: string; validation?: string }
export function CommentForm({ value, onChange, onSubmit, onCancel, disabled, label, validation }: Props) {
  return <form className="my-4 rounded-md border border-line bg-surface p-4 shadow-sm" onSubmit={(event) => { event.preventDefault(); onSubmit() }}>
    <FormField htmlFor="discussion-content" label={label} required error={validation} hint={`${value.trim().length}/5.000`}>
      <Textarea id="discussion-content" className="min-h-32" disabled={disabled} value={value} onChange={(event) => onChange(event.target.value)} />
    </FormField>
    <div className="mt-3 flex flex-wrap gap-3">
      <Button type="submit" disabled={disabled}>Gửi bình luận</Button>
      <Button type="button" variant="outline" onClick={onCancel}>Đóng</Button>
    </div>
  </form>
}
