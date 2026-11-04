import { Link } from 'react-router-dom'
import { Button } from '@/shared/ui/Button'
import type { ExamResult, ResultItem } from '../types'

const filters = { ALL: 'Tất cả', WRONG: 'Sai', BLANK: 'Bỏ trống' } as const
const labels = { CORRECT: 'Đúng', WRONG: 'Sai', BLANK: 'Bỏ trống' } as const
export type ResultFilter = keyof typeof filters

function answerText(item: ResultItem, key: string) {
  const option = item.options.find((entry) => entry.key === key)
  return option ? key + '. ' + option.text : key
}

export function ExamResultView({
  result,
  filter,
  onFilter,
  showWeakPoints,
}: {
  result: ExamResult
  filter: ResultFilter
  onFilter: (filter: ResultFilter) => void
  showWeakPoints: boolean
}) {
  const items = result.items.filter((item) => filter === 'ALL' || item.result === filter)
  return (
    <main className="mx-auto max-w-content space-y-6 p-4 sm:p-6">
      <h1 className="text-h1-sm sm:text-h1">Kết quả · {result.exam.title}</h1>
      <p>
        Điểm trọng số: {result.score}/{result.maxScore} ({result.percent}%)
      </p>
      <p>
        Số câu đúng: {result.correctCount}/{result.totalCount}
      </p>
      <section aria-label="Điểm theo phần">
        <h2 className="text-h2">Điểm theo phần</h2>
        <ul>
          {result.bySection.map((part) => (
            <li key={part.index}>
              {part.name}: {part.score}/{part.maxScore}
            </li>
          ))}
        </ul>
      </section>
      {result.previous && (
        <p>
          Lần trước: {result.previous.score}/{result.previous.maxScore} ({result.previous.percent}%)
        </p>
      )}
      <section aria-label="Danh sách câu hỏi" className="space-y-4">
        <h2 className="text-h2">Xem lại câu hỏi</h2>
        <div className="flex gap-2" role="group" aria-label="Lọc câu hỏi">
          {(Object.keys(filters) as ResultFilter[]).map((value) => (
            <Button
              key={value}
              variant="outline"
              aria-pressed={value === filter}
              onClick={() => onFilter(value)}
            >
              {filters[value]}
            </Button>
          ))}
        </div>
        {items.length === 0 && <p>Không có câu trong bộ lọc này.</p>}
        {items.map((item) => (
          <article
            key={item.no}
            aria-label={'Câu ' + item.no}
            className="space-y-2 rounded-md border border-line p-4"
          >
            <h3 className="text-h3">
              Câu {item.no} · {labels[item.result]}
            </h3>
            <p>{item.stem}</p>
            <p>
              Câu trả lời của bạn:{' '}
              {item.yourAnswer ? answerText(item, item.yourAnswer) : 'Bỏ trống'}
            </p>
            <p>Đáp án đúng: {answerText(item, item.correctKey)}</p>
            <p>{item.explanation?.trim() || 'Lời giải đang được cập nhật'}</p>
            {item.result !== 'CORRECT' && item.primaryKid && (
              <Link
                className="text-brand-700 underline"
                to={'/learn/practice/targeted?kid=' + encodeURIComponent(item.primaryKid)}
              >
                Luyện nút này
              </Link>
            )}
          </article>
        ))}
      </section>
      {result.wrongByKid.length > 0 && (
        <section aria-label="Câu sai theo KID">
          <h2 className="text-h2">Câu sai theo KID</h2>
          <ul className="space-y-2">
            {result.wrongByKid.map(({ kid, nameVi, wrong, total }) => (
              <li key={kid}>
                {nameVi} ({kid}) · {wrong}/{total} câu sai hoặc bỏ trống{' '}
                <Link
                  className="text-brand-700 underline"
                  to={'/learn/practice/targeted?kid=' + encodeURIComponent(kid)}
                >
                  Luyện nút này
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}
      {showWeakPoints && (
        <section aria-label="Điểm yếu cần luyện">
          <h2 className="text-h2">Điểm yếu cần luyện</h2>
        </section>
      )}
    </main>
  )
}
