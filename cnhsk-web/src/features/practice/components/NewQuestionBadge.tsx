import { Sparkles } from 'lucide-react'

/** Small marker for a question newly added to the bank, shown above it during practice. */
export function NewQuestionBadge() {
  return (
    <p className="inline-flex items-center gap-1.5 rounded-pill bg-info-bg px-3 py-1 text-caption font-semibold text-info">
      <Sparkles aria-hidden="true" className="size-3.5" />
      <span>Câu mới</span>
    </p>
  )
}
