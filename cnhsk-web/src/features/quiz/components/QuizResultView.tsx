import { cn } from '@/shared/lib/cn'
import { Button, Card, EmptyState, PageHeader, StatTile, StatusBadge } from '@/shared/ui'
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
  const statusTone = result.suspicious ? 'warning' : result.rankingEligible ? 'success' : 'neutral'
  const statusLabel = result.suspicious
    ? 'Bị đánh dấu nghi vấn — không tính hạng'
    : result.rankingEligible
      ? 'Tính hạng'
      : 'Làm lại — không tính hạng'

  return (
    <div className="min-w-0 space-y-6">
      <PageHeader
        actions={<StatusBadge tone={statusTone}>{statusLabel}</StatusBadge>}
        title="Kết quả quiz theo chủ đề"
        subtitle={result.topicName}
      />

      <section aria-label="Số liệu kết quả" className="space-y-4">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <StatTile label="Tổng điểm" value={`${result.score}/100 điểm`} />
          <StatTile
            label="Độ chính xác"
            value={
              <span className="block text-h3">
                Đúng: {result.correctCount} câu · Sai: {result.wrongCount} câu
              </span>
            }
          />
          <StatTile
            label="Thời gian làm bài"
            value={
              <span className="block text-h3">Thời gian: {quizDuration(result.durationMs)}</span>
            }
          />
        </div>
        <p className="text-body-sm text-ink-secondary">Quiz không ảnh hưởng tiến độ học.</p>

        {first ? (
          <Card className="space-y-2 border-brand-100 bg-brand-50 p-5">
            <h2 className="text-h3 text-brand-800">Lượt đầu tiên</h2>
            <div className="flex flex-wrap gap-x-6 gap-y-2 text-body-sm text-ink">
              <p>Điểm lượt đầu: {first.score}</p>
              {first.durationMs !== null ? (
                <p>Thời gian lượt đầu: {quizDuration(first.durationMs)}</p>
              ) : null}
              {first.rank !== null && first.total !== null ? (
                <p className="font-semibold text-brand-700">
                  Hạng lần đầu: #{first.rank}/{first.total}
                </p>
              ) : null}
            </div>
          </Card>
        ) : null}
      </section>

      {actionError ? (
        <p role="alert" className="rounded-md border border-danger bg-danger-bg p-4 text-danger">
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

      <section aria-label="Đáp án quiz" className="min-w-0 space-y-4">
        <h2 className="text-h2 text-ink">Đáp án từng câu</h2>
        {result.items.length === 0 ? (
          <EmptyState
            title="Chưa có nội dung đáp án"
            description="Không có câu hỏi để hiển thị trong kết quả này."
          />
        ) : null}
        {result.items.map((item) => {
          const isCorrect = item.chosen === item.correct
          return (
            <Card
              key={item.itemId}
              className="min-w-0 space-y-3 break-words p-5 [overflow-wrap:anywhere] sm:p-6"
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <h3 className="text-h3 text-ink">
                  Câu {item.itemId}: {item.prompt}
                </h3>
                <StatusBadge tone={isCorrect ? 'success' : 'danger'}>
                  {item.chosen === null ? 'Bỏ trống — Sai' : isCorrect ? 'Đúng' : 'Sai'}
                </StatusBadge>
              </div>
              <ul className="space-y-2">
                {item.options.map((option) => {
                  const chosen = option.key === item.chosen
                  const correct = option.key === item.correct
                  return (
                    <li
                      key={option.key}
                      className={cn(
                        'rounded-sm border p-3 text-body-sm',
                        correct
                          ? 'border-success bg-success-bg font-medium text-ink'
                          : chosen
                            ? 'border-danger bg-danger-bg text-ink'
                            : 'border-line bg-surface text-ink',
                      )}
                    >
                      {option.key}. {option.text}
                      {chosen ? ' — Đã chọn' : ''}
                      {correct ? ' — Đáp án đúng' : ''}
                    </li>
                  )
                })}
              </ul>
              {item.chosen === null ? (
                <p className="text-body-sm text-ink-secondary">Bạn chưa chọn đáp án.</p>
              ) : null}
              {item.explanation ? (
                <p className="rounded-sm bg-canvas p-3 text-body-sm text-ink-secondary">
                  Giải thích: {item.explanation}
                </p>
              ) : null}
            </Card>
          )
        })}
      </section>
    </div>
  )
}
