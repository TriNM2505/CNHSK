import {
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  BookCheck,
  BookOpenText,
  Quote,
  Sparkles,
} from 'lucide-react'

import type { AnswerResult, FinishResult, ServedItemView } from '@/features/practice'
import { ChoiceQuestion, SessionProgress, SessionSummary } from '@/features/practice'
import {
  Button,
  Card,
  ConfirmPanel,
  EmptyState,
  ErrorState,
  HanziText,
  LevelBadge,
  PageHeader,
  Skeleton,
  StatusBadge,
  type PageStateValue,
} from '@/shared/ui'

import { grammarTabForLevel } from '../grammarLevels'
import type { GrammarLesson, GrammarSummary } from '../types'

type LoadError = { message: string; requestId?: string }

export interface GrammarLessonViewProps {
  checkingActive?: boolean
  confirmOpen?: boolean
  current?: number
  error?: LoadError
  finishResult?: FinishResult
  hasActiveSession?: boolean
  item?: ServedItemView
  lesson?: GrammarLesson
  nextLookupError?: string
  nextLookupPending?: boolean
  nextPoint?: GrammarSummary
  onAnswer?: (answer: string) => Promise<unknown>
  onBegin?: () => void
  onNext?: () => void
  onRetry: () => void
  onSkipCancel?: () => void
  onSkipConfirm?: () => void
  onSkipRequest?: () => void
  pending?: boolean
  practiceError?: LoadError
  result?: AnswerResult
  stage?: 'reading' | 'exercise' | 'finished'
  state: PageStateValue
  total?: number
}

function numberFromExtra(result: FinishResult, key: string) {
  const value = result.extra?.[key]
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined
}

function LessonContent({ lesson }: { lesson: GrammarLesson }) {
  return (
    <div className="space-y-5">
      <Card className="border-brand-100 bg-surface-subtle p-5 sm:p-6">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="inline-flex items-center gap-2 text-brand-800">
            <Sparkles aria-hidden="true" className="size-4 shrink-0 text-brand-700" />
            <h2 className="text-h3 text-brand-800">Cấu trúc</h2>
          </div>
          <span className="rounded-pill border border-brand-100 bg-surface px-3 py-0.5 font-hanzi text-caption font-semibold text-brand-700">
            语法公式
          </span>
        </div>

        <div className="mt-3 rounded-md border border-brand-100 bg-surface px-4 py-4 shadow-sm sm:px-5">
          <p className="break-words font-hanzi text-h2 leading-relaxed text-brand-800" lang="zh-CN">
            {lesson.structure || lesson.text}
          </p>
        </div>
      </Card>

      <Card className="p-5 sm:p-6">
        <div className="flex items-center gap-2">
          <BookOpenText aria-hidden="true" className="size-5 shrink-0 text-brand-700" />
          <h2 className="text-h3 text-ink">Giải thích</h2>
        </div>
        <p className="mt-3 whitespace-pre-line break-words text-body leading-relaxed text-ink-secondary">
          {lesson.explanation}
        </p>
      </Card>

      <section aria-labelledby="grammar-examples-title" className="space-y-3">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <Quote aria-hidden="true" className="size-5 shrink-0 text-brand-700" />
            <h2 className="text-h3 text-ink" id="grammar-examples-title">
              Ví dụ
            </h2>
          </div>
          <span className="text-caption font-medium text-ink-muted">
            {lesson.examples.length} câu mẫu song ngữ
          </span>
        </div>

        <div className="space-y-3">
          {lesson.examples.map((example, index) => (
            <Card className="p-4 sm:p-5" key={`${example.cn}-${index}`}>
              <div className="flex items-start gap-3.5">
                <span className="inline-flex size-7 shrink-0 items-center justify-center rounded-pill border border-brand-100 bg-brand-50 text-caption font-semibold text-brand-700">
                  {String(index + 1).padStart(2, '0')}
                </span>
                <div className="min-w-0 flex-1 space-y-1.5">
                  <HanziText className="block break-words text-ink" size="sm">
                    {example.cn}
                  </HanziText>
                  {example.pinyin ? (
                    <p className="break-words text-body-sm font-medium text-brand-700">
                      {example.pinyin}
                    </p>
                  ) : null}
                  <p className="break-words border-l-2 border-brand-100 pl-3 text-body text-ink-secondary">
                    {example.vi}
                  </p>
                </div>
              </div>
            </Card>
          ))}
        </div>
      </section>

      {lesson.commonMistake?.trim() ? (
        <Card className="border-warning bg-warning-bg p-5 sm:p-6">
          <div className="flex items-start gap-3">
            <AlertTriangle aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-warning" />
            <div className="min-w-0 flex-1">
              <h2 className="text-h3 text-warning">Lỗi thường gặp</h2>
              <p className="mt-2 whitespace-pre-line break-words text-body text-ink">
                {lesson.commonMistake}
              </p>
            </div>
          </div>
        </Card>
      ) : null}
    </div>
  )
}

function FinishedLesson({
  finishResult,
  lesson,
  nextLookupError,
  nextLookupPending,
  nextPoint,
}: Pick<
  GrammarLessonViewProps,
  'finishResult' | 'lesson' | 'nextLookupError' | 'nextLookupPending' | 'nextPoint'
>) {
  if (!finishResult || !lesson) return null
  const total = numberFromExtra(finishResult, 'exercisesTotal') ?? 0
  const correct = numberFromExtra(finishResult, 'correct') ?? 0
  const skipped = finishResult.extra?.skippedExercises === true
  const title = skipped || total === 0 ? 'Đã ghi nhận bài học' : `Đúng ${correct}/${total}`
  const listPath = `/learn/grammar?level=${grammarTabForLevel(lesson.hskLevel)}`

  return (
    <div className="space-y-3">
      <SessionSummary
        actions={
          <>
            <Button to={listPath} variant="outline">
              Quay lại danh sách
            </Button>
            {nextPoint ? (
              <Button to={`/learn/grammar/${nextPoint.id}`}>
                <span className="inline-flex items-center gap-2">
                  Học điểm tiếp theo
                  <ArrowRight aria-hidden="true" className="size-4 shrink-0" />
                </span>
              </Button>
            ) : null}
          </>
        }
        result={finishResult}
        title={title}
      />
      {total === 0 ? (
        <p className="text-center text-body-sm text-ink-secondary" role="status">
          Điểm này chưa có bài tập.
        </p>
      ) : null}
      {nextLookupPending ? (
        <p className="text-center text-body-sm text-ink-secondary" role="status">
          Đang tìm điểm ngữ pháp tiếp theo…
        </p>
      ) : null}
      {nextLookupError ? (
        <p className="text-center text-body-sm text-danger" role="alert">
          {nextLookupError}
        </p>
      ) : null}
    </div>
  )
}

function LessonLoading() {
  return (
    <div aria-label="Đang tải bài ngữ pháp" className="space-y-4" role="status">
      <Skeleton className="h-20" />
      <Skeleton className="h-36" />
      <Skeleton className="h-48" />
    </div>
  )
}

export function GrammarLessonView({
  checkingActive = false,
  confirmOpen = false,
  current = 1,
  error,
  finishResult,
  hasActiveSession = false,
  item,
  lesson,
  nextLookupError,
  nextLookupPending,
  nextPoint,
  onAnswer,
  onBegin,
  onNext,
  onRetry,
  onSkipCancel,
  onSkipConfirm,
  onSkipRequest,
  pending = false,
  practiceError,
  result,
  stage = 'reading',
  state,
  total = 1,
}: GrammarLessonViewProps) {
  if (state === 'loading') {
    return (
      <main className="mx-auto w-full min-w-0 max-w-reading px-4 py-6 pb-20 sm:px-6">
        <LessonLoading />
      </main>
    )
  }

  if (state === 'empty') {
    return (
      <main className="mx-auto w-full min-w-0 max-w-reading px-4 py-6 pb-20 sm:px-6">
        <EmptyState
          action={<Button to="/learn/grammar">Quay lại danh sách</Button>}
          description="Điểm ngữ pháp này không tồn tại hoặc không còn khả dụng."
          icon={<BookOpenText aria-hidden="true" className="size-5" />}
          title="Không tìm thấy điểm ngữ pháp"
        />
      </main>
    )
  }

  if (state === 'error' || !lesson) {
    return (
      <main className="mx-auto w-full min-w-0 max-w-reading px-4 py-6 pb-20 sm:px-6">
        <ErrorState
          message={error?.message ?? 'Vui lòng thử lại sau.'}
          onRetry={onRetry}
          requestId={error?.requestId}
          title="Không thể tải bài ngữ pháp"
        />
      </main>
    )
  }

  const listPath = `/learn/grammar?level=${grammarTabForLevel(lesson.hskLevel)}`

  return (
    <main className="mx-auto w-full min-w-0 max-w-reading space-y-6 px-4 py-6 pb-20 sm:px-6">
      {stage !== 'finished' ? (
        <div>
          <Button size="sm" to={listPath} variant="ghost">
            <span className="inline-flex items-center gap-2">
              <ArrowLeft aria-hidden="true" className="size-4 shrink-0" />
              Quay lại danh sách
            </span>
          </Button>
        </div>
      ) : null}

      <PageHeader
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <LevelBadge exact level={lesson.hskLevel} />
            {lesson.learned ? <StatusBadge tone="success">Đã học</StatusBadge> : null}
          </div>
        }
        className="mb-0"
        subtitle={lesson.titleVi}
        title={<HanziText size="lg">{lesson.text}</HanziText>}
      />

      <LessonContent lesson={lesson} />

      <section aria-label="Hoạt động bài học" className="border-t border-line pt-6">
        {stage === 'reading' ? (
          <Card className="space-y-4 border-brand-100 bg-surface-subtle p-5 text-center sm:p-6">
            <p className="text-body-sm text-ink-secondary">
              {lesson.exerciseCount > 0
                ? `Hoàn thành bài đọc và làm ${lesson.exerciseCount} câu bài tập ngắn để củng cố kiến thức.`
                : 'Xác nhận bạn đã đọc hiểu điểm ngữ pháp này để ghi nhận tiến độ.'}
            </p>
            <div>
              <Button disabled={checkingActive || pending} loading={pending} onClick={onBegin}>
                <span className="inline-flex items-center gap-2">
                  <BookCheck aria-hidden="true" className="size-5 shrink-0" />
                  {checkingActive
                    ? 'Đang kiểm tra lượt học'
                    : hasActiveSession
                      ? 'Tiếp tục bài tập'
                      : 'Đã hiểu'}
                </span>
              </Button>
            </div>
            {practiceError ? (
              <div className="mx-auto max-w-xl text-left">
                <ErrorState
                  message={practiceError.message}
                  onRetry={onRetry}
                  requestId={practiceError.requestId}
                  title="Không thể tải lượt bài tập"
                />
              </div>
            ) : null}
          </Card>
        ) : null}

        {stage === 'exercise' ? (
          <div className="space-y-5">
            {practiceError ? (
              <ErrorState
                message={practiceError.message}
                onRetry={onRetry}
                requestId={practiceError.requestId}
                title="Không thể tiếp tục bài tập"
              />
            ) : item && onAnswer ? (
              <>
                <SessionProgress current={current} total={total} />
                <Card className="space-y-5 p-5 sm:p-6">
                  <ChoiceQuestion
                    feedbackMode="IMMEDIATE"
                    item={item}
                    key={`grammar-question-${item.no}`}
                    onAnswer={onAnswer}
                    pending={pending}
                    result={result}
                  />
                  <div className="flex flex-wrap justify-end gap-3">
                    <Button disabled={pending} onClick={onSkipRequest} variant="outline">
                      Bỏ qua bài tập
                    </Button>
                    {result ? (
                      <Button disabled={pending} onClick={onNext}>
                        <span className="inline-flex items-center gap-2">
                          Câu tiếp
                          <ArrowRight aria-hidden="true" className="size-4 shrink-0" />
                        </span>
                      </Button>
                    ) : null}
                  </div>
                </Card>
              </>
            ) : (
              <Skeleton className="h-64" />
            )}
          </div>
        ) : null}

        {stage === 'finished' ? (
          <FinishedLesson
            finishResult={finishResult}
            lesson={lesson}
            nextLookupError={nextLookupError}
            nextLookupPending={nextLookupPending}
            nextPoint={nextPoint}
          />
        ) : null}
      </section>

      <ConfirmPanel
        confirmLabel="Bỏ qua và ghi nhận bài học"
        message="Các câu chưa trả lời sẽ không được ghi. Bài học vẫn được đánh dấu là đã học nhưng không có điểm đầy đủ."
        onCancel={onSkipCancel ?? (() => undefined)}
        onConfirm={onSkipConfirm ?? (() => undefined)}
        open={confirmOpen}
        pending={pending}
        title="Bỏ qua phần bài tập?"
      />
    </main>
  )
}
