import { ArrowRight, Flag, RotateCcw, Sparkles, Trophy } from 'lucide-react'

import type { ApiError, ApiQueryState } from '@/shared/api'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  FormField,
  LevelBadge,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  StatusBadge,
  SUPPORTED_HSK_LEVELS,
} from '@/shared/ui'

import type { QuizListingPage } from '../types'

type QuizListViewProps = {
  state: ApiQueryState
  result?: QuizListingPage
  hsk: string
  pendingTopic?: number
  error?: ApiError
  actionError?: ApiError
  onHskChange: (value: string) => void
  onPageChange: (page: number) => void
  onRetry: () => void
  onStart: (topicId: number) => void
}

function actionMessage(error: ApiError): string {
  if (error.errorCode === 'QUIZ_NOT_AVAILABLE')
    return 'Chủ đề này chưa có đủ câu hỏi quiz. Hãy tải lại danh sách để chọn chủ đề khác.'
  if (error.errorCode === 'RATE_LIMITED')
    return 'Bạn đã đạt giới hạn 30 lượt bắt đầu quiz trong một giờ. Vui lòng thử lại sau.'
  if (error.errorCode === 'TOPIC_NOT_FOUND')
    return 'Chủ đề này không còn khả dụng. Hãy tải lại danh sách.'
  return 'Chưa thể bắt đầu quiz. Vui lòng kiểm tra kết nối và thử lại.'
}

export function QuizListView(props: QuizListViewProps) {
  const { state, result, hsk, pendingTopic, error, actionError } = props
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-20 sm:px-6">
      <PageHeader
        title="Quiz theo chủ đề"
        subtitle="Chọn chủ đề để thử sức với những câu hỏi ngắn."
      />

      <Card className="space-y-4 border-brand-100 bg-surface-subtle p-5 sm:p-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="inline-flex items-center gap-1.5 rounded-pill border border-brand-100 bg-surface px-3 py-1 text-caption font-semibold text-brand-700">
            <Trophy aria-hidden="true" className="size-3.5 shrink-0" />
            <span>Đấu trường Quiz HSK</span>
          </div>
          {result && state === 'ready' ? (
            <span className="rounded-pill border border-line bg-surface px-3 py-1 text-caption font-medium text-ink-secondary">
              {result.total} chủ đề sẵn sàng
            </span>
          ) : null}
        </div>

        <p className="text-body font-medium text-ink">
          Quiz là thi đua. Chỉ lần làm đầu tiên được tính xếp hạng. Quiz không ảnh hưởng tiến độ
          học.
        </p>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div className="rounded-md border border-brand-100 bg-surface p-3.5 shadow-sm">
            <p className="inline-flex items-center gap-1.5 text-label text-brand-800">
              <Flag aria-hidden="true" className="size-4 shrink-0 text-brand-700" />
              <span>Chế độ ghi nhận bảng hạng</span>
            </p>
            <p className="mt-1 text-body-sm text-ink-secondary">
              Điểm số và thời gian hoàn thành ở lần thử sức đầu tiên được ghi thẳng lên bảng xếp
              hạng chủ đề.
            </p>
          </div>
          <div className="rounded-md border border-line bg-surface p-3.5 shadow-sm">
            <p className="inline-flex items-center gap-1.5 text-label text-ink">
              <Sparkles aria-hidden="true" className="size-4 shrink-0 text-accent-700" />
              <span>Chế độ rèn phản xạ tự do</span>
            </p>
            <p className="mt-1 text-body-sm text-ink-secondary">
              Sau lần đầu, bạn có thể ôn luyện lại bất kỳ lúc nào để nâng kỷ lục điểm cá nhân.
            </p>
          </div>
        </div>
      </Card>

      <Card className="p-4 sm:p-5">
        <div className="max-w-xs">
          <FormField htmlFor="quiz-hsk" label="Cấp HSK">
            <Select value={hsk} onChange={(event) => props.onHskChange(event.target.value)}>
              <option value="">Mọi cấp</option>
              {SUPPORTED_HSK_LEVELS.map((level) => (
                <option key={level} value={level}>
                  HSK {level}
                </option>
              ))}
            </Select>
          </FormField>
        </div>
      </Card>

      {actionError ? (
        <div
          role="alert"
          className="min-w-0 space-y-3 rounded-md border border-danger bg-danger-bg p-4 text-danger"
        >
          <p>{actionMessage(actionError)}</p>
          {actionError.errorCode !== 'RATE_LIMITED' ? (
            <Button variant="outline" onClick={props.onRetry}>
              Tải lại danh sách
            </Button>
          ) : null}
        </div>
      ) : null}

      {state === 'loading' ? (
        <section
          aria-label="Đang tải quiz"
          aria-busy="true"
          className="grid min-w-0 grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3"
        >
          {[1, 2, 3].map((id) => (
            <Skeleton key={id} variant="card" />
          ))}
          <p role="status" className="sr-only">
            Đang tải quiz…
          </p>
        </section>
      ) : null}

      {state === 'error' ? (
        <ErrorState
          title="Không tải được quiz"
          message={error?.message ?? 'Vui lòng thử lại.'}
          onRetry={props.onRetry}
          requestId={error?.requestId}
        />
      ) : null}

      {state === 'empty' ? (
        <EmptyState
          title="Chưa có quiz phù hợp"
          description="Các chủ đề cần đủ câu hỏi đã duyệt để mở quiz. Hãy chọn cấp HSK khác hoặc quay lại sau."
          action={
            <Button
              variant="outline"
              onClick={() => (hsk ? props.onHskChange('') : props.onRetry())}
            >
              {hsk ? 'Xem mọi cấp' : 'Tải lại danh sách'}
            </Button>
          }
        />
      ) : null}

      {state === 'ready' && result ? (
        <section
          aria-label="Các chủ đề quiz"
          className="grid min-w-0 grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3"
        >
          {result.items.map((quiz) => {
            const retry = quiz.myFirstAttempt !== null
            return (
              <Card
                key={quiz.topicId}
                className="flex min-w-0 flex-col gap-4 break-words p-5 [overflow-wrap:anywhere]"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="flex size-11 shrink-0 items-center justify-center rounded-md border border-brand-100 bg-brand-50 font-hanzi text-h3 text-brand-700">
                    测
                  </div>
                  <LevelBadge level={quiz.hskLevel} />
                </div>

                <div className="min-w-0 space-y-1">
                  <h2 className="min-w-0 text-h3 text-ink">{quiz.topicName}</h2>
                  <p className="text-body-sm text-ink-secondary">{quiz.questionCount} câu hỏi</p>
                </div>

                <div className="space-y-1 rounded-sm border border-line bg-canvas p-3.5 text-body-sm text-ink-secondary">
                  <p className="font-medium text-ink">
                    {quiz.myFirstAttempt
                      ? `Đã làm lần đầu: ${quiz.myFirstAttempt.score} điểm`
                      : 'Chưa làm lần đầu'}
                  </p>
                  {quiz.myFirstAttempt?.suspicious ? (
                    <p className="text-warning">Lượt đầu bị đánh dấu nghi vấn.</p>
                  ) : null}
                  {quiz.myBestRetryScore !== null ? (
                    <p>Điểm làm lại tốt nhất: {quiz.myBestRetryScore}</p>
                  ) : null}
                </div>

                <div className="mt-auto flex items-center justify-between gap-2 pt-1">
                  <StatusBadge className="whitespace-normal" tone={retry ? 'neutral' : 'info'}>
                    {retry ? 'Làm lại — không tính hạng' : 'Lượt đầu tính hạng'}
                  </StatusBadge>
                </div>

                <Button
                  aria-label={`${retry ? 'Làm lại' : 'Bắt đầu'} quiz ${quiz.topicName}`}
                  disabled={pendingTopic !== undefined}
                  loading={pendingTopic === quiz.topicId}
                  onClick={() => props.onStart(quiz.topicId)}
                >
                  <span className="inline-flex items-center gap-2">
                    <span>{retry ? 'Làm lại' : 'Bắt đầu'}</span>
                    {retry ? (
                      <RotateCcw aria-hidden="true" className="size-4 shrink-0" />
                    ) : (
                      <ArrowRight aria-hidden="true" className="size-4 shrink-0" />
                    )}
                  </span>
                </Button>
              </Card>
            )
          })}
        </section>
      ) : null}

      {result && state !== 'error' && result.total > result.size ? (
        <Pagination
          label="Phân trang quiz"
          page={result.page + 1}
          size={result.size}
          total={result.total}
          onChange={(page) => props.onPageChange(page - 1)}
        />
      ) : null}
    </main>
  )
}
