import { Button, Card, EmptyState, PageHeader } from '@/shared/ui'
import type { QuizResult } from '../types'
import { quizDuration } from '../types'

export function QuizResultView({
  result,
  pending,
  actionError,
  leaderboardAvailable,
  onRetry,
}: {
  result: QuizResult
  pending: boolean
  actionError?: string
  leaderboardAvailable: boolean
  onRetry: () => void
}) {
  const first = result.firstAttempt
  return (
    <div className="min-w-0 space-y-6">
      <PageHeader title="Kết quả quiz theo chủ đề" subtitle={result.topicName} />
      <Card className="space-y-3">
        <p className="text-h1">{result.score}/100 điểm</p>
        <p>
          {result.suspicious
            ? 'Bị đánh dấu nghi vấn — không tính hạng'
            : result.rankingEligible
              ? 'Tính hạng'
              : 'Làm lại — không tính hạng'}
        </p>
        <p>
          Đúng: {result.correctCount} câu · Sai: {result.wrongCount} câu
        </p>
        <p>Thời gian: {quizDuration(result.durationMs)}</p>
        <p>Quiz không ảnh hưởng tiến độ học.</p>
      </Card>
      {first ? (
        <Card className="space-y-2">
          <h2 className="text-h3">Lượt đầu tiên</h2>
          <p>Điểm lượt đầu: {first.score}</p>
          {first.durationMs !== null ? (
            <p>Thời gian lượt đầu: {quizDuration(first.durationMs)}</p>
          ) : null}
          {first.rank !== null && first.total !== null ? (
            <p className="font-semibold">
              Hạng lần đầu: #{first.rank}/{first.total}
            </p>
          ) : null}
        </Card>
      ) : null}
      <section aria-label="Đáp án quiz" className="min-w-0 space-y-4">
        <h2 className="text-h2">Đáp án từng câu</h2>
        {result.items.length === 0 ? (
          <EmptyState
            title="Chưa có nội dung đáp án"
            description="Không có câu hỏi để hiển thị trong kết quả này."
          />
        ) : null}
        {result.items.map((item) => (
          <Card
            key={item.itemId}
            className="min-w-0 space-y-3 break-words [overflow-wrap:anywhere]"
          >
            <h3 className="text-h3">
              Câu {item.itemId}: {item.prompt}
            </h3>
            <p
              className={
                item.chosen === item.correct
                  ? 'font-semibold text-success'
                  : 'font-semibold text-danger'
              }
            >
              {item.chosen === null
                ? 'Bỏ trống — Sai'
                : item.chosen === item.correct
                  ? 'Đúng'
                  : 'Sai'}
            </p>
            <ul className="space-y-2">
              {item.options.map((option) => (
                <li key={option.key} className="rounded-sm border border-line p-3">
                  {option.key}. {option.text}
                  {option.key === item.chosen ? ' — Đã chọn' : ''}
                  {option.key === item.correct ? ' — Đáp án đúng' : ''}
                </li>
              ))}
            </ul>
            {item.chosen === null ? <p>Bạn chưa chọn đáp án.</p> : null}
            {item.explanation ? <p>Giải thích: {item.explanation}</p> : null}
          </Card>
        ))}
      </section>
      {actionError ? (
        <p role="alert" className="rounded-md bg-danger-bg p-4 text-danger">
          {actionError}
        </p>
      ) : null}
      <div className="flex flex-wrap gap-3">
        <Button loading={pending} onClick={onRetry}>
          Làm lại
        </Button>
        <Button variant="outline" to="/community/quizzes">
          Quiz khác
        </Button>
        {leaderboardAvailable ? (
          <Button variant="outline" to={`/leaderboards/topics?topicId=${result.topicId}`}>
            Bảng xếp hạng chủ đề
          </Button>
        ) : null}
      </div>
    </div>
  )
}
