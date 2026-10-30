import { Button } from '@/shared/ui/Button'
import { FormField } from '@/shared/ui/FormField'

type Props = { value: string; onChange: (value: string) => void; onSubmit: () => void; onCancel: () => void; disabled: boolean; label: string; validation?: string }
export function CommentForm({ value, onChange, onSubmit, onCancel, disabled, label, validation }: Props) {
  return <form className="my-4 rounded-md border border-line p-4" onSubmit={(event) => { event.preventDefault(); onSubmit() }}>
    <FormField htmlFor="discussion-content" label={label} required error={validation} hint={`${value.trim().length}/5.000`}>
      <textarea id="discussion-content" className="min-h-32 w-full border border-line p-3" disabled={disabled} value={value} onChange={(event) => onChange(event.target.value)} />
    </FormField>
    <Button type="submit" disabled={disabled}>Gửi bình luận</Button>
    <Button className="ml-3" type="button" variant="outline" disabled={disabled} onClick={onCancel}>Đóng</Button>
  </form>
}
