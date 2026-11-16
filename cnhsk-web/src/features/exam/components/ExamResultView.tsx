import { Link } from 'react-router-dom'
import { Button, PageHeader, StatTile } from '@/shared/ui'
import type { ExamResult, ResultItem, WeakPoint } from '../types'

const filters = { ALL: 'Tất cả', WRONG: 'Sai', BLANK: 'Bỏ trống' } as const
const labels = { CORRECT: 'Đúng', WRONG: 'Sai', BLANK: 'Bỏ trống' } as const
export type ResultFilter = keyof typeof filters
type WeakPointsState =
  | { state: 'loading' }
  | { state: 'error'; onRetry: () => void }
  | { state: 'ready'; points: WeakPoint[] }

function answerText(item: ResultItem, key: string) {
  const option = item.options.find((entry) => entry.key === key)
  return option ? key + '. ' + option.text : key
}

export function ExamResultView({
  result,
  filter,
  onFilter,
  weakPoints,
}: {
  result: ExamResult
  filter: ResultFilter
  onFilter: (filter: ResultFilter) => void
  weakPoints?: WeakPointsState
}) {
  const items = result.items.filter((item) => filter === 'ALL' || item.result === filter)
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-16 sm:px-6">
      <PageHeader title={`Kết quả · ${result.exam.title}`} />
      <div className="grid min-w-0 gap-4 sm:grid-cols-2">
        <StatTile
          label="Điểm bài làm"
          value={
            <span className="block text-h3">
              Điểm trọng số: {result.score}/{result.maxScore} ({result.percent}%)
            </span>
          }
        />
        <StatTile
          label="Câu trả lời"
          value={
            <span className="block text-h3">
              Số câu đúng: {result.correctCount}/{result.totalCount}
            </span>
          }
        />
      </div>
      <nav aria-label="Học tiếp" className="flex flex-wrap gap-3">
        <Button to="/learn/exams">Chọn đề tiếp theo</Button>
        <Button to="/learn/progress" variant="outline">
          Xem tiến độ
        </Button>
      </nav>
      <section
        aria-label="Điểm theo phần"
        className="space-y-4 rounded-md border border-line bg-surface p-6"
      >
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
        <div className="flex flex-wrap gap-2" role="group" aria-label="Lọc câu hỏi">
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
            className="min-w-0 space-y-3 rounded-md border border-line bg-surface p-6"
          >
            <h3 className="text-h3">
              Câu {item.no} · {labels[item.result]}
            </h3>
            <p className="break-words font-hanzi text-h3" lang="zh-CN">
              {item.stem}
            </p>
            <p>
              Câu trả lời của bạn:{' '}
              {item.yourAnswer ? answerText(item, item.yourAnswer) : 'Bỏ trống'}
            </p>
            <p>Đáp án đúng: {answerText(item, item.correctKey)}</p>
            <p>{item.explanation?.trim() || 'Lời giải đang được cập nhật'}</p>
            {item.result !== 'CORRECT' && item.primaryKid && (
              <Link
                className="inline-flex min-h-touch items-center text-label text-brand-700 underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
                to={'/learn/practice/targeted?kid=' + encodeURIComponent(item.primaryKid)}
              >
                Luyện nút này
              </Link>
            )}
          </article>
        ))}
      </section>
      {result.wrongByKid.length > 0 && (
        <section
          aria-label="Câu sai theo KID"
          className="space-y-4 rounded-md border border-line bg-surface p-6"
        >
          <h2 className="text-h2">Câu sai theo KID</h2>
          <ul className="space-y-2">
            {result.wrongByKid.map(({ kid, nameVi, wrong, total }) => (
              <li key={kid}>
                {nameVi} ({kid}) · {wrong}/{total} câu sai hoặc bỏ trống{' '}
                <Link
                  className="inline-flex min-h-touch items-center text-label text-brand-700 underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
                  to={'/learn/practice/targeted?kid=' + encodeURIComponent(kid)}
                >
                  Luyện nút này
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}
      {weakPoints && (weakPoints.state !== 'ready' || weakPoints.points.length > 0) && (
        <section
          aria-label="Điểm yếu cần luyện"
          className="space-y-4 rounded-md border border-line bg-surface p-6"
        >
          <h2 className="text-h2">Điểm yếu cần luyện</h2>
          {weakPoints.state === 'loading' && <p role="status">Đang tải điểm yếu…</p>}
          {weakPoints.state === 'error' && (
            <>
              <p role="alert">Không thể tải điểm yếu.</p>
              <Button onClick={weakPoints.onRetry}>Thử lại</Button>
            </>
          )}
          {weakPoints.state === 'ready' && (
            <ul className="space-y-2">
              {weakPoints.points.map(({ kid, nameVi, mastery }) => (
                <li key={kid}>
                  {nameVi} ({kid}) · {mastery}%{' '}
                  <Link
                    className="inline-flex min-h-touch items-center text-label text-brand-700 underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
                    to={'/learn/practice/targeted?kid=' + encodeURIComponent(kid)}
                  >
                    Luyện nút này
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}
    </main>
  )
}
