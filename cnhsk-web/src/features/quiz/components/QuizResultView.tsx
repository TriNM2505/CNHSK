import { ArrowRight, BookOpen, CheckCircle2, Clock3, RotateCcw, Trophy } from 'lucide-react'
import {
  Button,
  Card,
  EmptyState,
  PageHeader,
  Progress,
  Section,
  StatTile,
  StatusBadge,
} from '@/shared/ui'
import { cn } from '@/shared/lib/cn'
import type { QuizResult } from '../types'
import { quizDuration } from '../types'
import { QuizHeaderArt } from './QuizHeaderArt'
import { QuizLandscapeArt } from './QuizLandscapeArt'
import { QuizResultText } from './QuizResultParts'
import './QuizResultView.css'

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
    <div className="quiz-result min-w-0 space-y-4 pb-4">
      <div className="relative overflow-hidden rounded-md border border-brand-100 bg-brand-50 px-5 py-5 sm:px-6">
        <PageHeader
          className="relative z-nav mb-0"
          title={
            <span className="inline-flex items-center gap-3">
              <span aria-hidden="true" className="h-7 w-1.5 shrink-0 rounded-pill bg-brand-600" />
              Kết quả quiz theo chủ đề
            </span>
          }
          subtitle={<QuizResultText text={result.topicName} />}
        />
        <QuizHeaderArt />
      </div>
      <div className="quiz-result-content min-w-0 space-y-4">
        <Card className="relative isolate overflow-hidden border-brand-100 p-5 shadow-sm sm:p-6">
          <QuizLandscapeArt bridge />
          <div className="relative grid min-w-0 gap-5 sm:grid-cols-2 sm:items-center">
            <div className="min-w-0 space-y-3">
              <div className="flex items-center gap-2 text-label text-brand-700">
                <Trophy aria-hidden="true" className="size-5" />
                Kết quả của bạn
              </div>
              <StatTile
                className="border-0 bg-transparent p-0"
                label="Điểm quiz"
                value={`${result.score}/100 điểm`}
              />
              <Progress value={result.score} aria-label="Điểm quiz trên thang 100" />
            </div>
            <div className="min-w-0 space-y-3">
              <StatusBadge
                className="max-w-full whitespace-normal"
                tone={
                  result.suspicious ? 'warning' : result.rankingEligible ? 'success' : 'neutral'
                }
              >
                {result.suspicious
                  ? 'Bị đánh dấu nghi vấn — không tính hạng'
                  : result.rankingEligible
                    ? 'Tính hạng'
                    : 'Làm lại — không tính hạng'}
              </StatusBadge>
              <p className="flex items-start gap-2 text-body-sm text-ink">
                <CheckCircle2 aria-hidden="true" className="size-4 shrink-0 text-success" />
                Đúng: {result.correctCount} câu · Sai: {result.wrongCount} câu
              </p>
              <p className="flex items-start gap-2 text-body-sm text-ink">
                <Clock3 aria-hidden="true" className="size-4 shrink-0 text-brand-600" />
                Thời gian: {quizDuration(result.durationMs)}
              </p>
              <p className="text-body-sm text-ink-secondary">Quiz không ảnh hưởng tiến độ học.</p>
            </div>
          </div>
        </Card>
        {actionError ? (
          <p role="alert" className="rounded-md bg-danger-bg p-4 text-danger">
            {actionError}
          </p>
        ) : null}
        <div className="quiz-result-actions flex flex-wrap gap-3 rounded-md border border-brand-100 bg-surface p-4">
          <Button loading={pending} onClick={onRetry}>
            <RotateCcw aria-hidden="true" className="size-4" />
            Làm lại
          </Button>
          <Button variant="outline" to="/community/quizzes">
            Quiz khác
            <ArrowRight aria-hidden="true" className="size-4" />
          </Button>
          {leaderboardAvailable ? (
            <Button variant="outline" to={`/leaderboards/topics?topicId=${result.topicId}`}>
              Bảng xếp hạng chủ đề
            </Button>
          ) : null}
        </div>
        {first ? (
          <Card className="border-brand-100 p-4 sm:p-5">
            <Section
              className="mb-0"
              title={
                <span className="inline-flex items-center gap-2">
                  <Trophy aria-hidden="true" className="size-5 text-brand-600" />
                  Lượt đầu tiên
                </span>
              }
            >
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="space-y-2 text-body-sm text-ink">
                  <p className="font-semibold">Điểm lượt đầu: {first.score}</p>
                  {first.durationMs !== null ? (
                    <p>Thời gian lượt đầu: {quizDuration(first.durationMs)}</p>
                  ) : null}
                </div>
                {first.rank !== null && first.total !== null ? (
                  <p className="rounded-md bg-brand-50 px-4 py-3 text-label text-brand-700">
                    Hạng lần đầu: #{first.rank}/{first.total}
                  </p>
                ) : null}
              </div>
            </Section>
          </Card>
        ) : null}
        <section aria-label="Đáp án quiz" className="min-w-0 space-y-4">
          <Section
            className="mb-0"
            title={
              <span className="inline-flex items-center gap-2">
                <BookOpen aria-hidden="true" className="size-5 text-brand-600" />
                Đáp án từng câu
              </span>
            }
          >
            <div className="space-y-3">
              {result.items.length === 0 ? (
                <EmptyState
                  title="Chưa có nội dung đáp án"
                  description="Không có câu hỏi để hiển thị trong kết quả này."
                />
              ) : null}
              {result.items.map((item) => (
                <Card
                  key={item.itemId}
                  className="quiz-result-answer relative isolate min-w-0 space-y-3 overflow-hidden border-brand-100 p-4 transition-shadow hover:shadow-sm sm:p-5"
                >
                  <QuizLandscapeArt />
                  <div className="relative space-y-3">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <h3 className="min-w-0 flex-1 text-h3">
                        Câu {item.itemId}: <QuizResultText text={item.prompt} />
                      </h3>
                      <StatusBadge tone={item.chosen === item.correct ? 'success' : 'danger'}>
                        {item.chosen === null
                          ? 'Bỏ trống — Sai'
                          : item.chosen === item.correct
                            ? 'Đúng'
                            : 'Sai'}
                      </StatusBadge>
                    </div>
                    <ul className="grid gap-2 sm:grid-cols-2">
                      {item.options.map((option) => (
                        <li
                          key={option.key}
                          className={cn(
                            'min-w-0 rounded-sm border p-3 text-body',
                            option.key === item.correct
                              ? 'border-success bg-success-bg text-success'
                              : option.key === item.chosen
                                ? 'border-danger bg-danger-bg text-danger'
                                : 'border-line bg-surface text-ink-secondary',
                          )}
                        >
                          {option.key}. <QuizResultText text={option.text} />
                          {option.key === item.chosen ? ' — Đã chọn' : ''}
                          {option.key === item.correct ? ' — Đáp án đúng' : ''}
                        </li>
                      ))}
                    </ul>
                    {item.chosen === null ? (
                      <p className="text-body-sm text-ink-secondary">Bạn chưa chọn đáp án.</p>
                    ) : null}
                    {item.explanation ? (
                      <p className="rounded-sm bg-canvas p-3 text-body text-ink-secondary">
                        Giải thích: <QuizResultText text={item.explanation} />
                      </p>
                    ) : null}
                  </div>
                </Card>
              ))}
            </div>
          </Section>
        </section>
      </div>
    </div>
  )
}
