import { TriangleAlert } from 'lucide-react'

/** Tells the learner that answers given faster than the minimum time were left out of progress and review. */
export function FastAnswersNotice() {
  return (
    <p
      className="flex items-start gap-2 rounded-lg border border-warning bg-warning-bg p-4 text-body-sm text-ink"
      role="note"
    >
      <TriangleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-warning" />
      <span>
        Một số câu trả lời quá nhanh nên không được tính vào Tiến độ học tập và lịch ôn. Hãy đọc kỹ từng
        câu trước khi chọn đáp án.
      </span>
    </p>
  )
}
